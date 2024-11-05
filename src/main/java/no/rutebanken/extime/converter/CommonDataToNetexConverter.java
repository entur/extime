package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.model.AirlineIATA;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.util.DateUtils;
import no.rutebanken.extime.util.NetexObjectFactory;
import org.apache.camel.ExchangeProperty;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.xml.bind.JAXBElement;
import java.time.Instant;
import java.util.*;

import static no.rutebanken.extime.Constants.AVINOR_XMLNS;
import static no.rutebanken.extime.Constants.AVINOR_XMLNSURL;
import static no.rutebanken.extime.Constants.NSR_XMLNS;
import static no.rutebanken.extime.Constants.NSR_XMLNSURL;

@Component(value = "commonDataToNetexConverter")
public class CommonDataToNetexConverter {

    private static final Logger logger = LoggerFactory.getLogger(CommonDataToNetexConverter.class);

    public static final String PROPERTY_NSR_QUAY_MAP = "NsrQuayMap";

    private final ObjectFactory objectFactory;

    private final NetexObjectFactory netexObjectFactory;

    private final NetexCommonDataSet netexCommonDataSet;

    private final DateUtils dateUtils;

    public CommonDataToNetexConverter(ObjectFactory objectFactory, NetexObjectFactory netexObjectFactory, NetexCommonDataSet netexCommonDataSet, DateUtils dateUtils) {
        this.objectFactory = objectFactory;
        this.netexObjectFactory = netexObjectFactory;
        this.netexCommonDataSet = netexCommonDataSet;
        this.dateUtils = dateUtils;
    }

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(
            @ExchangeProperty(PROPERTY_NSR_QUAY_MAP) Map<String, Quay> nsrQuayMap) {

        logger.info("Converting common data to NeTEx");
        Instant publicationTimestamp = Instant.now();

        Codespace avinorCodespace = netexObjectFactory.createCodespace(AVINOR_XMLNS, AVINOR_XMLNSURL);
        Codespace nsrCodespace = netexObjectFactory.createCodespace(NSR_XMLNS, NSR_XMLNSURL);

        JAXBElement<Authority> avinorAuthorityElement = netexObjectFactory.createAvinorAuthorityElement();
        List<JAXBElement<Authority>> authorityElements = List.of(avinorAuthorityElement);

        List<JAXBElement<Operator>> operatorElements = new ArrayList<>(AirlineIATA.values().length);
        List<JAXBElement<Branding>> brandingElements = new ArrayList<>(AirlineIATA.values().length);

        for (AirlineIATA designator : AirlineIATA.values()) {
            String designatorName = designator.name().toUpperCase();
            JAXBElement<Operator> operatorElement = netexObjectFactory.createAirlineOperatorElement(designatorName);
            operatorElements.add(operatorElement);

            JAXBElement<Branding> brandingElement = netexObjectFactory.createAirlineBrandingElement(designatorName);
            brandingElements.add(brandingElement);
        }

        List<AirportIATA> airportIATAS = Lists.newArrayList(AirportIATA.values());
        airportIATAS.sort(Comparator.comparing(Enum::name));

        List<ScheduledStopPoint> stopPoints = Lists.newArrayList();
        List<RoutePoint> routePoints = Lists.newArrayList();
        List<JAXBElement<PassengerStopAssignment>> stopAssignmentElements = Lists.newArrayList();
        Set<Network> networks = new HashSet<>();

        for (AirportIATA airportIATA : airportIATAS) {
            String airportIATAName = airportIATA.name();

            ScheduledStopPoint stopPoint = netexCommonDataSet.getStopPointMap().get(airportIATAName);
            stopPoints.add(stopPoint);

            RoutePoint routePoint = netexCommonDataSet.getRoutePointMap().get(airportIATAName);
            routePoints.add(routePoint);

            PassengerStopAssignment stopAssignment = netexCommonDataSet.getStopAssignmentMap().get(airportIATAName);

            var nsrQuay = nsrQuayMap.get(stopAssignment.getQuayRef().getValue().getRef());



            PassengerStopAssignment passengerStopAssignment = nsrQuay != null
                    ? stopAssignment.withQuayRef(objectFactory.createQuayRef(netexObjectFactory.createQuayRefStructure(nsrQuay.getId())))
                    : stopAssignment;

            var stopAssignmentElement = objectFactory.createPassengerStopAssignment(passengerStopAssignment);
            stopAssignmentElements.add(stopAssignmentElement);
        }

        var framesStruct = objectFactory.createFrames_RelStructure();

        stopPoints.sort(Comparator.comparing(ScheduledStopPoint::getId));
        logger.info("Retrieved and populated NeTEx structure with {} stop points", stopPoints.size());
        framesStruct.getCommonFrame().add(
                netexObjectFactory.createResourceFrameElement(authorityElements, operatorElements, brandingElements)
        );

        for (AirlineIATA designator : AirlineIATA.values()) {
            String designatorName = designator.name().toUpperCase();
            Network network = netexObjectFactory.createNetwork(publicationTimestamp, designatorName, null);
            networks.add(network);
        }

        framesStruct.getCommonFrame().add(
                netexObjectFactory.createCommonServiceFrameElement(
                        networks, routePoints, stopPoints, stopAssignmentElements
                )
        );

        var compositeFrameElement = netexObjectFactory.createCompositeFrameElement(
                publicationTimestamp, framesStruct, dateUtils.generateAvailabilityPeriod(), avinorCodespace, nsrCodespace
        );

        logger.info("Done converting common data to NeTEx");
        return netexObjectFactory.createPublicationDeliveryStructureElement(publicationTimestamp, compositeFrameElement);
    }
}