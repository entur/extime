package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.model.AirlineDesignator;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.util.DateUtils;
import no.rutebanken.extime.util.NetexObjectFactory;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

@Component(value = "commonDataToNetexConverter")
public class CommonDataToNetexConverter {

    private static final Logger logger = LoggerFactory.getLogger(CommonDataToNetexConverter.class);

    @Autowired
    private ObjectFactory objectFactory;

    @Autowired
    private NetexObjectFactory netexObjectFactory;

    @Autowired
    private NetexCommonDataSet netexCommonDataSet;

    @Autowired
    private DateUtils dateUtils;

    @Value("${avinor.timetable.period.months}")
    private int numberOfMonthsInPeriod;

    @Value("${avinor.timetable.export.site}")
    private int isWithSiteFrame;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex() throws Exception {
        logger.info("Converting common data to NeTEx");
        OffsetDateTime publicationTimestamp = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of(DEFAULT_ZONE_ID));

        Codespace avinorCodespace = netexObjectFactory.createCodespace(AVINOR_XMLNS, AVINOR_XMLNSURL);
        Codespace nsrCodespace = netexObjectFactory.createCodespace(NSR_XMLNS, NSR_XMLNSURL);

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
        List<RoutePoint> routePoints = Lists.newArrayList();
        List<JAXBElement<PassengerStopAssignment>> stopAssignmentElements = Lists.newArrayList();

        for (AirportIATA airportIATA : airportIATAS) {
            String airportIataName = airportIATA.name();
            
            StopPlace stopPlace = netexCommonDataSet.getStopPlaceMap().get(airportIataName);
            stopPlaces.add(stopPlace);

            ScheduledStopPoint stopPoint = netexCommonDataSet.getStopPointMap().get(airportIataName);
            stopPoints.add(stopPoint);

            RoutePoint routePoint = netexCommonDataSet.getRoutePointMap().get(airportIataName);
            routePoints.add(routePoint);

            PassengerStopAssignment stopAssignment = netexCommonDataSet.getStopAssignmentMap().get(airportIataName);
            JAXBElement<PassengerStopAssignment> stopAssignmentElement = objectFactory.createPassengerStopAssignment(stopAssignment);
            stopAssignmentElements.add(stopAssignmentElement);
        }

        stopPlaces.sort(Comparator.comparing(StopPlace::getId));
        logger.info("Retrieved and populated NeTEx structure with {} stop places", stopPlaces.size());

        stopPoints.sort(Comparator.comparing(ScheduledStopPoint::getId));
        logger.info("Retrieved and populated NeTEx structure with {} stop points", stopPoints.size());

        Frames_RelStructure framesStruct = objectFactory.createFrames_RelStructure();
        framesStruct.getCommonFrame().add(netexObjectFactory.createResourceFrameElement(authorityElements, operatorElements));
        framesStruct.getCommonFrame().add(netexObjectFactory.createSiteFrameElement(stopPlaces));

        for (AirlineDesignator designator : AirlineDesignator.values()) {
            String designatorName = designator.name().toUpperCase();
            Network network = netexObjectFactory.createNetwork(publicationTimestamp, designatorName, null);
            framesStruct.getCommonFrame().add(netexObjectFactory.createNetworkServiceFrameElement(network));
        }

        framesStruct.getCommonFrame().add(netexObjectFactory.createCommonServiceFrameElement(routePoints, stopPoints, stopAssignmentElements));

        JAXBElement<CompositeFrame> compositeFrameElement = netexObjectFactory
                .createCompositeFrameElement(publicationTimestamp, framesStruct, dateUtils.generateAvailabilityPeriod(), avinorCodespace, nsrCodespace);

        logger.info("Done converting common data to NeTEx");

        return netexObjectFactory.createPublicationDeliveryStructureElement(publicationTimestamp, compositeFrameElement, "Description");
    }

}
