package no.rutebanken.extime.converter;

import no.avinor.flydata.xjc.model.feed.Flight;
import org.springframework.stereotype.Component;
import uk.org.siri.siri20.*;

import java.time.Duration;
import java.time.ZonedDateTime;

@Component(value = "realTimeFlightToSiriConverter")
public class RealTimeFlightToSiriConverter {

    //public List<Siri> convertToSiri() {
    public Siri convertToSiri() {
        Siri siri = new Siri();
        return siri;
        //return Arrays.asList(siri);
    }

    private Siri convertToSiri(Flight flight) {
        ObjectFactory factory = new ObjectFactory();
        Siri siri = factory.createSiri();
        ServiceDelivery serviceDelivery = factory.createServiceDelivery();

        /*--==HEADER-SECTION==--*/
        serviceDelivery.setResponseTimestamp(ZonedDateTime.now());
        RequestorRef producerRef = factory.createRequestorRef();
        producerRef.setValue("EXTIME");
        serviceDelivery.setProducerRef(producerRef);
        serviceDelivery.setStatus(Boolean.TRUE);
        serviceDelivery.setMoreData(Boolean.FALSE);

        /*--==PAYLOAD-SECTION==--*/
        EstimatedTimetableDeliveryStructure estimatedTimetableDeliveryStructure = factory.createEstimatedTimetableDeliveryStructure();
        estimatedTimetableDeliveryStructure.setVersion("2.0");
        estimatedTimetableDeliveryStructure.setResponseTimestamp(ZonedDateTime.now());

        RequestorRef subscriberRef = factory.createRequestorRef();
        subscriberRef.setValue("EXT-SYSTEM");
        estimatedTimetableDeliveryStructure.setSubscriberRef(subscriberRef);

        SubscriptionQualifierStructure subscriptionQualifierStructure = factory.createSubscriptionQualifierStructure();
        subscriptionQualifierStructure.setValue("0001");
        estimatedTimetableDeliveryStructure.setSubscriptionRef(subscriptionQualifierStructure);

        estimatedTimetableDeliveryStructure.setStatus(Boolean.TRUE);
        estimatedTimetableDeliveryStructure.setValidUntil(ZonedDateTime.now().plusDays(7L));
        estimatedTimetableDeliveryStructure.setShortestPossibleCycle(createDuration("P1Y2M3DT10H30M"));

        EstimatedVersionFrameStructure estimatedVersionFrameStructure = factory.createEstimatedVersionFrameStructure();
        estimatedVersionFrameStructure.setRecordedAtTime(ZonedDateTime.now());
        estimatedVersionFrameStructure.setVersionRef("5645");

        EstimatedVehicleJourney estimatedVehicleJourney = factory.createEstimatedVehicleJourney();

        LineRef lineRef = factory.createLineRef();
        lineRef.setValue("WF149");
        estimatedVehicleJourney.setLineRef(lineRef);

        DirectionRefStructure directionRefStructure = factory.createDirectionRefStructure();
        directionRefStructure.setValue("INBOUND");
        estimatedVehicleJourney.setDirectionRef(directionRefStructure);

        DatedVehicleJourneyRef datedVehicleJourneyRef = factory.createDatedVehicleJourneyRef();
        datedVehicleJourneyRef.setValue("00008");
        estimatedVehicleJourney.setDatedVehicleJourneyRef(datedVehicleJourneyRef);

        estimatedVehicleJourney.setCancellation(Boolean.FALSE);

        NaturalLanguageStringStructure publishedLineName = factory.createNaturalLanguageStringStructure();
        publishedLineName.setValue("WF149");
        estimatedVehicleJourney.getPublishedLineNames().add(publishedLineName);

        OperatorRefStructure operatorRefStructure = factory.createOperatorRefStructure();
        operatorRefStructure.setValue("Widerøe");
        estimatedVehicleJourney.setOperatorRef(operatorRefStructure);

        ProductCategoryRefStructure productCategoryRefStructure = factory.createProductCategoryRefStructure();
        productCategoryRefStructure.setValue("CAT274");
        estimatedVehicleJourney.setProductCategoryRef(productCategoryRefStructure);

        ServiceFeatureRef serviceFeatureRef = factory.createServiceFeatureRef();
        serviceFeatureRef.setValue("CyclesPermitted");
        estimatedVehicleJourney.getServiceFeatureReves().add(serviceFeatureRef);

        VehicleFeatureRefStructure vehicleFeatureRefStructure = factory.createVehicleFeatureRefStructure();
        vehicleFeatureRefStructure.setValue("DisabledAccess");
        estimatedVehicleJourney.getVehicleFeatureReves().add(vehicleFeatureRefStructure);

        NaturalLanguageStringStructure vehicleJourneyName = factory.createNaturalLanguageStringStructure();
        vehicleJourneyName.setValue("WF149");
        estimatedVehicleJourney.getVehicleJourneyNames().add(vehicleJourneyName);

        NaturalLanguageStringStructure journeyNote = factory.createNaturalLanguageStringStructure();
        journeyNote.setValue("Monday-Friday");
        estimatedVehicleJourney.getJourneyNotes().add(journeyNote);

        estimatedVehicleJourney.setMonitored(Boolean.TRUE);
        estimatedVehicleJourney.setPredictionInaccurate(Boolean.FALSE);
        estimatedVehicleJourney.setOccupancy(OccupancyEnumeration.FULL);

        EstimatedVehicleJourney.EstimatedCalls estimatedCalls = factory.createEstimatedVehicleJourneyEstimatedCalls();

        /*--==CALL 1==--*/
        EstimatedCall estimatedCallOne = factory.createEstimatedCall();

        StopPointRef departureStopPointOne = factory.createStopPointRef();
        departureStopPointOne.setValue("00001");
        estimatedCallOne.setStopPointRef(departureStopPointOne);

        estimatedCallOne.setExtraCall(Boolean.FALSE);
        estimatedCallOne.setPredictionInaccurate(Boolean.FALSE);
        estimatedCallOne.setOccupancy(OccupancyEnumeration.SEATS_AVAILABLE);
        estimatedCallOne.setBoardingStretch(Boolean.FALSE);
        estimatedCallOne.setRequestStop(Boolean.FALSE);

        NaturalLanguageStringStructure callNoteOne = factory.createNaturalLanguageStringStructure();
        callNoteOne.setValue("Starts here");
        estimatedCallOne.getCallNotes().add(callNoteOne);

        estimatedCallOne.setAimedDepartureTime(ZonedDateTime.now());
        estimatedCallOne.setArrivalBoardingActivity(ArrivalBoardingActivityEnumeration.NO_ALIGHTING);
        estimatedCallOne.setAimedArrivalTime(ZonedDateTime.now().plusHours(1L));

        estimatedCallOne.setExpectedDepartureTime(ZonedDateTime.now().plusHours(1L));
        estimatedCallOne.setExpectedArrivalTime(ZonedDateTime.now().plusHours(2L));

        NaturalLanguageStringStructure departurePlatform = factory.createNaturalLanguageStringStructure();
        departurePlatform.setValue("1");
        estimatedCallOne.setDeparturePlatformName(departurePlatform);

        /*--==CALL 2==--*/
        EstimatedCall estimatedCallTwo = factory.createEstimatedCall();

        StopPointRef departureStopPointTwo = factory.createStopPointRef();
        departureStopPointTwo.setValue("00002");
        estimatedCallTwo.setStopPointRef(departureStopPointTwo);

        estimatedCallTwo.setExtraCall(Boolean.FALSE);
        estimatedCallTwo.setPredictionInaccurate(Boolean.FALSE);
        estimatedCallTwo.setOccupancy(OccupancyEnumeration.SEATS_AVAILABLE);
        estimatedCallTwo.setBoardingStretch(Boolean.FALSE);
        estimatedCallTwo.setRequestStop(Boolean.FALSE);

        NaturalLanguageStringStructure callNoteTwo = factory.createNaturalLanguageStringStructure();
        callNoteTwo.setValue("Starts here");
        estimatedCallTwo.getCallNotes().add(callNoteTwo);

        estimatedCallTwo.setAimedArrivalTime(ZonedDateTime.now());
        estimatedCallTwo.setArrivalBoardingActivity(ArrivalBoardingActivityEnumeration.NO_ALIGHTING);
        estimatedCallTwo.setAimedDepartureTime(ZonedDateTime.now().plusHours(1L));

        estimatedCallTwo.setExpectedDepartureTime(ZonedDateTime.now().plusHours(1L));
        estimatedCallTwo.setExpectedArrivalTime(ZonedDateTime.now().plusHours(2L));

        NaturalLanguageStringStructure departurePlatformTwo = factory.createNaturalLanguageStringStructure();
        departurePlatformTwo.setValue("2");
        estimatedCallTwo.setDeparturePlatformName(departurePlatformTwo);

        estimatedCalls.getEstimatedCalls().add(estimatedCallOne);
        estimatedCalls.getEstimatedCalls().add(estimatedCallTwo);
        estimatedVehicleJourney.setEstimatedCalls(estimatedCalls);

        estimatedVersionFrameStructure.getEstimatedVehicleJourneies().add(estimatedVehicleJourney);
        estimatedTimetableDeliveryStructure.getEstimatedJourneyVersionFrames().add(estimatedVersionFrameStructure);
        serviceDelivery.getEstimatedTimetableDeliveries().add(estimatedTimetableDeliveryStructure);
        siri.setServiceDelivery(serviceDelivery);

        return siri;
    }

    public Duration createDuration(String lexicalRepresentation) {
            return java.time.Duration.ofMillis(215081000L);
    }

}
