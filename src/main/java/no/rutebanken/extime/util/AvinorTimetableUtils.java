package no.rutebanken.extime.util;

import com.google.cloud.storage.Storage;
import com.google.common.collect.Lists;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.avinor.flydata.xjc.model.scheduled.ObjectFactory;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.ServiceType;
import no.rutebanken.extime.model.StopVisitType;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Header;
import org.apache.commons.lang3.EnumUtils;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zeroturnaround.zip.ZipUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_HTTP_URI;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_MESSAGE_CORRELATION_ID;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.PROPERTY_STATIC_FLIGHTS_XML_FILE;

@Component
public class AvinorTimetableUtils {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String XML_GLOB = "glob:*.xml";

    @Value("${netex.generated.output.path}")
    private String generatedOutputPath;

    @Value("${netex.compressed.output.path}")
    private String compressedOutputPath;

    @Value("${avinor.timetable.period.months}")
    private int numberOfMonthsInPeriod;

    @Value("${blobstore.gcs.credential.path}")
    private String credentialPath;

    @Value("${blobstore.gcs.bucket.name}")
    private String bucketName;

    @Value("${blobstore.gcs.blob.path}")
    private String blobPath;

    @Value("${blobstore.gcs.project.id}")
    private String projectId;

    @Value("${blobstore.gcs.provider.id}")
    private String providerId;


    public String useHttp4Client(@Header(HEADER_EXTIME_HTTP_URI) String httpUri) {
        return httpUri.replace("http", "http4");
    }

    public void findUniqueAirlines(List<Flight> flights) {
        System.out.printf("%nUnique airline designators:%n");
        flights.stream()
                .map(Flight::getAirlineDesignator)
                .distinct()
                .sorted()
                .forEach(System.out::println);
    }

    public JAXBElement<Flights> createFlightsElement(List<Flight> flightList) {
        ObjectFactory objectFactory = new ObjectFactory();
        Flights flights = objectFactory.createFlights();
        flights.setTime(OffsetDateTime.now());
        flights.setAirport("OSL");
        flightList.forEach(flight -> flights.getFlight().add(flight));
        return objectFactory.createFlights(flights);
    }

    public List<Flight> generateFlightsFromFeedDump(@ExchangeProperty(PROPERTY_STATIC_FLIGHTS_XML_FILE) String xmlFile) throws Exception {
        ArrayList<Flight> generatedFlights = Lists.newArrayList();
        Flights flightStructure = generateObjectsFromXml(xmlFile, Flights.class);
        List<Flight> flights = flightStructure.getFlight();
        generatedFlights.addAll(flights);
        return generatedFlights;
    }

    public List<Flight> generateStaticFlights() throws Exception {
        AirportIATA[] airportIATAs = Arrays.stream(AirportIATA.values())
                .filter(iata -> !iata.equals(AirportIATA.OSL))
                .toArray(AirportIATA[]::new);
        ArrayList<Flight> generatedFlights = Lists.newArrayList();
        for (int i = 1; i <= 2; i++) {
            for (AirportIATA airportIATA : airportIATAs) {
                String resourceName = String.format("%s-%d.xml", airportIATA, i);
                Flights flightStructure = generateObjectsFromXml(String.format("/xml/testdata/%s", resourceName), Flights.class);
                List<Flight> flights = flightStructure.getFlight();
                generatedFlights.addAll(flights);
            }
        }
        ArrayList<Flight> filteredFlights = Lists.newArrayList();
        for (Flight flight : generatedFlights) {
            for (StopVisitType stopVisitType : StopVisitType.values()) {
                if (isValidFlight(stopVisitType, flight)) {
                    filteredFlights.add(flight);
                }
            }
        }
        return filteredFlights;
    }

