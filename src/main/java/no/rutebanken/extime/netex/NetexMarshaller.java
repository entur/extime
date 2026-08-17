package no.rutebanken.extime.netex;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import no.rutebanken.extime.util.ExtimeException;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a NeTEx publication delivery to a file.
 *
 * <p>Replaces the {@code netexJaxbDataFormat} plus {@code to("file:...")} pair. The marshaller settings
 * are the ones Camel's {@code JaxbDataFormat} applied for {@code prettyPrint} and {@code encoding}:
 * a context built from the model's package name, {@code JAXB_FORMATTED_OUTPUT} and {@code JAXB_ENCODING},
 * marshalling straight to the output stream.
 */
@Component
public class NetexMarshaller {

    private static final String NETEX_CHARSET_NAME = StandardCharsets.UTF_8.name();

    private final JAXBContext netexContext;

    public NetexMarshaller() {
        try {
            netexContext = JAXBContext.newInstance(PublicationDeliveryStructure.class.getPackage().getName());
        } catch (JAXBException e) {
            throw new ExtimeException("Could not create the NeTEx JAXB context", e);
        }
    }

    public void marshalToFile(JAXBElement<PublicationDeliveryStructure> publicationDelivery, Path file) {
        try (OutputStream out = Files.newOutputStream(file)) {
            Marshaller marshaller = netexContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, NETEX_CHARSET_NAME);
            marshaller.marshal(publicationDelivery, out);
        } catch (JAXBException | IOException e) {
            throw new ExtimeException("Could not write NeTEx to " + file, e);
        }
    }
}
