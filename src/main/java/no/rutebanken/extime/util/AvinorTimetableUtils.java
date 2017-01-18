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
import org.apache.camel.Header;
import org.apache.commons.lang3.EnumUtils;
import org.rutebanken.helper.gcp.BlobStoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static no.rutebanken.extime.routes.avinor.AvinorCommonRouteBuilder.HEADER_EXTIME_HTTP_URI;
import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.HEADER_MESSAGE_CORRELATION_ID;

@Component
public class AvinorTimetableUtils {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

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

    public List<Flight> generateFlightsFromFeedDump() throws Exception {
        ArrayList<Flight> generatedFlights = Lists.newArrayList();
        Flights flightStructure = generateObjectsFromXml("/xml/testdata/avinor-flights_20170118-203723.xml", Flights.class);
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
