package no.rutebanken.extime.util;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;

public class XmlUtil {
    public String convertToXml(Object source, Class... type) {
        StringWriter writer = new StringWriter();
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(type);
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(source, writer);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
        return writer.toString();
    }
}