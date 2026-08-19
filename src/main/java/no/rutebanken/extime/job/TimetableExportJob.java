package no.rutebanken.extime.job;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.xml.bind.JAXBElement;
import no.rutebanken.extime.avinor.FlightEventFetcher;
import no.rutebanken.extime.converter.CommonDataToNetexConverter;
import no.rutebanken.extime.converter.LineDataToNetexConverter;
import no.rutebanken.extime.converter.ScheduledFlightConverter;
import no.rutebanken.extime.model.FlightEvent;
import no.rutebanken.extime.model.LineDataSet;
import no.rutebanken.extime.netex.NetexMarshaller;
import no.rutebanken.extime.pubsub.MardukNotifier;
import no.rutebanken.extime.services.MardukExchangeBlobStoreService;
import no.rutebanken.extime.stop.StopAreaRepository;
import no.rutebanken.extime.util.AvinorTimetableUtils;
import no.rutebanken.extime.util.DateUtils;
import no.rutebanken.extime.util.ExtimeException;
import no.rutebanken.extime.util.Retry;
import no.rutebanken.extime.util.RetrySettings;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.Quay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Produces one NeTEx export: fetch the timetables, convert them, zip them, upload the archive and tell
 * marduk about it.
 *
 * <p>This is the whole of the former {@code AvinorTimetableRouteBuilder} as a single method. The steps
 * are unchanged and in the same order; what has gone is the exchange that carried state between them as
 * headers and properties.
 */
