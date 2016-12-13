package no.rutebanken.extime.util;

import no.rutebanken.extime.config.NetexStaticDataSet;
import org.rutebanken.netex.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static no.rutebanken.extime.Constants.*;

@Component(value = "netexObjectFactory")
public class NetexObjectFactory {

    @Autowired
    private ObjectFactory objectFactory;

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

    public JAXBElement<PublicationDeliveryStructure> createPublicationDeliveryStructureElement(
            OffsetDateTime publicationTimestamp, JAXBElement<CompositeFrame> compositeFrame, String description) {

        NetexStaticDataSet.OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations().get(AVINOR_AUTHORITY_ID.toLowerCase());

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

    public JAXBElement<CompositeFrame> createCompositeFrameElement(OffsetDateTime publicationTimestamp, Frames_RelStructure frames, Codespace... codespaces) {

        Codespaces_RelStructure codespacesStruct = objectFactory.createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace((Object[]) codespaces);

        LocaleStructure localeStructure = objectFactory.createLocaleStructure()
                .withTimeZone(DEFAULT_ZONE_ID)
                .withDefaultLanguage(DEFAULT_LANGUAGE);

        VersionFrameDefaultsStructure versionFrameDefaultsStructure = objectFactory.createVersionFrameDefaultsStructure()
                .withDefaultLocale(localeStructure);

        String compositeFrameId = NetexObjectIdCreator.createCompositeFrameId(AVINOR_AUTHORITY_ID,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        CompositeFrame compositeFrame = objectFactory.createCompositeFrame()
                .withVersion(VERSION_ONE)
                .withCreated(publicationTimestamp)
                .withId(compositeFrameId)
                .withCodespaces(codespacesStruct)
                .withFrameDefaults(versionFrameDefaultsStructure)
                .withFrames(frames);

        return objectFactory.createCompositeFrame(compositeFrame);
    }

    public JAXBElement<ResourceFrame> createResourceFrameElement(Collection<JAXBElement<Authority>> authorityElements,
            Collection<JAXBElement<Operator>> operatorElements) {

        OrganisationsInFrame_RelStructure organisationsStruct = objectFactory.createOrganisationsInFrame_RelStructure();

        String resourceFrameId = NetexObjectIdCreator.createResourceFrameId(AVINOR_AUTHORITY_ID,
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

    public JAXBElement<SiteFrame> createSiteFrameElement(List<StopPlace> stopPlaces) {
        StopPlacesInFrame_RelStructure stopPlacesStruct = objectFactory.createStopPlacesInFrame_RelStructure();

        String siteFrameId = NetexObjectIdCreator.createSiteFrameId(AVINOR_AUTHORITY_ID,
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

        String serviceFrameId = NetexObjectIdCreator.createServiceFrameId(AVINOR_AUTHORITY_ID,
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

    public Network createNetwork(OffsetDateTime publicationTimestamp, String airlineIata, String airlineName) {
        String networkId = NetexObjectIdCreator.createNetworkId(AVINOR_AUTHORITY_ID, airlineIata);

        return objectFactory.createNetwork()
                .withVersion(VERSION_ONE)
                .withChanged(publicationTimestamp)
                .withId(networkId)
                .withName(createMultilingualString(airlineName));
    }

    public JAXBElement<Authority> createAvinorAuthorityElement() {
        NetexStaticDataSet.OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations()
                .get(AVINOR_AUTHORITY_ID.toLowerCase());

        String authorityId = NetexObjectIdCreator.createAuthorityId(AVINOR_AUTHORITY_ID, avinorDataSet.getName());

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
                .get(NSR_AUTHORITY_ID.toLowerCase());

        String authorityId = NetexObjectIdCreator.createAuthorityId(NSR_AUTHORITY_ID, NSR_AUTHORITY_ID);

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

        String operatorId = NetexObjectIdCreator.createOperatorId(AVINOR_AUTHORITY_ID, airlineIata);

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

    // TODO consider the id generation to be moved to converter level instead, for more precise control, use a uniform way of doing it
    public PointOnRoute createPointOnRoute(String objectId, String stopPointId) {
        String pointOnRouteId = NetexObjectIdCreator.createPointOnRouteId(AVINOR_AUTHORITY_ID, objectId);
        RoutePointRefStructure routePointRefStruct = createRoutePointRefStructure(stopPointId);
        JAXBElement<RoutePointRefStructure> routePointRefStructElement = objectFactory.createRoutePointRef(routePointRefStruct);

        return objectFactory.createPointOnRoute()
                .withVersion(VERSION_ONE)
                .withId(pointOnRouteId)
                .withPointRef(routePointRefStructElement);
    }

    public StopPointInJourneyPattern createStopPointInJourneyPattern(String objectId, BigInteger orderIndex, String stopPointId) {
        String stopPointInJourneyPatternId = NetexObjectIdCreator.createStopPointInJourneyPatternId(AVINOR_AUTHORITY_ID, objectId);
        ScheduledStopPointRefStructure stopPointRefStruct = createScheduledStopPointRefStructure(stopPointId, Boolean.FALSE);
        JAXBElement<ScheduledStopPointRefStructure> stopPointRefStructElement = objectFactory.createScheduledStopPointRef(stopPointRefStruct);

        return objectFactory.createStopPointInJourneyPattern()
                .withVersion(VERSION_ONE)
                .withId(stopPointInJourneyPatternId)
                .withOrder(orderIndex)
                .withScheduledStopPointRef(stopPointRefStructElement);
    }

    // TODO find out how to best handle incoming departure and arrival times, disabled for now, caller responsible to set
    public TimetabledPassingTime createTimetabledPassingTime(String stopPointInJourneyPatternId) {
        StopPointInJourneyPatternRefStructure stopPointInJourneyPatternRefStruct =
                createStopPointInJourneyPatternRefStructure(stopPointInJourneyPatternId);

        JAXBElement<StopPointInJourneyPatternRefStructure> stopPointInJourneyPatternRefStructElement = objectFactory
                .createStopPointInJourneyPatternRef(stopPointInJourneyPatternRefStruct);

        return objectFactory.createTimetabledPassingTime()
                .withPointInJourneyPatternRef(stopPointInJourneyPatternRefStructElement);
    }

    public MultilingualString createMultilingualString(String value) {
        return objectFactory.createMultilingualString().withValue(value);
    }

    // reference structures creation

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

    public StopPlaceRefStructure createStopPlaceRefStructure(String stopPlaceId, boolean withRefValidation) {
        StopPlaceRefStructure stopPlaceRefStruct = objectFactory.createStopPlaceRefStructure()
                .withRef(stopPlaceId);
        return withRefValidation ? stopPlaceRefStruct.withVersion(VERSION_ONE) : stopPlaceRefStruct;
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

}
