package no.rutebanken.extime.util;

import com.google.common.collect.Lists;
import no.rutebanken.extime.config.NetexStaticDataSet;
import org.rutebanken.netex.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBElement;
import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static no.rutebanken.extime.Constants.*;

// TODO autowire the object factory and make all methods none-static, also change tests, and remove method-local objectfactory-refs
@Component(value = "netexObjectFactory")
public class NetexObjectFactory {

    private static ObjectFactory objectFactory = new ObjectFactory();

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

    public JAXBElement<PublicationDeliveryStructure> createPublicationDeliveryStructureElement(
            OffsetDateTime publicationTimestamp, JAXBElement<CompositeFrame> compositeFrame, String description) {

        ObjectFactory objectFactory = new ObjectFactory(); // TODO remove and use injected factory instead

        NetexStaticDataSet.OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations().get(AVINOR_AUTHORITY_ID.toLowerCase());

        PublicationDeliveryStructure.DataObjects dataObjects = objectFactory.createPublicationDeliveryStructureDataObjects();
        dataObjects.getCompositeFrameOrCommonFrame().add(compositeFrame);

        PublicationDeliveryStructure publicationDeliveryStructure = objectFactory.createPublicationDeliveryStructure()
                .withVersion(NETEX_PROFILE_VERSION)
                .withPublicationTimestamp(publicationTimestamp)
                .withParticipantRef(avinorDataSet.getName())
                //.withDescription(createMultilingualString(description))
                .withDataObjects(dataObjects);

        return objectFactory.createPublicationDelivery(publicationDeliveryStructure);
    }