@Component
public class TimetableExportJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimetableExportJob.class);

    private static final String COMMON_ELEMENTS_FILENAME = "_avinor_common_elements";
    private static final String NETEX_ARCHIVE_PREFIX = "avinor-netex_";

    private final StopAreaRepository stopAreaRepository;
    private final FlightEventFetcher flightEventFetcher;
    private final ScheduledFlightConverter scheduledFlightConverter;
    private final CommonDataToNetexConverter commonDataToNetexConverter;
    private final LineDataToNetexConverter lineDataToNetexConverter;
    private final NetexMarshaller netexMarshaller;
    private final AvinorTimetableUtils avinorTimetableUtils;
    private final MardukExchangeBlobStoreService mardukExchangeBlobStoreService;
    private final MardukNotifier mardukNotifier;
    private final DateUtils dateUtils;
    private final RetrySettings retrySettings;
    private final Path generatedOutputPath;
    private final String blobPath;
    private final Timer exportTimer;
    private final MeterRegistry meterRegistry;
    private final AtomicBoolean running = new AtomicBoolean();

    @SuppressWarnings("java:S107") // the job is the seam where every collaborator meets
    public TimetableExportJob(
            StopAreaRepository stopAreaRepository,
            FlightEventFetcher flightEventFetcher,
            ScheduledFlightConverter scheduledFlightConverter,
            CommonDataToNetexConverter commonDataToNetexConverter,
            LineDataToNetexConverter lineDataToNetexConverter,
            NetexMarshaller netexMarshaller,
            AvinorTimetableUtils avinorTimetableUtils,
            MardukExchangeBlobStoreService mardukExchangeBlobStoreService,
            MardukNotifier mardukNotifier,
            DateUtils dateUtils,
            RetrySettings retrySettings,
            MeterRegistry meterRegistry,
            @Value("${netex.generated.output.path}") String generatedOutputPath,
            @Value("${blobstore.blob.path}") String blobPath) {
        this.stopAreaRepository = stopAreaRepository;
        this.flightEventFetcher = flightEventFetcher;
        this.scheduledFlightConverter = scheduledFlightConverter;
        this.commonDataToNetexConverter = commonDataToNetexConverter;
        this.lineDataToNetexConverter = lineDataToNetexConverter;
        this.netexMarshaller = netexMarshaller;
        this.avinorTimetableUtils = avinorTimetableUtils;
        this.mardukExchangeBlobStoreService = mardukExchangeBlobStoreService;
        this.mardukNotifier = mardukNotifier;
        this.dateUtils = dateUtils;
        this.retrySettings = retrySettings;
        this.meterRegistry = meterRegistry;
        this.generatedOutputPath = Path.of(generatedOutputPath);
        this.blobPath = blobPath;
        this.exportTimer = Timer.builder("extime.timetable.export")
                .description("Duration of a complete Avinor timetable export")
                .register(meterRegistry);
    }

    /**
     * Runs one export, and only one at a time.
     *
     * <p>The cron cannot overlap itself: {@code @Scheduled} runs it on a single-threaded scheduler. A
     * manual trigger arrives on a request thread and can, and two exports sharing one output directory
     * would build the archive from each other's half-written files.
     *
     * @return the correlation id, which is also the attribute marduk joins its import to.
     * @throws ExportAlreadyRunningException if an export is in progress.
     */
    public String export() {
        if (!running.compareAndSet(false, true)) {
            throw new ExportAlreadyRunningException();
        }
        try {
            return runExport();
        } finally {
            // Released whatever happened, including an Error: a held flag would block every later export
            // with nothing to show why.
            running.set(false);
        }
    }

    private String runExport() {
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = UUID.randomUUID().toString();
        LOGGER.info("Starting the Avinor timetable export, correlationId={}", correlationId);
        try {
            Map<String, Quay> nsrQuayMap = stopAreaRepository.loadQuayMap();
            List<FlightEvent> flightEvents = flightEventFetcher.fetchFlightEvents();

            LOGGER.info("Converting to line centric flight data sets");
            List<LineDataSet> lineDataSets =
                    scheduledFlightConverter.convertFlightEventsToLineCentricDataSets(flightEvents);

            cleanOutputDirectory();
            convertCommonDataToNetex(nsrQuayMap);
            convertLineDataSetsToNetex(lineDataSets);
            compressAndSendToStorage(correlationId);

            meterRegistry.counter("extime.timetable.export.result", "result", "success").increment();
            LOGGER.info("Finished the Avinor timetable export, correlationId={}", correlationId);
            return correlationId;
        } catch (RuntimeException e) {
            meterRegistry.counter("extime.timetable.export.result", "result", "failure").increment();
            LOGGER.error("The Avinor timetable export failed, correlationId={}", correlationId, e);
            throw e;
        } finally {
            sample.stop(exportTimer);
        }
    }

    /**
     * The archive is built by globbing this directory, so a file left behind from an earlier run ships as
     * part of today's dataset. If it belongs to a line that no longer exists, nothing overwrites it and
     * nothing notices. {@code Files.delete} therefore reports a failure rather than returning false into
     * the void, as the Camel processor this replaces did.
     */
    private void cleanOutputDirectory() {
        LOGGER.info("Cleaning NeTEx output directory : {}", generatedOutputPath);
        try {
            Files.createDirectories(generatedOutputPath);
            try (Stream<Path> netexFiles = Files.list(generatedOutputPath)) {
                for (Path netexFile : netexFiles.filter(Files::isRegularFile).toList()) {
                    Files.delete(netexFile);
                }
            }
        } catch (IOException e) {
            throw new ExtimeException("Could not clean the NeTEx output directory " + generatedOutputPath, e);
        }
    }

    private void convertCommonDataToNetex(Map<String, Quay> nsrQuayMap) {
        LOGGER.info("Converting common aviation data to NeTEx");
        JAXBElement<PublicationDeliveryStructure> commonData =
                commonDataToNetexConverter.convertToNetex(nsrQuayMap);
        netexMarshaller.marshalToFile(commonData, generatedOutputPath.resolve(COMMON_ELEMENTS_FILENAME + ".xml"));
    }

    private void convertLineDataSetsToNetex(List<LineDataSet> lineDataSets) {
        LOGGER.info("Converting line centric data sets to NeTEx");
        for (LineDataSet lineDataSet : lineDataSets) {
            lineDataSet.setAirlineName(scheduledFlightConverter.resolveAirlineName(lineDataSet.getAirlineIata()));
            JAXBElement<PublicationDeliveryStructure> lineData =
                    lineDataToNetexConverter.convertToNetex(lineDataSet);
            String filename = avinorTimetableUtils.generateFilename(lineData);
            netexMarshaller.marshalToFile(lineData, generatedOutputPath.resolve(filename + ".xml"));
        }
    }

    private void compressAndSendToStorage(String correlationId) {
        LOGGER.info("Compressing XML files and send to storage");
        String archiveName = NETEX_ARCHIVE_PREFIX + dateUtils.timestamp() + ".zip";
        Path archive = avinorTimetableUtils.compressNetexFiles(archiveName);
        LOGGER.info("Done compressing all files to zip archive : {}", archiveName);

        String fileHandle = blobPath + archiveName;
        Retry.withRetry(retrySettings, "Uploading " + fileHandle, () -> uploadBlob(fileHandle, archive));
        LOGGER.info("Done storage upload of file : {}", archiveName);

        mardukNotifier.notifyMarduk(archiveName, fileHandle, correlationId);
    }

    private void uploadBlob(String fileHandle, Path archive) throws IOException {
        try (InputStream in = Files.newInputStream(archive)) {
            mardukExchangeBlobStoreService.uploadBlob(fileHandle, in);
        }
    }
}
