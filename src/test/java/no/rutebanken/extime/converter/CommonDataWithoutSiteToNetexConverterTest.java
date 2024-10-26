package no.rutebanken.extime.converter;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import jakarta.xml.bind.JAXBElement;
import no.rutebanken.extime.Constants;
import no.rutebanken.extime.ExtimeRouteBuilderIntegrationTestBase;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.util.NetexObjectIdTypes;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static no.rutebanken.extime.Constants.*;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("unchecked")
class CommonDataWithoutSiteToNetexConverterTest extends ExtimeRouteBuilderIntegrationTestBase {

    @Autowired
    private CommonDataToNetexConverter netexConverter;

    @Test
    void verifyPublicationDelivery() {
        PublicationDeliveryStructure publicationDelivery = netexConverter.convertToNetex(Map.of()).getValue();
        assertValidPublicationDelivery(publicationDelivery);

        NetexTestUtils.verifyCompositeFrameAttributes(publicationDelivery);
        assertValidCompositeFrame(publicationDelivery);

        ResourceFrame resourceFrame = NetexTestUtils.getFrames(ResourceFrame.class, NetexTestUtils.getDataObjectFrames(publicationDelivery)).getFirst();
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
        assertThat(publicationDelivery.getPublicationTimestamp()).isNotNull().isBeforeOrEqualTo(ZonedDateTime.now(ZoneId.of(Constants.DEFAULT_ZONE_ID)).toLocalDateTime());
        assertThat(publicationDelivery.getParticipantRef()).isEqualTo("Avinor");
        assertThat(publicationDelivery.getDataObjects()).isNotNull();
    }

    private void assertValidCompositeFrame(PublicationDeliveryStructure publicationDelivery) {
        CompositeFrame compositeFrame = NetexTestUtils.getFrames(CompositeFrame.class, publicationDelivery.getDataObjects().getCompositeFrameOrCommonFrame()).getFirst();
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

                StopAssignmentsInFrame_RelStructure stopAssignmentStruct = serviceFrame.getStopAssignments();
                assertThat(stopAssignmentStruct).isNotNull();
                assertThat(stopAssignmentStruct.getStopAssignment()).isNotEmpty();
                assertThat(stopAssignmentStruct.getStopAssignment()).hasSize(AirportIATA.values().length);

                for (JAXBElement<? extends StopAssignment_VersionStructure> stopAssignmentElement : stopAssignmentStruct.getStopAssignment()) {
                    PassengerStopAssignment stopAssignment = (PassengerStopAssignment) stopAssignmentElement.getValue();

                    StopPlaceRefStructure stopPlaceRef = stopAssignment.getStopPlaceRef().getValue();
                    assertThat(stopPlaceRef).isNotNull();
                    assertThat(stopPlaceRef.getRef()).isNotNull().isNotEmpty();

                    String stopPlaceIdSuffix = Iterables.getLast(Splitter.on(COLON).trimResults().split(stopPlaceRef.getRef()));

                    if (StringUtils.isNotEmpty(stopPlaceIdSuffix)) {
                        assertThat(stopPlaceRef.getRef()).matches("^NSR:StopPlace:\\d{5}$");
                    }

                    QuayRefStructure quayRef = stopAssignment.getQuayRef().getValue();
                    assertThat(quayRef).isNotNull();
                    assertThat(quayRef.getRef()).isNotNull().isNotEmpty();

                    String quayIdSuffix = Iterables.getLast(Splitter.on(COLON).trimResults().split(quayRef.getRef()));

                    if (StringUtils.isNotEmpty(quayIdSuffix)) {
                        assertThat(quayRef.getRef()).matches("^NSR:Quay:\\d{5}$");
                    }
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
