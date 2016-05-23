package no.rutebanken.extime.routes.avinor;

import no.rutebanken.netex.model.*;

import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import java.math.BigInteger;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public class NetexFormatFactory {

    public PublicationDeliveryStructure createPublicationDeliveryStructure() {
        return new ObjectFactory().createPublicationDeliveryStructure()
                .withVersion("1.0")
                .withPublicationTimestamp(ZonedDateTime.now())
                .withParticipantRef("PROVIDER_ID")
                .withPublicationRefreshInterval(createDuration("P15M"))
                .withDescription(createMultilingualString("Simple timetable section for air traffic"));
    }

    public PublicationDeliveryStructure.DataObjects createDataObjects() {
        return new ObjectFactory().createPublicationDeliveryStructureDataObjects()
                .withCompositeFrameOrCommonFrame(createCompositeFrameElement());
    }

    public JAXBElement createCompositeFrameElement() {
        return new ObjectFactory().createCompositeFrame(createCompositeFrame());
    }

    public CompositeFrame createCompositeFrame() {
        return new ObjectFactory().createCompositeFrame()
                .withVersion("1")
                .withId("norwegian:cf:cf01")
                .withFrames(createFramesRelStructure());
    }

    public Frames_RelStructure createFramesRelStructure() {
        return new ObjectFactory().createFrames_RelStructure()
                .withCommonFrame(createTimetableFrameElement("", ""));
    }

    public PublicationRequestStructure createPublicationRequestStructure() {
        return new ObjectFactory().createPublicationRequestStructure()
                .withRequestTimestamp(ZonedDateTime.now())
                .withParticipantRef("REQUESTOR_ID")
                .withDescription(createMultilingualString("Request object for time table example"))
                .withTopics(createTopics());
    }

    public NetworkFrameTopicStructure createNetworkFrameTopicStructure() {
        return new ObjectFactory().createNetworkFrameTopicStructure()
                .withSelectionValidityConditions(createSelectionValidityConditions());
    }

    public NetworkFrameTopicStructure.SelectionValidityConditions createSelectionValidityConditions() {
        return new ObjectFactory().createNetworkFrameTopicStructureSelectionValidityConditions()
                .withValidityCondition_(createAvailabilityConditionElement("", ZonedDateTime.now(), ZonedDateTime.now()));
    }

    public JAXBElement createAvailabilityConditionElement(String description, ZonedDateTime fromDate, ZonedDateTime toDate) {
        return new ObjectFactory().createAvailabilityCondition(
                createAvailabilityCondition(description, fromDate, toDate));
    }

    public AvailabilityCondition createAvailabilityCondition(String description, ZonedDateTime fromDate, ZonedDateTime toDate) {
        return new ObjectFactory().createAvailabilityCondition()
                .withDescription(createMultilingualString(description))
                .withFromDate(fromDate)
                .withToDate(toDate);
    }

    public PublicationRequestStructure.Topics createTopics() {
        return new ObjectFactory().createPublicationRequestStructureTopics()
                .withNetworkFrameTopic(createNetworkFrameTopicStructure());
    }

    public JAXBElement createTimetableFrameElement(String id, String name) {
        return new ObjectFactory().createTimetableFrame(
                createTimetableFrame("hde:TimetableFrame:TIM_23_O", "Winter timetable for route 23 outbound"));
    }

    public TimetableFrame createTimetableFrame(String id, String name) {
        return new ObjectFactory().createTimetableFrame()
                .withVersion("1")
                .withId(id)
                .withValidityConditions(createValidityConditionsRelStructure())
                .withName(createMultilingualString(name))
                .withVehicleModes(VehicleModeEnumeration.AIR)
                .withVehicleJourneys(createJourneysInFrameRelStructure());
    }

    public JourneysInFrame_RelStructure createJourneysInFrameRelStructure() {
        return new ObjectFactory().createJourneysInFrame_RelStructure()
                .withDatedServiceJourneyOrDeadRunOrServiceJourney(
                        createServiceJourney("hde:ServiceJourney:sj_24o_01", LocalTime.now(), "", ""));
    }

    public ValidityConditions_RelStructure createValidityConditionsRelStructure() {
        return new ObjectFactory().createValidityConditions_RelStructure()
                .withValidityConditionRefOrValidBetweenOrValidityCondition_(
                        createAvailabilityCondition("3 ahead", ZonedDateTime.now(), ZonedDateTime.now()));
    }

    public ServiceJourney createServiceJourney(String id, LocalTime departureTime, String servicePatternRef, String lineRef) {
        return new ObjectFactory().createServiceJourney()
                .withVersion("any")
                .withId(id)
                .withDepartureTime(departureTime)
                .withRunTimes(createVehicleJourneyRunTimesRelStructure())
                .withCalls(createCallsRelStructure());
    }

    public VehicleJourneyRunTimes_RelStructure createVehicleJourneyRunTimesRelStructure() {
        return new ObjectFactory().createVehicleJourneyRunTimes_RelStructure()
                .withVehicleJourneyRunTime(createVehicleJourneyRunTime("", "", ""));
    }

    public VehicleJourneyRunTime createVehicleJourneyRunTime(String id, String name, String duration) {
        return new ObjectFactory().createVehicleJourneyRunTime()
                .withId(id)
                .withName(createMultilingualString(name))
                .withRunTime(createDuration(duration));
    }

    public Calls_RelStructure createCallsRelStructure() {
        return new ObjectFactory().createCalls_RelStructure()
                .withCallOrDatedCall(createDepartureCall("", 1L, LocalTime.now()))
                .withCallOrDatedCall(createArrivalCall("", 2L, LocalTime.now()));
    }

    public Call createDepartureCall(String id, Long order, LocalTime departureTime) {
        return new ObjectFactory().createCall()
                .withId(id)
                .withOrder(BigInteger.valueOf(order))
                //.withScheduledStopPointRef();
                .withDeparture(createDepartureStructure(departureTime));
    }

    public Call createArrivalCall(String id, Long order, LocalTime arrivalTime) {
        return new ObjectFactory().createCall()
                .withId(id)
                .withOrder(BigInteger.valueOf(order))
                //.withScheduledStopPointRef();
                .withArrival(createArrivalStructure(arrivalTime));
    }

    public DepartureStructure createDepartureStructure(LocalTime departureTime) {
        return new ObjectFactory().createDepartureStructure()
                .withTime(departureTime);
    }

    public ArrivalStructure createArrivalStructure(LocalTime arrivalTime) {
        return new ObjectFactory().createArrivalStructure()
                .withTime(arrivalTime);
    }

    public MultilingualString createMultilingualString(String value) {
        return new ObjectFactory().createMultilingualString()
                .withValue(value);
    }

    public static Duration createDuration(String lexicalRepresentation) {
        try {
            return DatatypeFactory.newInstance().newDuration(lexicalRepresentation);
        } catch (DatatypeConfigurationException ex) {
            // do some logging here...
        }
        return null;
    }

}
