package no.rutebanken.extime.converter;

import no.rutebanken.extime.config.CamelRouteDisabler;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.util.NetexObjectIdTypes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rutebanken.netex.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.OffsetDateTime;
import java.util.List;

import static no.rutebanken.extime.Constants.NETEX_PROFILE_VERSION;
import static no.rutebanken.extime.Constants.VERSION_ONE;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@ActiveProfiles("test")
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {CamelRouteDisabler.class, CommonDataToNetexConverter.class})
@TestPropertySource(properties = {"avinor.timetable.export.site = false"})
public class CommonDataWithoutSiteToNetexConverterTest {

    @Autowired
    private CommonDataToNetexConverter netexConverter;

    @Test
    public void verifyPublicationDelivery() throws Exception {
        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex().getValue();
        assertValidPublicationDelivery(publicationDelivery);

        NetexTestUtils.verifyCompositeFrameAttributes(publicationDelivery);
        assertValidCompositeFrame(publicationDelivery);

        ResourceFrame resourceFrame = NetexTestUtils.getFrames(ResourceFrame.class, NetexTestUtils.getDataObjectFrames(publicationDelivery)).get(0);
        assertThat(resourceFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(resourceFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.RESOURCE_FRAME_KEY), "ResourceFrame");

        List<SiteFrame> siteFrames = NetexTestUtils.getFrames(SiteFrame.class, NetexTestUtils.getDataObjectFrames(publicationDelivery));
        assertThat(siteFrames).isEmpty();

        NetexTestUtils.verifyServiceFrameAttributes(publicationDelivery);

        assertValidResourceFrame(resourceFrame);
        assertValidServiceFrame(publicationDelivery);
    }

    private void assertValidPublicationDelivery(PublicationDeliveryStructure publicationDelivery) {
        assertThat(publicationDelivery).isNotNull();
        assertThat(publicationDelivery.getVersion()).isEqualTo(NETEX_PROFILE_VERSION);
        assertThat(publicationDelivery.getPublicationTimestamp()).isNotNull().isBefore(OffsetDateTime.now());
        assertThat(publicationDelivery.getParticipantRef()).isEqualTo("Avinor");
        assertThat(publicationDelivery.getDataObjects()).isNotNull();
    }

    private void assertValidCompositeFrame(PublicationDeliveryStructure publicationDelivery) {
        CompositeFrame compositeFrame = NetexTestUtils.getFrames(CompositeFrame.class, publicationDelivery.getDataObjects().getCompositeFrameOrCommonFrame()).get(0);
        assertThat(compositeFrame.getValidityConditions()).isNotNull();

        assertThat(compositeFrame.getCodespaces()).isNotNull();
        assertThat(compositeFrame.getCodespaces().getCodespaceRefOrCodespace()).isNotEmpty();

        assertThat(compositeFrame.getFrameDefaults()).isNotNull();
    }

    private void assertValidResourceFrame(ResourceFrame resourceFrame) {
        assertThat(resourceFrame.getOrganisations()).isNotNull();
        assertThat(resourceFrame.getOrganisations().getOrganisation_()).isNotEmpty();
    }

    private void assertValidServiceFrame(PublicationDeliveryStructure publicationDelivery) {
        List<ServiceFrame> serviceFrames = NetexTestUtils.getFrames(ServiceFrame.class, NetexTestUtils.getDataObjectFrames(publicationDelivery));

        for (ServiceFrame serviceFrame : serviceFrames) {
            if (serviceFrame.getNetwork() != null) {
                assertValidNetwork(serviceFrame.getNetwork());
            } else {
                assertThat(serviceFrame.getRoutePoints()).isNotNull();
                assertThat(serviceFrame.getRoutePoints().getRoutePoint()).isNotEmpty();
                assertThat(serviceFrame.getRoutePoints().getRoutePoint()).hasSize(AirportIATA.values().length);

                assertThat(serviceFrame.getScheduledStopPoints()).isNotNull();
                assertThat(serviceFrame.getScheduledStopPoints().getScheduledStopPoint()).isNotEmpty();
                assertThat(serviceFrame.getScheduledStopPoints().getScheduledStopPoint()).hasSize(AirportIATA.values().length);

                assertThat(serviceFrame.getStopAssignments()).isNotNull();
                assertThat(serviceFrame.getStopAssignments().getStopAssignment()).isNotEmpty();
                assertThat(serviceFrame.getStopAssignments().getStopAssignment()).hasSize(AirportIATA.values().length);
            }
        }
    }

    private void assertValidNetwork(Network network) {
        assertThat(network.getChanged()).isNotNull();
        assertThat(network.getVersion()).isNotNull();
        assertThat(network.getId()).isNotNull();
        assertThat(network.getName()).isNotNull();
        assertThat(network.getTransportOrganisationRef()).isNotNull();
    }

}