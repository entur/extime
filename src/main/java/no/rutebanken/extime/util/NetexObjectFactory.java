package no.rutebanken.extime.util;

import static no.rutebanken.extime.Constants.AVINOR_XMLNS;
import static no.rutebanken.extime.Constants.AVINOR_XMLNSURL;
import static no.rutebanken.extime.Constants.DASH;
import static no.rutebanken.extime.Constants.DEFAULT_END_EXCLUSIVE;
import static no.rutebanken.extime.Constants.DEFAULT_LANGUAGE;
import static no.rutebanken.extime.Constants.DEFAULT_START_INCLUSIVE;
import static no.rutebanken.extime.Constants.DEFAULT_ZONE_ID;
import static no.rutebanken.extime.Constants.NETEX_PROFILE_VERSION;
import static no.rutebanken.extime.Constants.NSR_XMLNS;
import static no.rutebanken.extime.Constants.NSR_XMLNSURL;
import static no.rutebanken.extime.Constants.VERSION_ONE;

import java.math.BigInteger;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBElement;

import org.rutebanken.netex.model.AllVehicleModesOfTransportEnumeration;
import org.rutebanken.netex.model.Authority;
import org.rutebanken.netex.model.AvailabilityCondition;
import org.rutebanken.netex.model.Codespace;
import org.rutebanken.netex.model.Codespaces_RelStructure;
import org.rutebanken.netex.model.CompositeFrame;
import org.rutebanken.netex.model.ContactStructure;
import org.rutebanken.netex.model.DayOfWeekEnumeration;
import org.rutebanken.netex.model.DayType;
import org.rutebanken.netex.model.DayTypeAssignment;
import org.rutebanken.netex.model.DayTypeAssignmentsInFrame_RelStructure;
import org.rutebanken.netex.model.DayTypeRefStructure;
import org.rutebanken.netex.model.DayTypeRefs_RelStructure;
import org.rutebanken.netex.model.DayTypesInFrame_RelStructure;
import org.rutebanken.netex.model.DestinationDisplay;
import org.rutebanken.netex.model.DestinationDisplayRefStructure;
import org.rutebanken.netex.model.DestinationDisplaysInFrame_RelStructure;
import org.rutebanken.netex.model.Frames_RelStructure;
import org.rutebanken.netex.model.JourneyPattern;
import org.rutebanken.netex.model.JourneyPatternRefStructure;
import org.rutebanken.netex.model.JourneyPatternsInFrame_RelStructure;
import org.rutebanken.netex.model.JourneysInFrame_RelStructure;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.LineRefStructure;
import org.rutebanken.netex.model.LinesInFrame_RelStructure;
import org.rutebanken.netex.model.LocaleStructure;
import org.rutebanken.netex.model.MultilingualString;
import org.rutebanken.netex.model.Network;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.Operator;
import org.rutebanken.netex.model.OperatorRefStructure;
import org.rutebanken.netex.model.OrganisationTypeEnumeration;
import org.rutebanken.netex.model.OrganisationsInFrame_RelStructure;
import org.rutebanken.netex.model.PassengerStopAssignment;
import org.rutebanken.netex.model.PointOnRoute;
import org.rutebanken.netex.model.PointRefStructure;
import org.rutebanken.netex.model.PointsInJourneyPattern_RelStructure;
import org.rutebanken.netex.model.PointsOnRoute_RelStructure;
import org.rutebanken.netex.model.PropertiesOfDay_RelStructure;
import org.rutebanken.netex.model.PropertyOfDay;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.QuayRefStructure;
import org.rutebanken.netex.model.ResourceFrame;
import org.rutebanken.netex.model.Route;
import org.rutebanken.netex.model.RoutePoint;
import org.rutebanken.netex.model.RoutePointRefStructure;
import org.rutebanken.netex.model.RoutePointsInFrame_RelStructure;
import org.rutebanken.netex.model.RouteRefStructure;
import org.rutebanken.netex.model.RoutesInFrame_RelStructure;
import org.rutebanken.netex.model.ScheduledStopPoint;
import org.rutebanken.netex.model.ScheduledStopPointRefStructure;
import org.rutebanken.netex.model.ScheduledStopPointsInFrame_RelStructure;
import org.rutebanken.netex.model.ServiceCalendarFrame;
import org.rutebanken.netex.model.ServiceFrame;
import org.rutebanken.netex.model.ServiceJourney;
import org.rutebanken.netex.model.SiteFrame;
import org.rutebanken.netex.model.StopAssignmentsInFrame_RelStructure;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.StopPlaceRefStructure;
import org.rutebanken.netex.model.StopPlacesInFrame_RelStructure;
import org.rutebanken.netex.model.StopPointInJourneyPattern;
import org.rutebanken.netex.model.StopPointInJourneyPatternRefStructure;
import org.rutebanken.netex.model.TimetableFrame;
import org.rutebanken.netex.model.TimetabledPassingTime;
import org.rutebanken.netex.model.TimetabledPassingTimes_RelStructure;
import org.rutebanken.netex.model.ValidityConditions_RelStructure;
import org.rutebanken.netex.model.VersionFrameDefaultsStructure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.model.AvailabilityPeriod;

