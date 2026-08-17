package no.rutebanken.extime.netex;

import jakarta.xml.bind.JAXBElement;
import no.rutebanken.extime.util.ExtimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rutebanken.netex.model.ObjectFactory;
import org.rutebanken.netex.model.PublicationDeliveryStructure;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The marshalled XML is the product, so the settings Camel's {@code JaxbDataFormat} applied have to
 * carry over: UTF-8 and pretty printing. A silent change here reaches the national journey planner.
 */
class NetexMarshallerTest {

    private final NetexMarshaller netexMarshaller = new NetexMarshaller();

    @Test
    void writesPrettyPrintedUtf8(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("delivery.xml");

        netexMarshaller.marshalToFile(publicationDelivery("Ålesund"), file);

        String xml = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        assertThat(xml).contains("Ålesund");
        assertThat(xml.lines()).as("pretty printed, not one long line").hasSizeGreaterThan(3);
    }

    @Test
    void failsWhenTheFileCannotBeWritten(@TempDir Path directory) {
        Path file = directory.resolve("no-such-directory").resolve("delivery.xml");

        assertThatThrownBy(() -> netexMarshaller.marshalToFile(publicationDelivery("Bergen"), file))
                .isInstanceOf(ExtimeException.class)
                .hasMessageContaining(file.toString());
    }

    private static JAXBElement<PublicationDeliveryStructure> publicationDelivery(String participant) {
        ObjectFactory objectFactory = new ObjectFactory();
        return objectFactory.createPublicationDelivery(new PublicationDeliveryStructure()
                .withVersion("1.15:NO-NeTEx-networktimetable:1.5")
                .withPublicationTimestamp(LocalDateTime.of(2026, 8, 17, 3, 58))
                .withParticipantRef(participant));
    }
}
