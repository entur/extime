package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.model.AirlineDesignator;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.util.NetexObjectFactory;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static no.rutebanken.extime.Constants.*;

// TODO write unit test for this component
@Component(value = "commonDataToNetexConverter")
public class CommonDataToNetexConverter {

    private static final Logger logger = LoggerFactory.getLogger(CommonDataToNetexConverter.class);

    @Autowired
    private ObjectFactory objectFactory;

    @Autowired
    private NetexObjectFactory netexObjectFactory;

    @Autowired
    private NetexCommonDataSet netexCommonDataSet;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex() throws Exception {
        logger.info("Converting common data to NeTEx");
        OffsetDateTime publicationTimestamp = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of(DEFAULT_ZONE_ID));

        Codespace avinorCodespace = netexObjectFactory.createCodespace(AVINOR_AUTHORITY_ID, AVINOR_XMLNS_URL);
        Codespace nsrCodespace = netexObjectFactory.createCodespace(NSR_AUTHORITY_ID, NSR_XMLNS_URL);

        // TODO consider separating authorities and operators in yaml configuration file for static data
        JAXBElement<Authority> avinorAuthorityElement = netexObjectFactory.createAvinorAuthorityElement();
        JAXBElement<Authority> nsrAuthorityElement = netexObjectFactory.createNsrAuthorityElement();
        List<JAXBElement<Authority>> authorityElements = Arrays.asList(avinorAuthorityElement, nsrAuthorityElement);

        List<JAXBElement<Operator>> operatorElements = new ArrayList<>(AirlineDesignator.values().length);

        for (AirlineDesignator designator : AirlineDesignator.values()) {
            String designatorName = designator.name().toUpperCase();
            JAXBElement<Operator> operatorElement = netexObjectFactory.createAirlineOperatorElement(designatorName);
            operatorElements.add(operatorElement);
        }

        List<AirportIATA> airportIATAS = Lists.newArrayList(AirportIATA.values());
        airportIATAS.sort(Comparator.comparing(Enum::name));

        List<StopPlace> stopPlaces = Lists.newArrayList();
        List<ScheduledStopPoint> stopPoints = Lists.newArrayList();
        List<JAXBElement<PassengerStopAssignment>> stopAssignmentElements = Lists.newArrayList();

        for (AirportIATA airportIATA : airportIATAS) {
            String airportIataName = airportIATA.name();

            StopPlace stopPlace = netexCommonDataSet.getStopPlaceMap().get(airportIataName);
            stopPlaces.add(stopPlace);

            ScheduledStopPoint stopPoint = netexCommonDataSet.getStopPointMap().get(airportIataName);
            stopPoints.add(stopPoint);

            PassengerStopAssignment stopAssignment = netexCommonDataSet.getStopAssignmentMap().get(airportIataName);
            JAXBElement<PassengerStopAssignment> stopAssignmentElement = objectFactory.createPassengerStopAssignment(stopAssignment);
            stopAssignmentElements.add(stopAssignmentElement);
        }

        stopPlaces.sort(Comparator.comparing(StopPlace::getId));
        logger.info("Retrieved and populated NeTEx structure with {} stop places", stopPlaces.size());

        stopPoints.sort(Comparator.comparing(ScheduledStopPoint::getId));
        logger.info("Retrieved and populated NeTEx structure with {} stop points", stopPoints.size());

        JAXBElement<ResourceFrame> resourceFrameElement = netexObjectFactory.createResourceFrameElement(authorityElements, operatorElements);
        JAXBElement<SiteFrame> siteFrameElement = netexObjectFactory.createSiteFrameElement(stopPlaces);
        JAXBElement<ServiceFrame> serviceFrameElement = netexObjectFactory.createCommonServiceFrameElement(stopPoints, stopAssignmentElements);

        Frames_RelStructure framesStruct = objectFactory.createFrames_RelStructure();
        framesStruct.getCommonFrame().add(resourceFrameElement);
        framesStruct.getCommonFrame().add(siteFrameElement);
        framesStruct.getCommonFrame().add(serviceFrameElement);

        JAXBElement<CompositeFrame> compositeFrameElement = netexObjectFactory
                .createCompositeFrameElement(publicationTimestamp, framesStruct, avinorCodespace, nsrCodespace);

        logger.info("Done converting common data to NeTEx");

        return netexObjectFactory.createPublicationDeliveryStructureElement(publicationTimestamp, compositeFrameElement, "Description");
    }

}