@Component(value = "netexObjectFactory")
public class NetexObjectFactory {

    @Autowired
    private ObjectFactory objectFactory;

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

    private static final String WORK_DAYS_DISPLAY_NAME = "Ukedager (mandag til fredag)";
    private static final String SATURDAY_DISPLAY_NAME = "Helgdag (lørdag)";
    private static final String SUNDAY_DISPLAY_NAME = "Helgdag (søndag)";

    private static final String WORK_DAYS_LABEL = "weekday";
    private static final String SATURDAY_LABEL = "saturday";
    private static final String SUNDAY_LABEL = "sunday";

    private static final Map<DayOfWeek, DayOfWeekEnumeration> dayOfWeekMap = new HashMap<>();

    static {
        dayOfWeekMap.put(DayOfWeek.MONDAY, DayOfWeekEnumeration.MONDAY);
        dayOfWeekMap.put(DayOfWeek.TUESDAY, DayOfWeekEnumeration.TUESDAY);
        dayOfWeekMap.put(DayOfWeek.WEDNESDAY, DayOfWeekEnumeration.WEDNESDAY);
        dayOfWeekMap.put(DayOfWeek.THURSDAY, DayOfWeekEnumeration.THURSDAY);
        dayOfWeekMap.put(DayOfWeek.FRIDAY, DayOfWeekEnumeration.FRIDAY);
        dayOfWeekMap.put(DayOfWeek.SATURDAY, DayOfWeekEnumeration.SATURDAY);
        dayOfWeekMap.put(DayOfWeek.SUNDAY, DayOfWeekEnumeration.SUNDAY);
    }

    private Map<String, Route> routes = new HashMap<>();
    private Map<String, DestinationDisplay> destinationDisplays = new HashMap<>();

    public PublicationDeliveryStructure createPublicationDeliveryStructure(OffsetDateTime publicationTimestamp, JAXBElement<CompositeFrame> compositeFrame, String lineName) {
        NetexStaticDataSet.OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations().get(AVINOR_XMLNS.toLowerCase());
        PublicationDeliveryStructure.DataObjects dataObjects = objectFactory.createPublicationDeliveryStructureDataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrame);
        MultilingualString description = createMultilingualString(String.format("Line: %s", lineName));

