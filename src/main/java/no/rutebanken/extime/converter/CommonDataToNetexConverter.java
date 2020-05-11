package no.rutebanken.extime.converter;

import com.google.common.collect.Lists;
import no.rutebanken.extime.model.AirlineDesignator;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.util.DateUtils;
import no.rutebanken.extime.util.NetexObjectFactory;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.Branding;
import org.rutebanken.netex.model.Codespace;
import org.rutebanken.netex.model.CompositeFrame;
import org.rutebanken.netex.model.Frames_RelStructure;
import org.rutebanken.netex.model.Network;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.PassengerStopAssignment;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.RoutePoint;
import org.rutebanken.netex.model.ScheduledStopPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static no.rutebanken.extime.Constants.AVINOR_XMLNS;
import static no.rutebanken.extime.Constants.AVINOR_XMLNSURL;
import static no.rutebanken.extime.Constants.NSR_XMLNS;
import static no.rutebanken.extime.Constants.NSR_XMLNSURL;

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

    public JAXBElement<PublicationDeliveryStructure> convertToNetex() {
        logger.info("Converting common data to NeTEx");
        Instant publicationTimestamp = Instant.now();

        Codespace avinorCodespace = netexObjectFactory.createCodespace(AVINOR_XMLNS, AVINOR_XMLNSURL);
        Codespace nsrCodespace = netexObjectFactory.createCodespace(NSR_XMLNS, NSR_XMLNSURL);

        JAXBElement<Authority> avinorAuthorityElement = netexObjectFactory.createAvinorAuthorityElement();
        JAXBElement<Authority> nsrAuthorityElement = netexObjectFactory.createNsrAuthorityElement();
        List<JAXBElement<Authority>> authorityElements = Arrays.asList(avinorAuthorityElement, nsrAuthorityElement);

        List<JAXBElement<Operator>> operatorElements = new ArrayList<>(AirlineDesignator.values().length);
        List<JAXBElement<Branding>> brandingElements = new ArrayList<>(AirlineDesignator.values().length);

        for (AirlineDesignator designator : AirlineDesignator.values()) {
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
            String airportIataName = airportIATA.name();

            ScheduledStopPoint stopPoint = netexCommonDataSet.getStopPointMap().get(airportIataName);
            stopPoints.add(stopPoint);

            RoutePoint routePoint = netexCommonDataSet.getRoutePointMap().get(airportIataName);
            routePoints.add(routePoint);

            PassengerStopAssignment stopAssignment = netexCommonDataSet.getStopAssignmentMap().get(airportIataName);
            JAXBElement<PassengerStopAssignment> stopAssignmentElement = objectFactory.createPassengerStopAssignment(stopAssignment);
            stopAssignmentElements.add(stopAssignmentElement);
        }

        Frames_RelStructure framesStruct = objectFactory.createFrames_RelStructure();

        stopPoints.sort(Comparator.comparing(ScheduledStopPoint::getId));
        logger.info("Retrieved and populated NeTEx structure with {} stop points", stopPoints.size());
        framesStruct.getCommonFrame().add(netexObjectFactory.createResourceFrameElement(authorityElements, operatorElements, brandingElements));

        for (AirlineDesignator designator : AirlineDesignator.values()) {
            String designatorName = designator.name().toUpperCase();
            Network network = netexObjectFactory.createNetwork(publicationTimestamp, designatorName, null);
            networks.add(network);
        }

        framesStruct.getCommonFrame().add(netexObjectFactory.createCommonServiceFrameElement(networks, routePoints, stopPoints, stopAssignmentElements));

        JAXBElement<CompositeFrame> compositeFrameElement = netexObjectFactory
                .createCompositeFrameElement(publicationTimestamp, framesStruct, dateUtils.generateAvailabilityPeriod(), avinorCodespace, nsrCodespace);

        logger.info("Done converting common data to NeTEx");

        return netexObjectFactory.createPublicationDeliveryStructureElement(publicationTimestamp, compositeFrameElement);
    }

}
