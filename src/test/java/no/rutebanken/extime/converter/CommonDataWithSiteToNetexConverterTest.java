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

import javax.xml.bind.JAXBElement;
import java.time.OffsetDateTime;
import java.util.List;

import static no.rutebanken.extime.Constants.NETEX_PROFILE_VERSION;
import static no.rutebanken.extime.Constants.VERSION_ONE;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
@ActiveProfiles("test")
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {CamelRouteDisabler.class, CommonDataToNetexConverter.class})
@TestPropertySource(properties = {"avinor.timetable.export.site = true"})
public class CommonDataWithSiteToNetexConverterTest {

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

        SiteFrame siteFrame = NetexTestUtils.getFrames(SiteFrame.class, NetexTestUtils.getDataObjectFrames(publicationDelivery)).get(0);
        assertThat(siteFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(siteFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.SITE_FRAME_KEY), "SiteFrame");

        NetexTestUtils.verifyServiceFrameAttributes(publicationDelivery);

        assertValidResourceFrame(resourceFrame);
        assertValidSiteFrame(siteFrame);
        assertValidServiceFrame(publicationDelivery);
    }

    private void assertValidPublicationDelivery(PublicationDeliveryStructure publicationDelivery) {
        assertThat(publicationDelivery).isNotNull();
        assertThat(publicationDelivery.getVersion()).isEqualTo(NETEX_PROFILE_VERSION);
        assertThat(publicationDelivery.getPublicationTimestamp()).isNotNull().isBefore(OffsetDateTime.now());
        assertThat(publicationDelivery.getParticipantRef()).isEqualTo("Avinor");
        assertThat(publicationDelivery.getDataObjects()).isNotNull();
    }

    // TODO add more detailed assertions on object structures
    private void assertValidCompositeFrame(PublicationDeliveryStructure publicationDelivery) {
        CompositeFrame compositeFrame = NetexTestUtils.getFrames(CompositeFrame.class, publicationDelivery.getDataObjects().getCompositeFrameOrCommonFrame()).get(0);
        assertThat(compositeFrame.getValidityConditions()).isNotNull();

        assertThat(compositeFrame.getCodespaces()).isNotNull();
        assertThat(compositeFrame.getCodespaces().getCodespaceRefOrCodespace()).isNotEmpty();

        assertThat(compositeFrame.getFrameDefaults()).isNotNull();
    }

    // TODO add more detailed assertions on object structures
    private void assertValidResourceFrame(ResourceFrame resourceFrame) {
        assertThat(resourceFrame.getOrganisations()).isNotNull();
        assertThat(resourceFrame.getOrganisations().getOrganisation_()).isNotEmpty();
    }

    private void assertValidSiteFrame(SiteFrame siteFrame) {
        assertThat(siteFrame.getStopPlaces()).isNotNull();
        assertThat(siteFrame.getStopPlaces().getStopPlace()).isNotEmpty();
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

                StopAssignmentsInFrame_RelStructure stopAssignmentStruct = serviceFrame.getStopAssignments();
                assertThat(stopAssignmentStruct).isNotNull();
                assertThat(stopAssignmentStruct.getStopAssignment()).isNotEmpty();
                assertThat(stopAssignmentStruct.getStopAssignment()).hasSize(AirportIATA.values().length);

                for (JAXBElement<? extends StopAssignment_VersionStructure> stopAssignmentElement : stopAssignmentStruct.getStopAssignment()) {
                    PassengerStopAssignment stopAssignment = (PassengerStopAssignment) stopAssignmentElement.getValue();

//                    StopPlaceRefStructure stopPlaceRef = stopAssignment.getStopPlaceRef();
//                    assertThat(stopPlaceRef).isNotNull();
//                    assertThat(stopPlaceRef.getRef()).isNotNull().isNotEmpty().matches("^AVI:StopPlace:[A-Z]{3}$");

                    QuayRefStructure quayRef = stopAssignment.getQuayRef();
                    assertThat(quayRef).isNotNull();
                    assertThat(quayRef.getRef()).isNotNull().isNotEmpty().matches("^AVI:Quay:[A-Z]{3}$");
                }
            }
        }
    }

    private void assertValidNetwork(Network network) {
        assertThat(network.getChanged()).isNotNull();
        assertThat(network.getVersion()).isNotNull();
        assertThat(network.getId()).isNotNull();
        assertThat(network.getName()).isNotNull();
        assertThat(network.getTransportOrganisationRef()).isNotNull();
        assertThat(network.getTransportOrganisationRef().getValue().getVersion()).isNotNull();
        assertThat(network.getTransportOrganisationRef().getValue().getRef()).isNotNull().isNotEmpty().matches("AVI:Authority:Avinor");
    }
}