package no.rutebanken.extime.converter;

import no.rutebanken.extime.util.NetexObjectIdTypes;
import org.rutebanken.netex.model.Common_VersionFrameStructure;
import org.rutebanken.netex.model.CompositeFrame;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ServiceFrame;

import javax.xml.bind.JAXBElement;
import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.extime.Constants.VERSION_ONE;
import static org.assertj.core.api.Assertions.assertThat;

class NetexTestUtils {

    static <T> List<T> getFrames(Class<T> clazz, List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames) {
        List<T> foundFrames = new ArrayList<>();

        for (JAXBElement<? extends Common_VersionFrameStructure> frame : dataObjectFrames) {
            if (frame.getValue().getClass().equals(clazz)) {
                foundFrames.add(clazz.cast(frame.getValue()));
            }
        }

        return foundFrames;
    }

    static List<JAXBElement<? extends Common_VersionFrameStructure>> getDataObjectFrames(PublicationDeliveryStructure publicationDelivery) {
        List<JAXBElement<? extends Common_VersionFrameStructure>> dataObjectFrames = publicationDelivery.getDataObjects().getCompositeFrameOrCommonFrame();
        CompositeFrame compositeFrame = NetexTestUtils.getFrames(CompositeFrame.class, dataObjectFrames).get(0);
        return compositeFrame.getFrames().getCommonFrame();
    }

    static void verifyCompositeFrameAttributes(PublicationDeliveryStructure publicationDelivery) {
        CompositeFrame compositeFrame = NetexTestUtils.getFrames(CompositeFrame.class, publicationDelivery.getDataObjects().getCompositeFrameOrCommonFrame()).get(0);
        assertThat(compositeFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(compositeFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.COMPOSITE_FRAME_KEY), "CompositeFrame");
    }

    static void verifyServiceFrameAttributes(PublicationDeliveryStructure publicationDelivery) {
        ServiceFrame serviceFrame = NetexTestUtils.getFrames(ServiceFrame.class, getDataObjectFrames(publicationDelivery)).get(0);
        assertThat(serviceFrame).hasFieldOrPropertyWithValue("version", VERSION_ONE);
        assertThat(serviceFrame.getId()).matches(id -> id.split(":")[1].equals(NetexObjectIdTypes.SERVICE_FRAME_KEY), "ServiceFrame");
    }
}
