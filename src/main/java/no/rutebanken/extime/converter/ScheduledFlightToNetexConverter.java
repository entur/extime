package no.rutebanken.extime.converter;

import org.springframework.stereotype.Component;

// TODO rename this class to something more describing, like FlightLineToNetexConverter

@Component(value = "scheduledFlightToNetexConverter")
public class ScheduledFlightToNetexConverter {

/*
    private static final Logger logger = LoggerFactory.getLogger(ScheduledFlightToNetexConverter.class);

    private static final String WORK_DAYS_DISPLAY_NAME = "Ukedager (mandag til fredag)";
    private static final String SATURDAY_DISPLAY_NAME = "Helgdag (lørdag)";
    private static final String SUNDAY_DISPLAY_NAME = "Helgdag (søndag)";

    private static final String WORK_DAYS_LABEL = "weekday";
    private static final String SATURDAY_LABEL = "saturday";
    private static final String SUNDAY_LABEL = "sunday";

    private static final HashMap<DayOfWeek, DayOfWeekEnumeration> dayOfWeekMap = new HashMap<>();

    private Map<String, String> routeIdDesignationMap = new HashMap<>();
    private Map<String, JourneyPattern> routeDesignationPatternMap = new HashMap<>();
    private Map<String, DayType> dayTypes = new HashMap<>();
    private Map<String, DayTypeAssignment> dayTypeAssignments = new HashMap<>();

    static {
        dayOfWeekMap.put(DayOfWeek.MONDAY, DayOfWeekEnumeration.MONDAY);
        dayOfWeekMap.put(DayOfWeek.TUESDAY, DayOfWeekEnumeration.TUESDAY);
        dayOfWeekMap.put(DayOfWeek.WEDNESDAY, DayOfWeekEnumeration.WEDNESDAY);
        dayOfWeekMap.put(DayOfWeek.THURSDAY, DayOfWeekEnumeration.THURSDAY);
        dayOfWeekMap.put(DayOfWeek.FRIDAY, DayOfWeekEnumeration.FRIDAY);
        dayOfWeekMap.put(DayOfWeek.SATURDAY, DayOfWeekEnumeration.SATURDAY);
        dayOfWeekMap.put(DayOfWeek.SUNDAY, DayOfWeekEnumeration.SUNDAY);
    }

    @Autowired
    private NetexCommonDataSet netexCommonDataSet;

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

    @Autowired
    private ObjectFactory objectFactory;

    @Autowired
    private NetexObjectFactory netexObjectFactory;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex(LineDataSet lineDataSet) throws Exception {
        OffsetDateTime publicationTimestamp = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of(DEFAULT_ZONE_ID));
        AvailabilityPeriod availabilityPeriod = lineDataSet.getAvailabilityPeriod();
        String airlineIata = lineDataSet.getAirlineIata();

        String operatorId = NetexObjectIdCreator.createOperatorId(AVINOR_XMLNS, airlineIata);
        boolean isFrequentOperator = isCommonDesignator(airlineIata);

        Line line = netexObjectFactory.createLine(lineDataSet.getAirlineIata(), lineDataSet.getLineDesignation(), lineDataSet.getLineName());
        line.setOperatorRef(isFrequentOperator ? netexObjectFactory.createOperatorRefStructure(operatorId, Boolean.FALSE) :
                netexObjectFactory.createOperatorRefStructure(operatorId, Boolean.TRUE));

        List<RoutePoint> routePoints = createRoutePoints(lineDataSet.getFlightRoutes());
        List<Route> routes = createRoutes(line, lineDataSet.getFlightRoutes());
        List<RouteRefStructure> routeRefStructures = netexObjectFactory.createRouteRefStructures(routes);
        RouteRefs_RelStructure routeRefStruct = objectFactory.createRouteRefs_RelStructure().withRouteRef(routeRefStructures);
        line.setRoutes(routeRefStruct);

        List<JourneyPattern> journeyPatterns = createJourneyPatterns(routes);
        List<ServiceJourney> serviceJourneys = createServiceJourneys(line, lineDataSet.getRouteJourneys());

        Frames_RelStructure frames = objectFactory.createFrames_RelStructure();

        if (!isFrequentOperator) {
            logger.warn("Infrequent operator identified by id : {}", airlineIata);
            Operator operator = netexObjectFactory.createInfrequentAirlineOperatorElement(airlineIata, lineDataSet.getAirlineName(), operatorId);
            JAXBElement<ResourceFrame> resourceFrameElement = netexObjectFactory.createResourceFrameElement(operator);
            frames.getCommonFrame().add(resourceFrameElement);
        }

        JAXBElement<ServiceFrame> serviceFrame = createServiceFrame(publicationTimestamp, lineDataSet.getAirlineName(),
                lineDataSet.getAirlineIata(), routePoints, routes, line, journeyPatterns);
        frames.getCommonFrame().add(serviceFrame);

        JAXBElement<TimetableFrame> timetableFrame = createTimetableFrame(serviceJourneys);
        frames.getCommonFrame().add(timetableFrame);

        //JAXBElement<ServiceCalendarFrame> serviceCalendarFrame = createServiceCalendarFrame(dayTypes);
        JAXBElement<ServiceCalendarFrame> serviceCalendarFrame = createServiceCalendarFrame();
        frames.getCommonFrame().add(serviceCalendarFrame);

        //cleanStopPointsFromTempValues(scheduledStopPoints); // TODO fix this to remove short names from stop points, or move to common converter

        JAXBElement<CompositeFrame> compositeFrame = createCompositeFrame(publicationTimestamp, availabilityPeriod,
                lineDataSet.getAirlineIata(), lineDataSet.getLineDesignation(), frames);

        PublicationDeliveryStructure publicationDeliveryStructure = createPublicationDeliveryStructure(
                publicationTimestamp, compositeFrame, lineDataSet.getLineName());

        return objectFactory.createPublicationDelivery(publicationDeliveryStructure);
    }

    public OperatorRefStructure createOperatorRefStructure(String operatorId, boolean withRefValidation) {
        OperatorRefStructure operatorRefStruct = objectFactory.createOperatorRefStructure()
                .withRef(operatorId);
        return withRefValidation ? operatorRefStruct.withVersion(VERSION_ONE) : operatorRefStruct;
    }

    public PublicationDeliveryStructure createPublicationDeliveryStructure(OffsetDateTime publicationTimestamp,
            JAXBElement<CompositeFrame> compositeFrame, String lineName) {
        OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations().get(AVINOR_XMLNS.toLowerCase());

        DataObjects dataObjects = objectFactory.createPublicationDeliveryStructureDataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrame);

        MultilingualString description = netexObjectFactory.createMultilingualString(String.format("Line: %s", lineName));

        return objectFactory.createPublicationDeliveryStructure()
                .withVersion(NETEX_PROFILE_VERSION)
                .withPublicationTimestamp(publicationTimestamp)
                .withParticipantRef(avinorDataSet.getName())
                .withDescription(description)
                .withDataObjects(dataObjects);
    }

    public JAXBElement<CompositeFrame> createCompositeFrame(OffsetDateTime publicationTimestamp,
            AvailabilityPeriod availabilityPeriod, String airlineIata, String lineDesignation, Frames_RelStructure frames) {

        ValidityConditions_RelStructure validityConditionsStruct = objectFactory.createValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(createAvailabilityCondition(availabilityPeriod));

        Codespace avinorCodespace = netexObjectFactory.createCodespace(AVINOR_XMLNS, AVINOR_XMLNSURL);
        Codespace nsrCodespace = netexObjectFactory.createCodespace(NSR_XMLNS, NSR_XMLNSURL);

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

    public JAXBElement<ServiceFrame> createServiceFrame(OffsetDateTime publicationTimestamp, String airlineName,
            String airlineIata, List<RoutePoint> routePoints, List<Route> routes, Line line, List<JourneyPattern> journeyPatterns) {

        String networkId = NetexObjectIdCreator.createNetworkId(AVINOR_XMLNS, airlineIata);

        Network network = objectFactory.createNetwork()
                .withVersion(VERSION_ONE)
                .withChanged(publicationTimestamp)
                .withId(networkId)
                .withName(netexObjectFactory.createMultilingualString(airlineName));

        RoutePointsInFrame_RelStructure routePointsInFrame = objectFactory.createRoutePointsInFrame_RelStructure()
                .withRoutePoint(routePoints);

        RoutesInFrame_RelStructure routesInFrame = objectFactory.createRoutesInFrame_RelStructure();
        for (Route route : routes) {
            JAXBElement<Route> routeElement = objectFactory.createRoute(route);
            routesInFrame.getRoute_().add(routeElement);
        }

        LinesInFrame_RelStructure linesInFrame = objectFactory.createLinesInFrame_RelStructure();
        linesInFrame.getLine_().add(objectFactory.createLine(line));

        JourneyPatternsInFrame_RelStructure journeyPatternsInFrame = objectFactory.createJourneyPatternsInFrame_RelStructure();
        for (JourneyPattern journeyPattern : journeyPatterns) {
            JAXBElement<JourneyPattern> journeyPatternElement = objectFactory.createJourneyPattern(journeyPattern);
            journeyPatternsInFrame.getJourneyPattern_OrJourneyPatternView().add(journeyPatternElement);
        }

        String serviceFrameId = NetexObjectIdCreator.createTimetableFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ServiceFrame serviceFrame = objectFactory.createServiceFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceFrameId)
                .withNetwork(network)
                .withRoutePoints(routePointsInFrame)
                .withRoutes(routesInFrame)
                .withLines(linesInFrame)
                .withJourneyPatterns(journeyPatternsInFrame);

        return objectFactory.createServiceFrame(serviceFrame);
    }

    // TODO implement operating period on calendar frame
    public JAXBElement<ServiceCalendarFrame> createServiceCalendarFrame(List<DayType> dayTypes) {
        DayTypesInFrame_RelStructure dayTypesStructure = objectFactory.createDayTypesInFrame_RelStructure();
        dayTypes.forEach(dayType -> dayTypesStructure.getDayType_().add(objectFactory.createDayType(dayType)));

        String serviceCalendarFrameId = NetexObjectIdCreator.createServiceCalendarFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ServiceCalendarFrame serviceCalendarFrame = objectFactory.createServiceCalendarFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceCalendarFrameId)
                .withDayTypes(dayTypesStructure);

        return objectFactory.createServiceCalendarFrame(serviceCalendarFrame);
    }

    public JAXBElement<ServiceCalendarFrame> createServiceCalendarFrame() {
        DayTypesInFrame_RelStructure dayTypesStruct = objectFactory.createDayTypesInFrame_RelStructure();
        for (DayType dayType : dayTypes.values()) {
            JAXBElement<DayType> dayTypeElement = objectFactory.createDayType(dayType);
            dayTypesStruct.getDayType_().add(dayTypeElement);
        }

        DayTypeAssignmentsInFrame_RelStructure dayTypeAssignmentsStruct = objectFactory.createDayTypeAssignmentsInFrame_RelStructure();
        dayTypeAssignments.values().forEach(dayTypeAssignment -> dayTypeAssignmentsStruct.getDayTypeAssignment().add(dayTypeAssignment));

        String serviceCalendarFrameId = NetexObjectIdCreator.createServiceCalendarFrameId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ServiceCalendarFrame serviceCalendarFrame = objectFactory.createServiceCalendarFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceCalendarFrameId)
                .withDayTypes(dayTypesStruct)
                .withDayTypeAssignments(dayTypeAssignmentsStruct);

        return objectFactory.createServiceCalendarFrame(serviceCalendarFrame);
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
                .withName(netexObjectFactory.createMultilingualString(name))
                .withProperties(propertiesOfDay);
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

    public List<ServiceJourney> createServiceJourneyList(ScheduledFlight scheduledFlight, List<DayType> dayTypes,
            JourneyPattern journeyPattern, Line line) throws IllegalArgumentException {

        List<ServiceJourney> serviceJourneyList = new ArrayList<>();
        TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory.createTimetabledPassingTimes_RelStructure();

        PointsInJourneyPattern_RelStructure pointsInSequence = journeyPattern.getPointsInSequence();
        List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence = pointsInSequence
                .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern();

        JourneyPatternRefStructure journeyPatternRefStructure = objectFactory.createJourneyPatternRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(journeyPattern.getId());
        JAXBElement<JourneyPatternRefStructure> journeyPatternRefStructureElement = objectFactory.createJourneyPatternRef(journeyPatternRefStructure);

        DayTypeRefs_RelStructure dayTypeStructure = objectFactory.createDayTypeRefs_RelStructure();

        dayTypes.forEach(dayType -> {
            DayTypeRefStructure dayTypeRefStruct = netexObjectFactory.createDayTypeRefStructure(dayType.getId());
            dayTypeStructure.getDayTypeRef().add(objectFactory.createDayTypeRef(dayTypeRefStruct));
        });

        // TODO swithc order of check (check for stopovers first)
        if (!scheduledFlight.hasStopovers()) {
            TimetabledPassingTime departurePassingTime = netexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(0).getId());
            departurePassingTime.setDepartureTime(scheduledFlight.getTimeOfDeparture());
            passingTimesRelStructure.withTimetabledPassingTime(departurePassingTime);

            TimetabledPassingTime arrivalPassingTime = netexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(1).getId());
            arrivalPassingTime.setArrivalTime(scheduledFlight.getTimeOfArrival());
            passingTimesRelStructure.withTimetabledPassingTime(arrivalPassingTime);

            // TODO move to factory
            LineRefStructure lineRefStruct = objectFactory.createLineRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(line.getId());
            JAXBElement<LineRefStructure> lineRefStructElement = objectFactory.createLineRef(lineRefStruct);

            String serviceJourneyId = NetexObjectIdCreator.createServiceJourneyId(AVINOR_XMLNS, scheduledFlight.getAirlineFlightId());

            // TODO move to factory
            ServiceJourney serviceJourney = objectFactory.createServiceJourney()
                    .withVersion(VERSION_ONE)
                    .withId(serviceJourneyId)
                    .withPublicCode(scheduledFlight.getAirlineFlightId())
                    .withDepartureTime(scheduledFlight.getTimeOfDeparture())
                    .withDayTypes(dayTypeStructure)
                    .withJourneyPatternRef(journeyPatternRefStructureElement)
                    .withLineRef(lineRefStructElement)
                    .withPassingTimes(passingTimesRelStructure);

            serviceJourneyList.add(serviceJourney);
            return serviceJourneyList;

        } else if (scheduledFlight.hasStopovers()) {
            List<ScheduledStopover> scheduledStopovers = scheduledFlight.getScheduledStopovers();
            Iterator<ScheduledStopover> stopoverIterator = scheduledStopovers.iterator();
            Iterator<PointInLinkSequence_VersionedChildStructure> pointInLinkSequenceIterator = pointsInLinkSequence.iterator();

            while (stopoverIterator.hasNext() && pointInLinkSequenceIterator.hasNext()) {
                ScheduledStopover scheduledStopover = stopoverIterator.next();
                PointInLinkSequence_VersionedChildStructure pointInLinkSequence = pointInLinkSequenceIterator.next();
                TimetabledPassingTime passingTime = netexObjectFactory.createTimetabledPassingTime(pointInLinkSequence.getId());

                if (scheduledStopover.getArrivalTime() != null) {
                    passingTime.setArrivalTime(scheduledStopover.getArrivalTime());
                }
                if (scheduledStopover.getDepartureTime() != null) {
                    passingTime.setDepartureTime(scheduledStopover.getDepartureTime());
                }

                passingTimesRelStructure.withTimetabledPassingTime(passingTime);
            }

            // TODO move to factory
            LineRefStructure lineRefStruct = objectFactory.createLineRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(line.getId());
            JAXBElement<LineRefStructure> lineRefStructElement = objectFactory.createLineRef(lineRefStruct);

            String serviceJourneyId = NetexObjectIdCreator.createServiceJourneyId(AVINOR_XMLNS, scheduledFlight.getAirlineFlightId());

            // TODO move to factory
            ServiceJourney serviceJourney = objectFactory.createServiceJourney()
                    .withVersion(VERSION_ONE)
                    .withId(serviceJourneyId)
                    .withPublicCode(scheduledFlight.getAirlineFlightId())
                    .withDepartureTime(scheduledStopovers.get(0).getDepartureTime())
                    .withDayTypes(dayTypeStructure)
                    .withJourneyPatternRef(journeyPatternRefStructureElement)
                    .withLineRef(lineRefStructElement)
                    .withPassingTimes(passingTimesRelStructure);

            serviceJourneyList.add(serviceJourney);
            return serviceJourneyList;
        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    public List<RoutePoint> createRoutePoints(ScheduledFlight scheduledFlight) {
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();

        if (!scheduledFlight.hasStopovers()) {

            RoutePoint departureRoutePoint = routePointMap.get(scheduledFlight.getDepartureAirportIATA());
            RoutePoint arrivalRoutePoint = routePointMap.get(scheduledFlight.getArrivalAirportIATA());
            return Lists.newArrayList(departureRoutePoint, arrivalRoutePoint);

        } else if (scheduledFlight.hasStopovers()) {

            List<ScheduledStopover> scheduledStopovers = scheduledFlight.getScheduledStopovers();
            List<RoutePoint> routePoints = new ArrayList<>();

            Set<String> iataCodes = scheduledStopovers.stream()
                    .map(ScheduledStopover::getAirportIATA)
                    .collect(Collectors.toSet());

            for (String iata : iataCodes) {
                RoutePoint routePoint = routePointMap.get(iata);
                routePoints.add(routePoint);
            }

            return routePoints;

        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }
    }

    public List<RoutePoint> createRoutePoints(List<FlightRoute> flightRoutes) {
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();

        return flightRoutes.stream()
                .flatMap(flightRoute -> flightRoute.getRoutePointsInSequence().stream())
                .distinct()
                .sorted(Comparator.comparing(iata -> iata))
                .map(routePointMap::get)
                .collect(Collectors.toList());
    }

    public Route createRoute(String flightId, String routePath, ScheduledFlight scheduledFlight, Line line) {
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();
        PointsOnRoute_RelStructure pointsOnRoute = objectFactory.createPointsOnRoute_RelStructure();

        if (!scheduledFlight.hasStopovers()) {
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, 2);

            RoutePoint departureRoutePoint = routePointMap.get(scheduledFlight.getDepartureAirportIATA());
            RoutePoint arrivalRoutePoint = routePointMap.get(scheduledFlight.getArrivalAirportIATA());

            PointOnRoute departurePointOnRoute = netexObjectFactory.createPointOnRoute(
                    idSequence[0], departureRoutePoint.getId());

            PointOnRoute arrivalPointOnRoute = netexObjectFactory.createPointOnRoute(
                    idSequence[1], arrivalRoutePoint.getId());

            pointsOnRoute.getPointOnRoute().add(departurePointOnRoute);
            pointsOnRoute.getPointOnRoute().add(arrivalPointOnRoute);

        } else if (scheduledFlight.hasStopovers()) {
            List<ScheduledStopover> scheduledStopovers = scheduledFlight.getScheduledStopovers();
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, scheduledStopovers.size());

            for (int i = 0; i < scheduledStopovers.size(); i++) {
                ScheduledStopover scheduledStopover = scheduledStopovers.get(i);
                RoutePoint routePoint = routePointMap.get(scheduledStopover.getAirportIATA());
                PointOnRoute pointOnRoute = netexObjectFactory.createPointOnRoute(idSequence[i], routePoint.getId());
                pointsOnRoute.getPointOnRoute().add(pointOnRoute);
            }
        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }

        // TODO find out what to use for route id
        String routeId = NetexObjectIdCreator.createRouteId(AVINOR_XMLNS, flightId);

        LineRefStructure lineRefStruct = objectFactory.createLineRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(line.getId());
        JAXBElement<LineRefStructure> lineRefStructElement = objectFactory.createLineRef(lineRefStruct);

        return objectFactory.createRoute()
                .withVersion(VERSION_ONE)
                .withId(routeId)
                .withName(netexObjectFactory.createMultilingualString(routePath))
                .withLineRef(lineRefStructElement)
                .withPointsInSequence(pointsOnRoute);
    }

    private List<Route> createRoutes(Line line, List<FlightRoute> flightRoutes) {
        Map<String, RoutePoint> routePointMap = netexCommonDataSet.getRoutePointMap();
        List<Route> routes = Lists.newArrayList();

        if (!routeIdDesignationMap.isEmpty()) {
            routeIdDesignationMap.clear();
        }

        for (FlightRoute flightRoute : flightRoutes) {
            PointsOnRoute_RelStructure pointsOnRoute = objectFactory.createPointsOnRoute_RelStructure();
            List<String> routePointsInSequence = flightRoute.getRoutePointsInSequence();
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, routePointsInSequence.size());

            for (int i = 0; i < routePointsInSequence.size(); i++) {
                RoutePoint routePoint = routePointMap.get(routePointsInSequence.get(i));
                PointOnRoute pointOnRoute = netexObjectFactory.createPointOnRoute(idSequence[i], routePoint.getId());
                pointsOnRoute.getPointOnRoute().add(pointOnRoute);
            }

            String routeId = NetexObjectIdCreator.createRouteId(AVINOR_XMLNS, flightRoute.getRouteDesignation());

            LineRefStructure lineRefStruct = objectFactory.createLineRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(line.getId());
            JAXBElement<LineRefStructure> lineRefStructElement = objectFactory.createLineRef(lineRefStruct);

            Route route = objectFactory.createRoute()
                    .withVersion(VERSION_ONE)
                    .withId(routeId)
                    .withName(netexObjectFactory.createMultilingualString(flightRoute.getRouteName()))
                    .withLineRef(lineRefStructElement)
                    .withPointsInSequence(pointsOnRoute);
            routes.add(route);

            if (!routeIdDesignationMap.containsKey(routeId)) {
                routeIdDesignationMap.put(routeId, flightRoute.getRouteDesignation());
            }
        }

        routes.sort(Comparator.comparing(Route::getId));
        return routes;
    }

    public JourneyPattern createJourneyPattern(Route route, ScheduledFlight scheduledFlight) {
        Map<String, ScheduledStopPoint> stopPointMap = netexCommonDataSet.getStopPointMap();
        PointsInJourneyPattern_RelStructure pointsInJourneyPattern = objectFactory.createPointsInJourneyPattern_RelStructure();

        if (!scheduledFlight.hasStopovers()) {
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, 2);

            ScheduledStopPoint departureStopPoint = stopPointMap.get(scheduledFlight.getDepartureAirportIATA());
            ScheduledStopPoint arrivalStopPoint = stopPointMap.get(scheduledFlight.getArrivalAirportIATA());

            StopPointInJourneyPattern departureStopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
                    idSequence[0], BigInteger.valueOf(1L), departureStopPoint.getId());
            departureStopPointInJourneyPattern.setForAlighting(Boolean.FALSE);

            StopPointInJourneyPattern arrivalStopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
                    idSequence[1], BigInteger.valueOf(2L), arrivalStopPoint.getId());
            arrivalStopPointInJourneyPattern.setForBoarding(Boolean.FALSE);

            pointsInJourneyPattern.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()
                    .add(departureStopPointInJourneyPattern);

            pointsInJourneyPattern.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()
                    .add(arrivalStopPointInJourneyPattern);

        } else if (scheduledFlight.hasStopovers()) {
            List<ScheduledStopover> scheduledStopovers = scheduledFlight.getScheduledStopovers();
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, scheduledStopovers.size());

            for (int i = 0; i < scheduledStopovers.size(); i++) {
                ScheduledStopover scheduledStopover = scheduledStopovers.get(i);
                ScheduledStopPoint scheduledStopPoint = stopPointMap.get(scheduledStopover.getAirportIATA());

                StopPointInJourneyPattern stopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
                        idSequence[i], BigInteger.valueOf(i + 1), scheduledStopPoint.getId());

                if (i == 0) {
                    stopPointInJourneyPattern.setForAlighting(false);
                }

                if (i == scheduledStopovers.size() - 1) {
                    stopPointInJourneyPattern.setForBoarding(false);
                }

                pointsInJourneyPattern.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern()
                        .add(stopPointInJourneyPattern);
            }

        } else {
            throw new IllegalArgumentException("Illegal instance class: " + scheduledFlight.getClass().getName());
        }

        RouteRefStructure routeRefStructure = objectFactory.createRouteRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(route.getId());

        int journeyPatternObjectId = NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE);
        String journeyPatternId = NetexObjectIdCreator.createJourneyPatternId(AVINOR_XMLNS, String.valueOf(journeyPatternObjectId));

        return objectFactory.createJourneyPattern()
                .withVersion(VERSION_ONE)
                .withId(journeyPatternId)
                .withRouteRef(routeRefStructure)
                .withPointsInSequence(pointsInJourneyPattern);
    }

    public List<JourneyPattern> createJourneyPatterns(List<Route> routes) {
        Map<String, ScheduledStopPoint> stopPointMap = netexCommonDataSet.getStopPointMap();
        List<JourneyPattern> journeyPatterns = Lists.newArrayList();

        if (!routeDesignationPatternMap.isEmpty()) {
            routeDesignationPatternMap.clear();
        }

        for (Route route : routes) {
            PointsInJourneyPattern_RelStructure pointsInJourneyPattern = objectFactory.createPointsInJourneyPattern_RelStructure();
            List<PointOnRoute> pointsOnRoute = route.getPointsInSequence().getPointOnRoute();
            String[] idSequence = NetexObjectIdCreator.generateIdSequence(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE, pointsOnRoute.size());

            for (int i = 0; i < pointsOnRoute.size(); i++) {
                String pointIdRef = pointsOnRoute.get(i).getPointRef().getValue().getRef();
                String airportIata = Iterables.getLast(Splitter.on(COLON).trimResults().split(pointIdRef));
                ScheduledStopPoint scheduledStopPoint = stopPointMap.get(airportIata);

                StopPointInJourneyPattern stopPointInJourneyPattern = netexObjectFactory.createStopPointInJourneyPattern(
                        idSequence[i], BigInteger.valueOf(i + 1), scheduledStopPoint.getId());

                if (i == 0) {
                    stopPointInJourneyPattern.setForAlighting(false);
                }

                if (i == pointsOnRoute.size() - 1) {
                    stopPointInJourneyPattern.setForBoarding(false);
                }

                pointsInJourneyPattern.getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern().add(stopPointInJourneyPattern);
            }

            RouteRefStructure routeRefStructure = objectFactory.createRouteRefStructure()
                    .withVersion(VERSION_ONE)
                    .withRef(route.getId());

            int journeyPatternObjectId = NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE);
            String journeyPatternId = NetexObjectIdCreator.createJourneyPatternId(AVINOR_XMLNS, String.valueOf(journeyPatternObjectId));

            JourneyPattern journeyPattern = objectFactory.createJourneyPattern()
                    .withVersion(VERSION_ONE)
                    .withId(journeyPatternId)
                    .withRouteRef(routeRefStructure)
                    .withPointsInSequence(pointsInJourneyPattern);
            journeyPatterns.add(journeyPattern);

            if (routeIdDesignationMap.containsKey(route.getId())) {
                String routeDesignation = routeIdDesignationMap.get(route.getId());
                if (!routeDesignationPatternMap.containsKey(routeDesignation)) {
                    routeDesignationPatternMap.put(routeDesignation, journeyPattern);
                }
            }
        }

        journeyPatterns.sort(Comparator.comparing(JourneyPattern::getId));
        return journeyPatterns;
    }

    public List<ServiceJourney> createServiceJourneys(Line line, Map<String, Map<String, List<ScheduledFlight>>> routeJourneys) {
        List<ServiceJourney> serviceJourneyList = new ArrayList<>();

        if (!dayTypes.isEmpty()) {
            dayTypes.clear();
        }
        if (!dayTypeAssignments.isEmpty()) {
            dayTypeAssignments.clear();
        }

        for (Map.Entry<String, Map<String, List<ScheduledFlight>>> entry : routeJourneys.entrySet()) {
            String routeDesignation = entry.getKey();
            JourneyPattern journeyPattern = null;

            if (routeDesignationPatternMap.containsKey(routeDesignation)) {
                journeyPattern = routeDesignationPatternMap.get(routeDesignation);
            }

            Map<String, List<ScheduledFlight>> flightsById = entry.getValue();

            for (Map.Entry<String, List<ScheduledFlight>> subEntry : flightsById.entrySet()) {
                String flightId = subEntry.getKey();
                List<ScheduledFlight> flights = subEntry.getValue();

                Map<String, List<ScheduledFlight>> flightsByStopTimes = flights.stream()
                        .collect(Collectors.groupingBy(ScheduledFlight::getStopTimesPattern));

                List<ServiceJourney> serviceJourneys = createServiceJourneys(line, flightId, journeyPattern, flightsByStopTimes);
                serviceJourneyList.addAll(serviceJourneys);
            }
        }

        return serviceJourneyList;
    }

    private List<ServiceJourney> createServiceJourneys(Line line, String flightId, JourneyPattern journeyPattern, Map<String, List<ScheduledFlight>> flightsByStopTimes) {
        List<ServiceJourney> serviceJourneys = new ArrayList<>();
        String journeyPatternId = journeyPattern.getId();

        List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence = journeyPattern.getPointsInSequence()
                .getPointInJourneyPatternOrStopPointInJourneyPatternOrTimingPointInJourneyPattern();

        for (Map.Entry<String, List<ScheduledFlight>> entry : flightsByStopTimes.entrySet()) {
            List<ScheduledFlight> journeyFlights = entry.getValue();
            TimetabledPassingTimes_RelStructure passingTimesRelStruct = aggregateJourneyPassingTimes(journeyFlights, pointsInLinkSequence);
            DayTypeRefs_RelStructure dayTypeRefsStruct = collectDayTypesAndAssignments(journeyFlights);
            ServiceJourney serviceJourney = createServiceJourney(line.getId(), flightId, journeyPatternId, passingTimesRelStruct);
            serviceJourneys.add(serviceJourney);
        }

        return serviceJourneys;
    }

    private TimetabledPassingTimes_RelStructure aggregateJourneyPassingTimes(List<ScheduledFlight> journeyFlights, List<PointInLinkSequence_VersionedChildStructure> pointsInLinkSequence) {
        ScheduledFlight guidingFlight = journeyFlights.get(0);
        TimetabledPassingTimes_RelStructure passingTimesRelStructure = objectFactory.createTimetabledPassingTimes_RelStructure();

        if (guidingFlight.hasStopovers()) {
            List<ScheduledStopover> scheduledStopovers = guidingFlight.getScheduledStopovers();
            Iterator<ScheduledStopover> stopoverIterator = scheduledStopovers.iterator();
            Iterator<PointInLinkSequence_VersionedChildStructure> pointInLinkSequenceIterator = pointsInLinkSequence.iterator();

            while (stopoverIterator.hasNext() && pointInLinkSequenceIterator.hasNext()) {
                ScheduledStopover scheduledStopover = stopoverIterator.next();
                PointInLinkSequence_VersionedChildStructure pointInLinkSequence = pointInLinkSequenceIterator.next();
                TimetabledPassingTime passingTime = netexObjectFactory.createTimetabledPassingTime(pointInLinkSequence.getId());

                if (scheduledStopover.getArrivalTime() != null) {
                    passingTime.setArrivalTime(scheduledStopover.getArrivalTime());
                }
                if (scheduledStopover.getDepartureTime() != null) {
                    passingTime.setDepartureTime(scheduledStopover.getDepartureTime());
                }

                passingTimesRelStructure.withTimetabledPassingTime(passingTime);
            }
        } else {
            TimetabledPassingTime departurePassingTime = netexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(0).getId());
            departurePassingTime.setDepartureTime(guidingFlight.getTimeOfDeparture());
            passingTimesRelStructure.withTimetabledPassingTime(departurePassingTime);

            TimetabledPassingTime arrivalPassingTime = netexObjectFactory.createTimetabledPassingTime(pointsInLinkSequence.get(1).getId());
            arrivalPassingTime.setArrivalTime(guidingFlight.getTimeOfArrival());
            passingTimesRelStructure.withTimetabledPassingTime(arrivalPassingTime);
        }

        return passingTimesRelStructure;
    }

    private DayTypeRefs_RelStructure collectDayTypesAndAssignments(List<ScheduledFlight> journeyFlights) {
        DayTypeRefs_RelStructure dayTypeStructure = objectFactory.createDayTypeRefs_RelStructure();
        journeyFlights.sort(Comparator.comparing(ScheduledFlight::getDateOfOperation));

        for (int i = 0; i < journeyFlights.size(); i++) {
            LocalDate dateOfOperation = journeyFlights.get(i).getDateOfOperation();

            // TODO generate the id based on the date of operation (See Hogia Calendars)
            String dayTypeIdSuffix = dateOfOperation.format(DateTimeFormatter.ofPattern("EEE_dd"));
            String dayTypeId = NetexObjectIdCreator.createDayTypeId(AVINOR_XMLNS, dayTypeIdSuffix);

            DayType dayType = netexObjectFactory.createDayType(dayTypeId);
            DayTypeRefStructure dayTypeRefStruct = netexObjectFactory.createDayTypeRefStructure(dayTypeId);
            JAXBElement<DayTypeRefStructure> dayTypeRefStructElement = objectFactory.createDayTypeRef(dayTypeRefStruct);
            dayTypeStructure.getDayTypeRef().add(dayTypeRefStructElement);

            // TODO generate the id based on the date of operation (See Hogia Calendars)
            String assignmentIdSuffix = dateOfOperation.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String dayTypeAssignmentId = NetexObjectIdCreator.createDayTypeAssignmentId(AVINOR_XMLNS, assignmentIdSuffix);

            DayTypeAssignment dayTypeAssignment = objectFactory.createDayTypeAssignment()
                    .withVersion(VERSION_ONE)
                    .withId(dayTypeAssignmentId)
                    .withOrder(BigInteger.valueOf(i))
                    .withDate(dateOfOperation)
                    .withDayTypeRef(dayTypeRefStructElement);

            dayTypes.put(dayTypeId, dayType);
            dayTypeAssignments.put(dayTypeAssignmentId, dayTypeAssignment);
        }

        return dayTypeStructure;
    }

    private ServiceJourney createServiceJourney(String lineId, String flightId, DayTypeRefs_RelStructure dayTypeRefsStruct,
                                                String journeyPatternId, TimetabledPassingTimes_RelStructure passingTimesRelStruct) {

        String serviceJourneyId = NetexObjectIdCreator.createServiceJourneyId(AVINOR_XMLNS,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        TimetabledPassingTime departurePassingTime = passingTimesRelStruct.getTimetabledPassingTime().get(0);
        OffsetTime departureTime = departurePassingTime.getDepartureTime();

        JourneyPatternRefStructure journeyPatternRefStruct = objectFactory.createJourneyPatternRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(journeyPatternId);
        JAXBElement<JourneyPatternRefStructure> journeyPatternRefStructElement = objectFactory.createJourneyPatternRef(journeyPatternRefStruct);

        LineRefStructure lineRefStruct = netexObjectFactory.createLineRefStructure(lineId);
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

    private void cleanStopPointsFromTempValues(List<ScheduledStopPoint> scheduledStopPoints) {
        scheduledStopPoints.forEach(stopPoint -> stopPoint.setShortName(null));
    }

    private boolean isCommonDesignator(String airlineIata) {
        if (EnumUtils.isValidEnum(AirlineDesignator.class, airlineIata.toUpperCase())) {
            AirlineDesignator designator = AirlineDesignator.valueOf(airlineIata.toUpperCase());
            return AirlineDesignator.commonDesignators.contains(designator);
        }
        return false;
    }

    private class DayTypeReferential {
        private Map<String, DayType> dayTypes = new HashMap<>();
        private Map<String, DayTypeAssignment> dayTypeAssignments = new HashMap<>();

        public Map<String, DayType> getDayTypes() {
            return dayTypes;
        }

        public Map<String, DayTypeAssignment> getDayTypeAssignments() {
            return dayTypeAssignments;
        }

        public void clear() {
            dayTypeAssignments.clear();
            dayTypes.clear();
        }
    }
*/

}