    public JAXBElement<CompositeFrame> createCompositeFrameElement(OffsetDateTime publicationTimestamp,
            List<Codespace> codespaces, Frames_RelStructure frames) {

        ObjectFactory objectFactory = new ObjectFactory(); // TODO remove and use injected factory instead

        Codespaces_RelStructure codespacesStruct = objectFactory.createCodespaces_RelStructure()
                .withCodespaceRefOrCodespace(codespaces);

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

        ObjectFactory objectFactory = new ObjectFactory(); // TODO remove and use injected factory instead

        OrganisationsInFrame_RelStructure organisationsInFrame = objectFactory.createOrganisationsInFrame_RelStructure();
        organisationsInFrame.getOrganisation_().addAll(authorityElements);
        organisationsInFrame.getOrganisation_().addAll(operatorElements);

        String resourceFrameId = NetexObjectIdCreator.createResourceFrameId(AVINOR_AUTHORITY_ID,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ResourceFrame resourceFrame = objectFactory.createResourceFrame()
                .withVersion(VERSION_ONE)
                .withId(resourceFrameId)
                .withOrganisations(organisationsInFrame);

        return objectFactory.createResourceFrame(resourceFrame);
    }

    public JAXBElement<SiteFrame> createSiteFrameElement(List<StopPlace> stopPlaces) {

        ObjectFactory objectFactory = new ObjectFactory(); // TODO remove and use injected factory instead

        StopPlacesInFrame_RelStructure stopPlacesInFrameRelStructure = objectFactory.createStopPlacesInFrame_RelStructure()
                .withStopPlace(stopPlaces);

        String siteFrameId = NetexObjectIdCreator.createSiteFrameId(AVINOR_AUTHORITY_ID,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        SiteFrame siteFrame = objectFactory.createSiteFrame()
                .withVersion(VERSION_ONE)
                .withId(siteFrameId)
                .withStopPlaces(stopPlacesInFrameRelStructure);

        return objectFactory.createSiteFrame(siteFrame);
    }

    public JAXBElement<ServiceFrame> createCommonServiceFrameElement(List<ScheduledStopPoint> scheduledStopPoints,
            List<JAXBElement<PassengerStopAssignment>> stopAssignmentElements) {

        ObjectFactory objectFactory = new ObjectFactory(); // TODO remove and use injected factory instead

        ScheduledStopPointsInFrame_RelStructure scheduledStopPointsInFrame = objectFactory.createScheduledStopPointsInFrame_RelStructure()
                .withScheduledStopPoint(scheduledStopPoints);

        StopAssignmentsInFrame_RelStructure stopAssignmentsInFrame = objectFactory.createStopAssignmentsInFrame_RelStructure();
        stopAssignmentElements.forEach(stopAssignmentElement -> stopAssignmentsInFrame.getStopAssignment().add(stopAssignmentElement));

        String serviceFrameId = NetexObjectIdCreator.createServiceFrameId(AVINOR_AUTHORITY_ID,
                String.valueOf(NetexObjectIdCreator.generateRandomId(DEFAULT_START_INCLUSIVE, DEFAULT_END_EXCLUSIVE)));

        ServiceFrame serviceFrame = objectFactory.createServiceFrame()
                .withVersion(VERSION_ONE)
                .withId(serviceFrameId)
                .withScheduledStopPoints(scheduledStopPointsInFrame)
                .withStopAssignments(stopAssignmentsInFrame);

        return objectFactory.createServiceFrame(serviceFrame);
    }

    public Network createNetwork(OffsetDateTime publicationTimestamp, String airlineIata, String airlineName) {
        ObjectFactory objectFactory = new ObjectFactory(); // TODO remove and use injected factory instead

        String networkId = NetexObjectIdCreator.createNetworkId(AVINOR_AUTHORITY_ID, airlineIata);

        return objectFactory.createNetwork()
                .withVersion(VERSION_ONE)
                .withChanged(publicationTimestamp)
                .withId(networkId)
                .withName(createMultilingualString(airlineName));
    }

    public JAXBElement<Authority> createNsrAuthorityElement() {
        NetexStaticDataSet.OrganisationDataSet nsrDataSet = netexStaticDataSet.getOrganisations()
                .get(NSR_AUTHORITY_ID.toLowerCase());

        List<JAXBElement<?>> authorityRest = createOrganisationRest(
                nsrDataSet.getCompanyNumber(),
                nsrDataSet.getName(),
                nsrDataSet.getLegalName(),
                nsrDataSet.getPhone(),
                nsrDataSet.getUrl(),
                OrganisationTypeEnumeration.AUTHORITY
        );

        String authorityId = NetexObjectIdCreator.createAuthorityId(NSR_AUTHORITY_ID, nsrDataSet.getName());

        return createAuthorityElement(authorityId, authorityRest);
    }

    public JAXBElement<Authority> createAvinorAuthorityElement() {
        NetexStaticDataSet.OrganisationDataSet avinorDataSet = netexStaticDataSet.getOrganisations()
                .get(AVINOR_AUTHORITY_ID.toLowerCase());

        List<JAXBElement<?>> authorityRest = createOrganisationRest(
                avinorDataSet.getCompanyNumber(),
                avinorDataSet.getName(),
                avinorDataSet.getLegalName(),
                avinorDataSet.getPhone(),
                avinorDataSet.getUrl(),
                OrganisationTypeEnumeration.AUTHORITY
        );

        String authorityId = NetexObjectIdCreator.createAuthorityId(AVINOR_AUTHORITY_ID, avinorDataSet.getName());

        return createAuthorityElement(authorityId, authorityRest);
    }

    public JAXBElement<Authority> createAuthorityElement(String authorityId, List<JAXBElement<?>> authorityRest) {
        ObjectFactory objectFactory = new ObjectFactory(); // TODO remove and use injected factory instead

        Authority authority = objectFactory.createAuthority()
                .withVersion(VERSION_ONE)
                .withId(authorityId)
                .withRest(authorityRest);

        return objectFactory.createAuthority(authority);
    }

    public JAXBElement<Operator> createAirlineOperatorElement(String airlineIata) {
        NetexStaticDataSet.OrganisationDataSet airlineDataSet = netexStaticDataSet.getOrganisations()
                .get(airlineIata.toLowerCase());

        List<JAXBElement<?>> operatorRest = createOrganisationRest(
                airlineDataSet.getCompanyNumber(),
                airlineDataSet.getName(),
                airlineDataSet.getLegalName(),
                airlineDataSet.getPhone(),
                airlineDataSet.getUrl(),
                OrganisationTypeEnumeration.OPERATOR
        );

        String operatorId = NetexObjectIdCreator.createOperatorId(AVINOR_AUTHORITY_ID, airlineIata);
        return createOperatorElement(operatorId, operatorRest);
    }

    public JAXBElement<Operator> createOperatorElement(String operatorId, List<JAXBElement<?>> operatorRest) {
        ObjectFactory objectFactory = new ObjectFactory(); // TODO remove and use injected factory instead

        Operator operator = objectFactory.createOperator()
                .withVersion(VERSION_ONE)
                .withId(operatorId)
                .withRest(operatorRest);

        return objectFactory.createOperator(operator);
    }


    public List<JAXBElement<?>> createOrganisationRest(String companyNumber, String name, String legalName,
            String phone, String url, OrganisationTypeEnumeration organisationType) {

        ObjectFactory objectFactory = new ObjectFactory();

        JAXBElement<String> companyNumberStructure = objectFactory
                .createOrganisation_VersionStructureCompanyNumber(companyNumber);

        JAXBElement<MultilingualString> nameStructure = objectFactory
                .createOrganisation_VersionStructureName(createMultilingualString(name));

        JAXBElement<MultilingualString> legalNameStructure = objectFactory
                .createOrganisation_VersionStructureLegalName(createMultilingualString(legalName));

        JAXBElement<ContactStructure> contactStructure = createContactStructure(phone, url);

        JAXBElement<List<OrganisationTypeEnumeration>> organisationTypes = objectFactory
                .createOrganisation_VersionStructureOrganisationType(Collections.singletonList(organisationType));

        return Lists.newArrayList(companyNumberStructure, nameStructure,
                legalNameStructure, contactStructure, organisationTypes);
    }

    public JAXBElement<ContactStructure> createContactStructure(String phone, String url) {
        ObjectFactory objectFactory = new ObjectFactory(); // TODO remove and use injected factory instead

        ContactStructure contactStructure = objectFactory.createContactStructure()
                .withPhone(phone)
                .withUrl(url);

        return objectFactory.createOrganisation_VersionStructureContactDetails(contactStructure);
    }

    public Codespace createCodespace(String id, String xmlnsUrl) {
        ObjectFactory objectFactory = new ObjectFactory(); // TODO remove and use injected factory instead

        return objectFactory.createCodespace()
                .withId(id.toLowerCase())
                .withXmlns(id)
                .withXmlnsUrl(xmlnsUrl);
    }

    // TODO consider the id generation to be moved to converter level instead, for more precise control, use a uniform way of doing it
    public static PointOnRoute createPointOnRoute(String objectId, String stopPointId) {
        String pointOnRouteId = NetexObjectIdCreator.createPointOnRouteId(AVINOR_AUTHORITY_ID, objectId);
        RoutePointRefStructure routePointRefStruct = createRoutePointRefStructure(stopPointId);
        JAXBElement<RoutePointRefStructure> routePointRefStructElement = objectFactory.createRoutePointRef(routePointRefStruct);

        return objectFactory.createPointOnRoute()
                .withVersion(VERSION_ONE)
                .withId(pointOnRouteId)
                .withPointRef(routePointRefStructElement);
    }

    public static StopPointInJourneyPattern createStopPointInJourneyPattern(String objectId, BigInteger orderIndex, String stopPointId) {
        String stopPointInJourneyPatternId = NetexObjectIdCreator.createStopPointInJourneyPatternId(AVINOR_AUTHORITY_ID, objectId);
        ScheduledStopPointRefStructure stopPointRefStruct = createScheduledStopPointRefStructure(stopPointId);
        JAXBElement<ScheduledStopPointRefStructure> stopPointRefStructElement = objectFactory.createScheduledStopPointRef(stopPointRefStruct);

        return objectFactory.createStopPointInJourneyPattern()
                .withVersion(VERSION_ONE)
                .withId(stopPointInJourneyPatternId)
                .withOrder(orderIndex)
                .withScheduledStopPointRef(stopPointRefStructElement);
    }

    // TODO find out how to best handle incoming departure and arrival times, disabled for now, caller responsible to set
    public static TimetabledPassingTime createTimetabledPassingTime(String stopPointInJourneyPatternId) {
        StopPointInJourneyPatternRefStructure stopPointInJourneyPatternRefStruct =
                createStopPointInJourneyPatternRefStructure(stopPointInJourneyPatternId);

        JAXBElement<StopPointInJourneyPatternRefStructure> stopPointInJourneyPatternRefStructElement = objectFactory
                .createStopPointInJourneyPatternRef(stopPointInJourneyPatternRefStruct);

        return objectFactory.createTimetabledPassingTime()
                .withPointInJourneyPatternRef(stopPointInJourneyPatternRefStructElement);
    }

    public static MultilingualString createMultilingualString(String value) {
        return objectFactory.createMultilingualString().withValue(value);
    }

    // reference structures creation

    public static OperatorRefStructure createOperatorRefStructure(String operatorId) {
        return objectFactory.createOperatorRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(operatorId);
    }

    public static RouteRefStructure createRouteRefStructure(String routeId) {
        return objectFactory.createRouteRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(routeId);
    }

    public static StopPlaceRefStructure createStopPlaceRefStructure(String stopPlaceId) {
        return objectFactory.createStopPlaceRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(stopPlaceId);
    }

    public static ScheduledStopPointRefStructure createScheduledStopPointRefStructure(String stopPointId) {
        return objectFactory.createScheduledStopPointRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(stopPointId);
    }

    public static StopPointInJourneyPatternRefStructure createStopPointInJourneyPatternRefStructure(String stopPointInJourneyPatternId) {
        return objectFactory.createStopPointInJourneyPatternRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(stopPointInJourneyPatternId);
    }

    public static PointRefStructure createPointRefStructure(String stopPointId) {
        return objectFactory.createPointRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(stopPointId);
    }

    public static RoutePointRefStructure createRoutePointRefStructure(String stopPointId) {
        return objectFactory.createRoutePointRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(stopPointId);
    }

    public static DayTypeRefStructure createDayTypeRefStructure(String dayTypeId) {
        return objectFactory.createDayTypeRefStructure()
                .withVersion(VERSION_ONE)
                .withRef(dayTypeId);
    }

}
