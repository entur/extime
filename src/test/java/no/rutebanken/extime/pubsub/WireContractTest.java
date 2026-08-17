package no.rutebanken.extime.pubsub;

import no.rutebanken.extime.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the part of extime's wire contract that lives in this repository's source: the PubSub attribute
 * names marduk reads, and the producer name it records. Changing any of them is a coordinated release
 * with marduk, not a refactor.
 *
 * <p>The expected values are written out as literals rather than read through the constants, which is
 * the whole point of this class: an assertion that takes the key from the same constant it means to pin
 * holds whatever the constant says.
 *
 * <p>The rest of the contract is deployment configuration, not source, and this test cannot reach it:
 * the topic name ({@code queue.upload.destination.name}), the blob path prefix
 * ({@code blobstore.blob.path}) and the provider id all come from the ConfigMap. The assertions on those
 * elsewhere in the suite read the test properties, so they pin the code's use of the values, not the
 * values themselves.
 */
class WireContractTest {

    /**
     * Attribute names on MardukInboundQueue. Marduk's own constants must match these exactly; see
     * {@code no.rutebanken.marduk.Constants}.
     */
    @Test
    void pubSubAttributeNames() {
        assertAll(
                () -> assertEquals("RutebankenCorrelationId", Constants.HEADER_MESSAGE_CORRELATION_ID),
                () -> assertEquals("RutebankenFileHandle", Constants.HEADER_MESSAGE_FILE_HANDLE),
                () -> assertEquals("RutebankenProviderId", Constants.HEADER_MESSAGE_PROVIDER_ID),
                () -> assertEquals("RutebankenFileName", Constants.HEADER_MESSAGE_FILE_NAME),
                () -> assertEquals("RutebankenUsername", Constants.HEADER_MESSAGE_USERNAME),
                () -> assertEquals("CamelFileName", Constants.HEADER_LEGACY_CAMEL_FILE_NAME));
    }

    /**
     * Marduk records the producer of an import under this name.
     */
    @Test
    void producerName() {
        assertEquals("Extime", Constants.EXTIME_USERNAME);
    }
}