    public void cleanNetexOutputPath() throws Exception {
        Path netexOutputPath = Paths.get(generatedOutputPath);
        PathMatcher matcher = netexOutputPath.getFileSystem().getPathMatcher(XML_GLOB);
        Predicate<Path> isRegularFile = path -> Files.isRegularFile(path);

        if (Files.exists(netexOutputPath) && Files.isDirectory(netexOutputPath)) {
            try (Stream<Path> stream = Files.list(netexOutputPath)) {
                stream.filter(isRegularFile.and(path -> matcher.matches(path.getFileName()))).forEach((path) -> {
                    try {
                        logger.info("Deleting '{}'", path.getFileName().toString());
                        Files.delete(path);
                    } catch (IOException e) {
                        logger.info("Failed to delete '{}'", path.toAbsolutePath());
                        throw new RuntimeException("File path delete error : " + path.getFileName().toString());
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void compressNetexFiles(Exchange exchange, @Header(Exchange.FILE_NAME) String compressedFileName) throws Exception {
        Path netexOutputPath = Paths.get(generatedOutputPath);
        Path zipOutputPath = Paths.get(compressedOutputPath);
        Path zipOutputFilePath = Paths.get(zipOutputPath.toString(), compressedFileName);
        PathMatcher matcher = netexOutputPath.getFileSystem().getPathMatcher(XML_GLOB);
        Predicate<Path> isRegularFile = path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);

        if (!Files.exists(zipOutputPath)) {
            try {
                Files.createDirectory(zipOutputPath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try (Stream<Path> stream = Files.list(netexOutputPath)) {
            File[] files = stream
                    .filter(isRegularFile.and(path -> matcher.matches(path.getFileName())))
                    .map(Path::toFile)
                    .toArray(File[]::new);
            ZipUtil.packEntries(files, zipOutputFilePath.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        exchange.getIn().setHeader(Exchange.FILE_NAME_PRODUCED, zipOutputFilePath.toAbsolutePath());
    }

    public void uploadBlobToStorage(@Header(Exchange.FILE_NAME) String compressedFileName,
                                    @Header(Exchange.FILE_NAME_PRODUCED) String compressedFilePath,
                                    @Header(HEADER_MESSAGE_CORRELATION_ID) String correlationId) throws Exception {
        try {
            Path filePath = Paths.get(compressedFilePath);
            logger.info("Placing file '{}' from provider with id '{}' and correlation id '{}' in blob store.",
                    compressedFileName, providerId, correlationId);

            String blobIdName = blobPath + compressedFileName;
            logger.info("Created blob : {}", blobIdName);

            logger.info("Created blob : {}", blobIdName);
            Storage storage = BlobStoreHelper.getStorage(credentialPath, projectId);

            try (InputStream inputStream = Files.newInputStream(filePath)) {
                BlobStoreHelper.uploadBlob(storage, bucketName, blobIdName, inputStream, false);
                logger.info("Stored blob with name '{}' and size '{}' in bucket '{}'", filePath.getFileName().toString(), Files.size(filePath), bucketName);
            }
        } catch (RuntimeException e) {
            logger.warn("Failed to put file '{}' in blobstore", compressedFileName, e);
        }
    }

    public static boolean isValidFlight(StopVisitType stopVisitType, Flight newFlight) {
        if (!isScheduledPassengerFlight(newFlight)) {
            return false;
        }

        switch (stopVisitType) {
            case ARRIVAL:
                return AirportIATA.OSL.name().equalsIgnoreCase(newFlight.getDepartureStation());
            case DEPARTURE:
                return isDomesticFlight(newFlight);
        }

        return false;
    }

    public static boolean isScheduledPassengerFlight(Flight flight) {
        return ServiceType.fromCode(flight.getServiceType()) != null;
    }

    public static boolean isDomesticFlight(Flight flight) {
        return isValidDepartureAndArrival(flight.getDepartureStation(), flight.getArrivalStation());
    }

    public static boolean isValidDepartureAndArrival(String departureIATA, String arrivalIATA) {
        return EnumUtils.isValidEnum(AirportIATA.class, departureIATA)
                && EnumUtils.isValidEnum(AirportIATA.class, arrivalIATA);
    }

    private <T> T generateObjectsFromXml(String resourceName, Class<T> clazz) throws JAXBException {
        return JAXBContext.newInstance(clazz).createUnmarshaller().unmarshal(
                new StreamSource(getClass().getResourceAsStream(resourceName)), clazz).getValue();
    }


}