        return objectFactory.createPublicationDeliveryStructure()
                .withVersion(NETEX_PROFILE_VERSION)
                .withPublicationTimestamp(publicationTimestamp)
                .withParticipantRef(avinorDataSet.getName())
                .withDescription(description)
                .withDataObjects(dataObjects);
    }

    public JAXBElement<PublicationDeliveryStructure> createPublicationDeliveryStructureElement(
            OffsetDateTime publicationTimestamp, JAXBElement<CompositeFrame> compositeFrame, String description) {

        NetexStaticDataSet.OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations().get(AVINOR_XMLNS.toLowerCase());

        PublicationDeliveryStructure.DataObjects dataObjects = objectFactory.createPublicationDeliveryStructureDataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrame);

        PublicationDeliveryStructure publicationDeliveryStructure = objectFactory.createPublicationDeliveryStructure()
                .withVersion(NETEX_PROFILE_VERSION)
                .withPublicationTimestamp(publicationTimestamp)
                .withParticipantRef(avinorDataSet.getName())
                //.withDescription(createMultilingualString(description)) // TODO find out if needed
                .withDataObjects(dataObjects);

        return objectFactory.createPublicationDelivery(publicationDeliveryStructure);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame(OffsetDateTime publicationTimestamp,
            AvailabilityPeriod availabilityPeriod, String airlineIata, String lineDesignation, Frames_RelStructure frames) {

        ValidityConditions_RelStructure validityConditionsStruct = objectFactory.createValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(createAvailabilityCondition(availabilityPeriod));

        Codespace avinorCodespace = createCodespace(AVINOR_XMLNS, AVINOR_XMLNSURL);
        Codespace nsrCodespace = createCodespace(NSR_XMLNS, NSR_XMLNSURL);

        Codespaces_RelStructure codespaces = objectFactory.createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace(Arrays.asList(avinorCodespace, nsrCodespace));

        LocaleStructure localeStructure = objectFactory.createLocaleStructure()
                .withTimeZone(DEFAULT_ZONE_ID)
                .withDefaultLanguage(DEFAULT_LANGUAGE);

        VersionFrameDefaultsStructure versionFrameDefaultsStructure = objectFactory.createVersionFrameDefaultsStructure()
                .withDefaultLocale(localeStructure);

        String compositeFrameId = NetexObjectIdCreator.createCompositeFrameId(
                AVINOR_XMLNS, Joiner.on(DASH).skipNulls().join(airlineIata, lineDesignation));

        CompositeFrame compositeFrame = objectFactory.createCompositeFrame()
                .withVersion(VERSION_ONE)
                .withCreated(publicationTimestamp)
                .withId(compositeFrameId)
                .withValidityConditions(validityConditionsStruct)
                .withCodespaces(codespaces)
                .withFrameDefaults(versionFrameDefaultsStructure)
                .withFrames(frames);

        return objectFactory.createCompositeFrame(compositeFrame);
    }

    public JAXBElement<CompositeFrame> createCompositeFrameElement(OffsetDateTime publicationTimestamp, Frames_RelStructure frames, AvailabilityPeriod availabilityPeriod, Codespace... codespaces) {

        ValidityConditions_RelStructure validityConditionsStruct = objectFactory.createValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(createAvailabilityCondition(availabilityPeriod));

        Codespaces_RelStructure codespacesStruct = objectFactory.createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace((Object[]) codespaces);

        LocaleStructure localeStructure = objectFactory.createLocaleStructure()
                .withTimeZone(DEFAULT_ZONE_ID)
                .withDefaultLanguage(DEFAULT_LANGUAGE);

        VersionFrameDefaultsStructure versionFrameDefaultsStructure = objectFactory.createVersionFrameDefaultsStructure()
                .withDefaultLocale(localeStructure);

        String compositeFrameId = NetexObjectIdCreator.createCompositeFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        CompositeFrame compositeFrame = objectFactory.createCompositeFrame()
                .withVersion(VERSION_ONE)
                .withCreated(publicationTimestamp)
                .withId(compositeFrameId)
                .withValidityConditions(validityConditionsStruct)
                .withCodespaces(codespacesStruct)
                .withFrameDefaults(versionFrameDefaultsStructure)
                .withFrames(frames);

        return objectFactory.createCompositeFrame(compositeFrame);
    }

    public JAXBElement<ResourceFrame> createResourceFrameElement(Collection<JAXBElement<Authority>> authorityElements,
            Collection<JAXBElement<Operator>> operatorElements) {

        OrganisationsInFrame_RelStructure organisationsStruct = objectFactory.createOrganisationsInFrame_RelStructure();

        String resourceFrameId = NetexObjectIdCreator.createResourceFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ResourceFrame resourceFrame = objectFactory.createResourceFrame()
                .withVersion(VERSION_ONE)
                .withId(resourceFrameId);
        resourceFrame.setOrganisations(organisationsStruct);

        for (Iterator<JAXBElement<Authority>> iterator = authorityElements.iterator(); iterator.hasNext(); ) {
            JAXBElement<Authority> authorityElement = iterator.next();
            resourceFrame.getOrganisations().getOrganisation_().add(authorityElement);
        }

        for (Iterator<JAXBElement<Operator>> iterator = operatorElements.iterator(); iterator.hasNext(); ) {
            JAXBElement<Operator> operatorElement = iterator.next();
            resourceFrame.getOrganisations().getOrganisation_().add(operatorElement);
        }

        return objectFactory.createResourceFrame(resourceFrame);
    }

    public JAXBElement<ResourceFrame> createResourceFrameElement(Operator operator) {
        OrganisationsInFrame_RelStructure organisationsStruct = objectFactory.createOrganisationsInFrame_RelStructure();
        organisationsStruct.getOrganisation_().add(createAirlineOperatorElement(operator));

        String resourceFrameId = NetexObjectIdCreator.createResourceFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ResourceFrame resourceFrame = objectFactory.createResourceFrame()
                .withVersion(VERSION_ONE)
                .withId(resourceFrameId);
        resourceFrame.setOrganisations(organisationsStruct);

        return objectFactory.createResourceFrame(resourceFrame);
    }

    public JAXBElement<SiteFrame> createSiteFrameElement(List<StopPlace> stopPlaces) {
        StopPlacesInFrame_RelStructure stopPlacesStruct = objectFactory.createStopPlacesInFrame_RelStructure();

        String siteFrameId = NetexObjectIdCreator.createSiteFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        SiteFrame siteFrame = objectFactory.createSiteFrame()
                .withVersion(VERSION_ONE)
                .withId(siteFrameId);
        siteFrame.setStopPlaces(stopPlacesStruct);

        stopPlaces.forEach(stopPlace -> stopPlacesStruct.getStopPlace().add(stopPlace));

        return objectFactory.createSiteFrame(siteFrame);
    }

    public JAXBElement<ServiceFrame> createCommonServiceFrameElement(List<ScheduledStopPoint> scheduledStopPoints,
            List<JAXBElement<PassengerStopAssignment>> stopAssignmentElements) {

        ScheduledStopPointsInFrame_RelStructure scheduledStopPointsStruct = objectFactory.createScheduledStopPointsInFrame_RelStructure();
        StopAssignmentsInFrame_RelStructure stopAssignmentsStruct = objectFactory.createStopAssignmentsInFrame_RelStructure();

        String serviceFrameId = NetexObjectIdCreator.createServiceFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ServiceFrame serviceFrame = objectFactory.createServiceFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceFrameId);
        serviceFrame.setScheduledStopPoints(scheduledStopPointsStruct);
        serviceFrame.setStopAssignments(stopAssignmentsStruct);

        scheduledStopPoints.forEach(stopPoint -> scheduledStopPointsStruct.getScheduledStopPoint().add(stopPoint));
        stopAssignmentElements.forEach(stopAssignmentElement -> stopAssignmentsStruct.getStopAssignment().add(stopAssignmentElement));

        return objectFactory.createServiceFrame(serviceFrame);
    }

    public JAXBElement<ServiceFrame> createServiceFrame(OffsetDateTime publicationTimestamp, String airlineName,
            String airlineIata, List<RoutePoint> routePoints, List<Route> routes, Line line,
            List<DestinationDisplay> destinationDisplays, List<JourneyPattern> journeyPatterns) {

        String networkId = NetexObjectIdCreator.createNetworkId(AVINOR_XMLNS, airlineIata);

        Network network = objectFactory.createNetwork()
                .withVersion(VERSION_ONE)
                .withChanged(publicationTimestamp)
                .withId(networkId)
                .withName(createMultilingualString(airlineName));

        RoutePointsInFrame_RelStructure routePointsInFrame = objectFactory.createRoutePointsInFrame_RelStructure()
                .withRoutePoint(routePoints);

        RoutesInFrame_RelStructure routesInFrame = objectFactory.createRoutesInFrame_RelStructure();
        for (Route route : routes) {
            JAXBElement<Route> routeElement = objectFactory.createRoute(route);
            routesInFrame.getRoute_().add(routeElement);
        }

        LinesInFrame_RelStructure linesInFrame = objectFactory.createLinesInFrame_RelStructure();
        linesInFrame.getLine_().add(objectFactory.createLine(line));

        DestinationDisplaysInFrame_RelStructure destinationDisplayStruct = objectFactory.createDestinationDisplaysInFrame_RelStructure()
                .withDestinationDisplay(destinationDisplays);

        JourneyPatternsInFrame_RelStructure journeyPatternsInFrame = objectFactory.createJourneyPatternsInFrame_RelStructure();
        for (JourneyPattern journeyPattern : journeyPatterns) {
            JAXBElement<JourneyPattern> journeyPatternElement = objectFactory.createJourneyPattern(journeyPattern);
            journeyPatternsInFrame.getJourneyPattern_OrJourneyPatternView().add(journeyPatternElement);
        }

        String serviceFrameId = NetexObjectIdCreator.createServiceFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ServiceFrame serviceFrame = objectFactory.createServiceFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceFrameId)
                .withNetwork(network)
                .withRoutePoints(routePointsInFrame)
                .withRoutes(routesInFrame)
                .withLines(linesInFrame)
                .withDestinationDisplays(destinationDisplayStruct)
                .withJourneyPatterns(journeyPatternsInFrame);

        return objectFactory.createServiceFrame(serviceFrame);
    }

    public JAXBElement<TimetableFrame> createTimetableFrame(List<ServiceJourney> serviceJourneys) {
        JourneysInFrame_RelStructure journeysInFrameRelStructure = objectFactory.createJourneysInFrame_RelStructure();
        journeysInFrameRelStructure.getDatedServiceJourneyOrDeadRunOrServiceJourney().addAll(serviceJourneys);

        String timetableFrameId = NetexObjectIdCreator.createTimetableFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        TimetableFrame timetableFrame = objectFactory.createTimetableFrame()
                .withVersion(VERSION_ONE)
                .withId(timetableFrameId)
                .withVehicleJourneys(journeysInFrameRelStructure);

        return objectFactory.createTimetableFrame(timetableFrame);
    }

    public JAXBElement<ServiceCalendarFrame> createServiceCalendarFrame(Map<String, DayType> dayTypes, Map<String, DayTypeAssignment> dayTypeAssignments) {
        DayTypesInFrame_RelStructure dayTypesStruct = objectFactory.createDayTypesInFrame_RelStructure();
        for (DayType dayType : dayTypes.values()) {
            JAXBElement<DayType> dayTypeElement = objectFactory.createDayType(dayType);
            dayTypesStruct.getDayType_().add(dayTypeElement);
        }

        List<DayTypeAssignment> dayTypeAssignmentList = new ArrayList<>(dayTypeAssignments.values());
        dayTypeAssignmentList.sort(Comparator.comparing(DayTypeAssignment::getOrder));
        DayTypeAssignmentsInFrame_RelStructure dayTypeAssignmentsStruct = objectFactory.createDayTypeAssignmentsInFrame_RelStructure();
        dayTypeAssignmentList.forEach(dayTypeAssignment -> dayTypeAssignmentsStruct.getDayTypeAssignment().add(dayTypeAssignment));

        String serviceCalendarFrameId = NetexObjectIdCreator.createServiceCalendarFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ServiceCalendarFrame serviceCalendarFrame = objectFactory.createServiceCalendarFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceCalendarFrameId)
                .withDayTypes(dayTypesStruct)
                .withDayTypeAssignments(dayTypeAssignmentsStruct);

        return objectFactory.createServiceCalendarFrame(serviceCalendarFrame);
    }

    public JAXBElement<AvailabilityCondition> createAvailabilityCondition(AvailabilityPeriod availabilityPeriod) {
        String availabilityConditionId = NetexObjectIdCreator.createAvailabilityConditionId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        AvailabilityCondition availabilityCondition = objectFactory.createAvailabilityCondition()
                .withVersion(VERSION_ONE)
                .withId(availabilityConditionId)
                .withFromDate(availabilityPeriod.getPeriodFromDateTime())
                .withToDate(availabilityPeriod.getPeriodToDateTime());

        return objectFactory.createAvailabilityCondition(availabilityCondition);
    }

    public Network createNetwork(OffsetDateTime publicationTimestamp, String airlineIata, String airlineName) {
        String networkId = NetexObjectIdCreator.createNetworkId(AVINOR_XMLNS, airlineIata);

        return objectFactory.createNetwork()
                .withVersion(VERSION_ONE)
                .withChanged(publicationTimestamp)
                .withId(networkId)
                .withName(createMultilingualString(airlineName));
    }

    public JAXBElement<Authority> createAvinorAuthorityElement() {
        NetexStaticDataSet.OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations()
                .get(AVINOR_XMLNS.toLowerCase());

        String authorityId = NetexObjectIdCreator.createAuthorityId(AVINOR_XMLNS, avinorDataSet.getName());

        Authority authority = objectFactory.createAuthority()
                .withVersion(VERSION_ONE)
                .withId(authorityId)
                .withCompanyNumber(avinorDataSet.getCompanyNumber())
                .withName(createMultilingualString(avinorDataSet.getName()))
                .withLegalName(createMultilingualString(avinorDataSet.getLegalName()))
                .withContactDetails(createContactStructure(avinorDataSet.getPhone(), avinorDataSet.getUrl()))
                .withOrganisationType(OrganisationTypeEnumeration.AUTHORITY);
        return objectFactory.createAuthority(authority);
    }

    public JAXBElement<Authority> createNsrAuthorityElement() {
        NetexStaticDataSet.OrganisationDataSet nsrDataSet = netexStaticDataSet.getOrganisations()
                .get(NSR_XMLNS.toLowerCase());

        String authorityId = NetexObjectIdCreator.createAuthorityId(NSR_XMLNS, NSR_XMLNS);

        Authority authority = objectFactory.createAuthority()
                .withVersion(VERSION_ONE)
                .withId(authorityId)
                .withCompanyNumber(nsrDataSet.getCompanyNumber())
                .withName(createMultilingualString(nsrDataSet.getName()))
                .withLegalName(createMultilingualString(nsrDataSet.getLegalName()))
                .withContactDetails(createContactStructure(nsrDataSet.getPhone(), nsrDataSet.getUrl()))
                .withOrganisationType(OrganisationTypeEnumeration.AUTHORITY);
        return objectFactory.createAuthority(authority);
    }

    public JAXBElement<Operator> createAirlineOperatorElement(String airlineIata) {
        NetexStaticDataSet.OrganisationDataSet airlineDataSet = netexStaticDataSet.getOrganisations()
                .get(airlineIata.toLowerCase());

        String operatorId = NetexObjectIdCreator.createOperatorId(AVINOR_XMLNS, airlineIata);

        Operator operator = objectFactory.createOperator()
                .withVersion(VERSION_ONE)
                .withId(operatorId)
                .withCompanyNumber(airlineDataSet.getCompanyNumber())
                .withName(createMultilingualString(airlineDataSet.getName()))
                .withLegalName(createMultilingualString((airlineDataSet.getLegalName())))
                .withContactDetails(createContactStructure(airlineDataSet.getPhone(), airlineDataSet.getUrl()))
                .withCustomerServiceContactDetails(createContactStructure(airlineDataSet.getPhone(), airlineDataSet.getUrl()))
                .withOrganisationType(OrganisationTypeEnumeration.OPERATOR);

        return objectFactory.createOperator(operator);
    }

    public JAXBElement<Operator> createAirlineOperatorElement(Operator operator) {
        return objectFactory.createOperator(operator);
    }

    public Operator createInfrequentAirlineOperatorElement(String airlineIata, String airlineName, String operatorId) {
        return objectFactory.createOperator()
                .withVersion(VERSION_ONE)
                .withId(operatorId)
                .withCompanyNumber("999999999")
                .withName(createMultilingualString(airlineName.trim()))
                .withLegalName(createMultilingualString((airlineName.trim().toUpperCase())))
                .withContactDetails(createContactStructure("0047 99999999", String.format("http://%s.no/", airlineIata.toLowerCase())))
                .withCustomerServiceContactDetails(createContactStructure("0047 99999999", String.format("http://%s.no/", airlineIata.toLowerCase())))
                .withOrganisationType(OrganisationTypeEnumeration.OPERATOR);
    }

    public ContactStructure createContactStructure(String phone, String url) {
        return objectFactory.createContactStructure()
                .withPhone(phone)
                .withUrl(url);
    }

    public Codespace createCodespace(String id, String xmlnsUrl) {
        return objectFactory.createCodespace()
                .withId(id.toLowerCase())
                .withXmlns(id)
                .withXmlnsUrl(xmlnsUrl);
    }

    public Line createLine(String airlineIata, String lineDesignation, String lineName) {
        String lineId = NetexObjectIdCreator.createLineId(AVINOR_XMLNS, new String[] {airlineIata, lineDesignation});

        return objectFactory.createLine()
                .withVersion(VERSION_ONE)
                .withId(lineId)
                .withName(createMultilingualString(lineName))
                .withTransportMode(AllVehicleModesOfTransportEnumeration.AIR)
                .withPublicCode(lineDesignation);
    }

    public Route createRoute(String lineId, String objectId, String routeName, PointsOnRoute_RelStructure pointsOnRoute) {
        LineRefStructure lineRefStruct = createLineRefStructure(lineId);
        JAXBElement<LineRefStructure> lineRefStructElement = objectFactory.createLineRef(lineRefStruct);

        String routeId = NetexObjectIdCreator.createRouteId(AVINOR_XMLNS, objectId);

        return objectFactory.createRoute()
                .withVersion(VERSION_ONE)
                .withId(routeId)
                .withName(createMultilingualString(routeName))
                .withLineRef(lineRefStructElement)
                .withPointsInSequence(pointsOnRoute);
    }

    public PointOnRoute createPointOnRoute(String objectId, String routePointId) {
        String pointOnRouteId = NetexObjectIdCreator.createPointOnRouteId(AVINOR_XMLNS, objectId);
        RoutePointRefStructure routePointRefStruct = createRoutePointRefStructure(routePointId);
        JAXBElement<RoutePointRefStructure> routePointRefStructElement = objectFactory.createRoutePointRef(routePointRefStruct);

        return objectFactory.createPointOnRoute()
                .withVersion(VERSION_ONE)
                .withId(pointOnRouteId)
                .withPointRef(routePointRefStructElement);
    }

    public JourneyPattern createJourneyPattern(String objectId, String routeId, PointsInJourneyPattern_RelStructure pointsInJourneyPattern) {
        String journeyPatternId = NetexObjectIdCreator.createJourneyPatternId(AVINOR_XMLNS, objectId);
        RouteRefStructure routeRefStructure = createRouteRefStructure(routeId);

        return objectFactory.createJourneyPattern()
                .withVersion(VERSION_ONE)
                .withId(journeyPatternId)
                .withRouteRef(routeRefStructure)
                .withPointsInSequence(pointsInJourneyPattern);
    }

    public StopPointInJourneyPattern createStopPointInJourneyPattern(String objectId, BigInteger orderIndex, String stopPointId) {
        String stopPointInJourneyPatternId = NetexObjectIdCreator.createStopPointInJourneyPatternId(AVINOR_XMLNS, objectId);
        ScheduledStopPointRefStructure stopPointRefStruct = createScheduledStopPointRefStructure(stopPointId, Boolean.FALSE);
        JAXBElement<ScheduledStopPointRefStructure> stopPointRefStructElement = objectFactory.createScheduledStopPointRef(stopPointRefStruct);

        return objectFactory.createStopPointInJourneyPattern()
                .withVersion(VERSION_ONE)
                .withId(stopPointInJourneyPatternId)
                .withOrder(orderIndex)
                .withScheduledStopPointRef(stopPointRefStructElement);
    }

    public DestinationDisplay getDestinationDisplay(String objectId) {
        if (destinationDisplays.containsKey(objectId)) {
            return destinationDisplays.get(objectId);
        } else {
            throw new RuntimeException("Missing reference to destination display");
        }
    }

    public DestinationDisplay createDestinationDisplay(String objectId) {
        String destinationDisplayId = NetexObjectIdCreator.createDestinationDisplayId(AVINOR_XMLNS, objectId);

        return objectFactory.createDestinationDisplay()
                .withVersion(VERSION_ONE)
                .withId(destinationDisplayId);
    }

    public DestinationDisplay getDestinationDisplay(String objectId, String frontText) {
        return destinationDisplays.computeIfAbsent(objectId, s -> objectFactory.createDestinationDisplay()
                .withVersion(VERSION_ONE)
                .withId(NetexObjectIdCreator.createDestinationDisplayId(AVINOR_XMLNS, objectId))
                .withFrontText(createMultilingualString(frontText)));
    }

    public DestinationDisplay createDestinationDisplay(String objectId, String frontText, boolean isStopDisplay) {
        String destinationDisplayId = NetexObjectIdCreator.createDestinationDisplayId(AVINOR_XMLNS, objectId);

        DestinationDisplay destinationDisplay = objectFactory.createDestinationDisplay()
                .withVersion(VERSION_ONE)
                .withId(destinationDisplayId)
                .withFrontText(createMultilingualString(frontText));

        if (isStopDisplay && !destinationDisplays.containsKey(destinationDisplayId)) {
            destinationDisplays.put(destinationDisplayId, destinationDisplay);
        }

        return destinationDisplay;
    }

    public ServiceJourney createServiceJourney(String objectId, String lineId, String flightId, DayTypeRefs_RelStructure dayTypeRefsStruct,
            String journeyPatternId, TimetabledPassingTimes_RelStructure passingTimesRelStruct) {

        String serviceJourneyId = NetexObjectIdCreator.createServiceJourneyId(AVINOR_XMLNS, objectId);

        TimetabledPassingTime departurePassingTime = passingTimesRelStruct.getTimetabledPassingTime().get(0);
        OffsetTime departureTime = departurePassingTime.getDepartureTime();

        JourneyPatternRefStructure journeyPatternRefStruct = objectFactory.createJourneyPatternRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(journeyPatternId);
        JAXBElement<JourneyPatternRefStructure> journeyPatternRefStructElement = objectFactory.createJourneyPatternRef(journeyPatternRefStruct);

        LineRefStructure lineRefStruct = createLineRefStructure(lineId);
        JAXBElement<LineRefStructure> lineRefStructElement = objectFactory.createLineRef(lineRefStruct);

        return objectFactory.createServiceJourney()
                .withVersion(VERSION_ONE)
                .withId(serviceJourneyId)
                .withPublicCode(flightId)
                .withDepartureTime(departureTime)
                .withDayTypes(dayTypeRefsStruct)
                .withJourneyPatternRef(journeyPatternRefStructElement)
                .withLineRef(lineRefStructElement)
                .withPassingTimes(passingTimesRelStruct);
    }


    public TimetabledPassingTime createTimetabledPassingTime(String stopPointInJourneyPatternId) {
        StopPointInJourneyPatternRefStructure stopPointInJourneyPatternRefStruct =
                createStopPointInJourneyPatternRefStructure(stopPointInJourneyPatternId);

        JAXBElement<StopPointInJourneyPatternRefStructure> stopPointInJourneyPatternRefStructElement = objectFactory
                .createStopPointInJourneyPatternRef(stopPointInJourneyPatternRefStruct);

        return objectFactory.createTimetabledPassingTime()
                .withPointInJourneyPatternRef(stopPointInJourneyPatternRefStructElement);
    }

    private List<DayType> createDayTypes(Set<DayOfWeek> weekDaysPattern, String flightId) {
        Map<Boolean, List<DayOfWeek>> dayOfWeeksByDayType = weekDaysPattern.stream()
                .collect(Collectors.partitioningBy(dayOfWeek -> dayOfWeek.query(DateUtils.WorkDays::isWorkDay)));

        List<DayOfWeek> workDays = dayOfWeeksByDayType.get(Boolean.TRUE);
        List<DayOfWeek> weekendDays = dayOfWeeksByDayType.get(Boolean.FALSE);
        List<DayType> dayTypes = Lists.newArrayList();

        if (!workDays.isEmpty()) {
            dayTypes.add(createDayType(workDays, flightId, WORK_DAYS_LABEL, WORK_DAYS_DISPLAY_NAME));
        }

        if (!weekendDays.isEmpty()) {
            if (weekendDays.contains(DayOfWeek.SATURDAY)) {
                int index = weekendDays.indexOf(DayOfWeek.SATURDAY);
                List<DayOfWeek> saturday = Lists.newArrayList(weekendDays.get(index));
                dayTypes.add(createDayType(saturday, flightId, SATURDAY_LABEL, SATURDAY_DISPLAY_NAME));
            }
            if (weekendDays.contains(DayOfWeek.SUNDAY)) {
                int index = weekendDays.indexOf(DayOfWeek.SUNDAY);
                List<DayOfWeek> sunday = Lists.newArrayList(weekendDays.get(index));
                dayTypes.add(createDayType(sunday, flightId, SUNDAY_LABEL, SUNDAY_DISPLAY_NAME));
            }
        }

        return dayTypes;
    }

    public DayType createDayType(List<DayOfWeek> daysOfWeekPattern, String flightId, String objectId, String name) {
        List<DayOfWeekEnumeration> daysOfWeek = Lists.newArrayList();
        daysOfWeekPattern.forEach(dayOfWeek -> daysOfWeek.add(dayOfWeekMap.get(dayOfWeek)));

        PropertyOfDay propertyOfDayWeekDays = objectFactory.createPropertyOfDay();
        propertyOfDayWeekDays.getDaysOfWeek().addAll(daysOfWeek);

        PropertiesOfDay_RelStructure propertiesOfDay = objectFactory.createPropertiesOfDay_RelStructure()
                .withPropertyOfDay(propertyOfDayWeekDays);

        String dayTypeId = NetexObjectIdCreator.createDayTypeId(AVINOR_XMLNS, String.format("%s_%s", flightId, objectId));

        return objectFactory.createDayType()
                .withVersion(VERSION_ONE)
                .withId(dayTypeId)
                .withName(createMultilingualString(name))
                .withProperties(propertiesOfDay);
    }

    public DayType createDayType(String dayTypeId) {
        return objectFactory.createDayType()
                .withVersion(VERSION_ONE)
                .withId(dayTypeId);
    }

    public DayTypeAssignment createDayTypeAssignment(String objectId, Integer order, OffsetDateTime dateOfOperation, String dayTypeId) {
        String dayTypeAssignmentId = NetexObjectIdCreator.createDayTypeAssignmentId(AVINOR_XMLNS, objectId);

        DayTypeRefStructure dayTypeRefStruct = createDayTypeRefStructure(dayTypeId);
        JAXBElement<DayTypeRefStructure> dayTypeRefStructElement = objectFactory.createDayTypeRef(dayTypeRefStruct);

        return objectFactory.createDayTypeAssignment()
                .withVersion(VERSION_ONE)
                .withId(dayTypeAssignmentId)
                .withOrder(BigInteger.valueOf(order))
                .withDate(dateOfOperation)
                .withDayTypeRef(dayTypeRefStructElement);
    }

    public MultilingualString createMultilingualString(String value) {
        return objectFactory.createMultilingualString().withValue(value);
    }

    // reference structures creation

    public LineRefStructure createLineRefStructure(String lineId) {
        return objectFactory.createLineRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(lineId);
    }

    public OperatorRefStructure createOperatorRefStructure(String operatorId, boolean withRefValidation) {
        OperatorRefStructure operatorRefStruct = objectFactory.createOperatorRefStructure()
                .withRef(operatorId);
        return withRefValidation ? operatorRefStruct.withVersion(VERSION_ONE) : operatorRefStruct;
    }

    public RouteRefStructure createRouteRefStructure(String routeId) {
        return objectFactory.createRouteRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(routeId);
    }

    public List<RouteRefStructure> createRouteRefStructures(List<Route> routes) {
        return routes.stream()
                .map(Route::getId)
                .collect(Collectors.toSet()).stream()
                .map(routeId -> objectFactory.createRouteRefStructure().withVersion(VERSION_ONE).withRef(routeId))
                .collect(Collectors.toList());
    }

    public StopPlaceRefStructure createStopPlaceRefStructure(String stopPlaceId, boolean withRefValidation) {
        StopPlaceRefStructure stopPlaceRefStruct = objectFactory.createStopPlaceRefStructure()
                .withRef(stopPlaceId);
        return withRefValidation ? stopPlaceRefStruct.withVersion(VERSION_ONE) : stopPlaceRefStruct;
    }

    public QuayRefStructure createQuayRefStructure(String quayId, boolean withRefValidation) {
        QuayRefStructure quayRefStructure = objectFactory.createQuayRefStructure()
                .withRef(quayId);
        return withRefValidation ? quayRefStructure.withVersion(VERSION_ONE) : quayRefStructure;
    }

    public ScheduledStopPointRefStructure createScheduledStopPointRefStructure(String stopPointId, boolean withRefValidation) {
        ScheduledStopPointRefStructure scheduledStopPointRefStruct = objectFactory.createScheduledStopPointRefStructure()
                .withRef(stopPointId);
        return withRefValidation ? scheduledStopPointRefStruct.withVersion(VERSION_ONE) : scheduledStopPointRefStruct;
    }

    public StopPointInJourneyPatternRefStructure createStopPointInJourneyPatternRefStructure(String stopPointInJourneyPatternId) {
        return objectFactory.createStopPointInJourneyPatternRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(stopPointInJourneyPatternId);
    }

    public PointRefStructure createPointRefStructure(String stopPointId, boolean withRefValidation) {
        PointRefStructure pointRefStruct = objectFactory.createPointRefStructure()
                .withRef(stopPointId);
        return withRefValidation ? pointRefStruct.withVersion(VERSION_ONE) : pointRefStruct;
    }

    public RoutePointRefStructure createRoutePointRefStructure(String stopPointId) {
        return objectFactory.createRoutePointRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(stopPointId);
    }

    public DayTypeRefStructure createDayTypeRefStructure(String dayTypeId) {
        return objectFactory.createDayTypeRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(dayTypeId);
    }

    public DestinationDisplayRefStructure createDestinationDisplayRefStructure(String destinationDisplayId) {
        return objectFactory.createDestinationDisplayRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(destinationDisplayId);
    }

    public void clearReferentials(){
        routes.clear();
        destinationDisplays.clear();
    }

}
