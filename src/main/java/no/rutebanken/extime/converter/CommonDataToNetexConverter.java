package no.rutebanken.extime.converter;

import autovalue.shaded.com.google.common.common.collect.Lists;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.model.AirlineDesignator;
import no.rutebanken.extime.util.NetexObjectFactory;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private NetexStaticDataSet netexStaticDataSet;

    public JAXBElement<PublicationDeliveryStructure> convertToNetex() throws Exception {
        OffsetDateTime publicationTimestamp = OffsetDateTime.ofInstant(Instant.now(), ZoneId.of(DEFAULT_ZONE_ID));

        Codespace avinorCodespace = netexObjectFactory.createCodespace(AVINOR_AUTHORITY_ID, AVINOR_XMLNS_URL);
        Codespace nsrCodespace = netexObjectFactory.createCodespace(NSR_AUTHORITY_ID, NSR_XMLNS_URL);
        List<Codespace> codespaces = Lists.newArrayList(avinorCodespace, nsrCodespace);

        // resource frame

        // TODO consider separating authorities and operators in yaml configuration file for static data
        JAXBElement<Authority> avinorAuthorityElement = netexObjectFactory.createAvinorAuthorityElement();
        JAXBElement<Authority> nsrAuthorityElement = netexObjectFactory.createNsrAuthorityElement();
        List<JAXBElement<Authority>> authorityElements = Arrays.asList(avinorAuthorityElement, nsrAuthorityElement);

        List<JAXBElement<Operator>> operatorElements = new ArrayList<>(AirlineDesignator.values().length);

        for (AirlineDesignator designator : AirlineDesignator.values()) {
            String designatorName = designator.name().toLowerCase();
            JAXBElement<Operator> operatorElement = netexObjectFactory.createAirlineOperatorElement(designatorName);
            operatorElements.add(operatorElement);
        }

        JAXBElement<ResourceFrame> resourceFrameElement = netexObjectFactory.createResourceFrameElement(authorityElements, operatorElements);

        // service frame

        Frames_RelStructure framesStruct = objectFactory.createFrames_RelStructure();
        framesStruct.getCommonFrame().add(resourceFrameElement);

        JAXBElement<CompositeFrame> compositeFrameElement = netexObjectFactory
                .createCompositeFrameElement(publicationTimestamp, codespaces, framesStruct);

        return netexObjectFactory.createPublicationDeliveryStructureElement(publicationTimestamp, compositeFrameElement, "Description");
    }

}
