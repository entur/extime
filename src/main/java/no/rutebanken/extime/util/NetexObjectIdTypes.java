package no.rutebanken.extime.util;

public abstract class NetexObjectIdTypes {

    private NetexObjectIdTypes() {
        // Class of constants - should not be instantiated
    }

    // frame id keys
    public static final String COMPOSITE_FRAME_KEY = "CompositeFrame";
    public static final String RESOURCE_FRAME_KEY = "ResourceFrame";
    public static final String SERVICE_FRAME_KEY = "ServiceFrame";
    public static final String SERVICE_CALENDAR_FRAME_KEY = "ServiceCalendarFrame";
    public static final String TIMETABLE_FRAME_KEY = "TimetableFrame";

    // entity id keys
    public static final String AUTHORITY_KEY = "Authority";
    public static final String BRANDING_KEY = "Branding";
    public static final String OPERATOR_KEY = "Operator";
    public static final String NETWORK_KEY = "Network";
    public static final String GROUP_OF_LINES_KEY = "GroupOfLines";
    public static final String LINE_KEY = "Line";
    public static final String ROUTE_KEY = "Route";
    public static final String QUAY_KEY = "Quay";
    public static final String STOP_POINT_KEY = "ScheduledStopPoint";
    public static final String POINT_PROJECTION_KEY = "PointProjection";
    public static final String ROUTE_POINT_KEY = "RoutePoint";
    public static final String POINT_ON_ROUTE_KEY = "PointOnRoute";
    public static final String JOURNEY_PATTERN_KEY = "JourneyPattern";
    public static final String STOP_POINT_IN_JOURNEY_PATTERN_KEY = "StopPointInJourneyPattern";
    public static final String DESTINATION_DISPLAY = "DestinationDisplay";
    public static final String DAY_TYPE_KEY = "DayType";
    public static final String DAY_TYPE_ASSIGNMENT_KEY = "DayTypeAssignment";
    public static final String AVAILABILITY_CONDITION_KEY = "AvailabilityCondition";
    public static final String SERVICE_JOURNEY_KEY = "ServiceJourney";
    public static final String PASSENGER_STOP_ASSIGNMENT_KEY = "PassengerStopAssignment";
    public static final String TIMETABLED_PASSING_TIME_KEY = "TimetabledPassingTime";
}
