package no.rutebanken.extime.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.avinor.flydata.xjc.model.scheduled.Flights;
import no.avinor.flydata.xjc.model.scheduled.ObjectFactory;
import no.rutebanken.extime.config.NetexStaticDataSet;
import no.rutebanken.extime.model.AirlineDesignator;
import no.rutebanken.extime.model.AirportIATA;
import no.rutebanken.extime.model.ServiceType;
import no.rutebanken.extime.model.StopVisitType;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Header;
import org.apache.commons.lang3.EnumUtils;
import org.rutebanken.netex.model.CompositeFrame;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ServiceFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zeroturnaround.zip.ZipUtil;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static no.rutebanken.extime.routes.avinor.AvinorTimetableRouteBuilder.PROPERTY_STATIC_FLIGHTS_XML_FILE;

@Component
public class AvinorTimetableUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AvinorTimetableUtils.class);

    private static final String XML_GLOB = "glob:*.xml";

    @Value("${netex.generated.output.path}")
    private String generatedOutputPath;

    @Value("${netex.compressed.output.path}")
    private String compressedOutputPath;

    @Value("${avinor.timetable.period.months}")
    private int numberOfMonthsInPeriod;

    @Autowired
    private NetexStaticDataSet netexStaticDataSet;

    private static final Map<String, String> SPECIAL_ASCII_MAPPING = Maps.newHashMap();
    static {
        SPECIAL_ASCII_MAPPING.put("Ê", "E");
        SPECIAL_ASCII_MAPPING.put("È", "E");
        SPECIAL_ASCII_MAPPING.put("É", "E");
        SPECIAL_ASCII_MAPPING.put("ë", "e");
        SPECIAL_ASCII_MAPPING.put("é", "e");
        SPECIAL_ASCII_MAPPING.put("è", "e");
        SPECIAL_ASCII_MAPPING.put("Â", "A");
        SPECIAL_ASCII_MAPPING.put("Ä", "A");
        SPECIAL_ASCII_MAPPING.put("Å", "A");
        SPECIAL_ASCII_MAPPING.put("ä", "a");
        SPECIAL_ASCII_MAPPING.put("å", "a");
        SPECIAL_ASCII_MAPPING.put("á", "a");
        SPECIAL_ASCII_MAPPING.put("ß", "ss");
        SPECIAL_ASCII_MAPPING.put("Ç", "C");
        SPECIAL_ASCII_MAPPING.put("Ö", "O");
        SPECIAL_ASCII_MAPPING.put("Ó", "O");
        SPECIAL_ASCII_MAPPING.put("Ø", "O");
        SPECIAL_ASCII_MAPPING.put("ø", "o");
        SPECIAL_ASCII_MAPPING.put("ö", "o");
        SPECIAL_ASCII_MAPPING.put("ª", "");
        SPECIAL_ASCII_MAPPING.put("º", "");
        SPECIAL_ASCII_MAPPING.put("Ñ", "N");
        SPECIAL_ASCII_MAPPING.put("Ü", "U");
        SPECIAL_ASCII_MAPPING.put("ü", "u");
        SPECIAL_ASCII_MAPPING.put("Æ", "E");
        SPECIAL_ASCII_MAPPING.put("æ", "e");
    }

    public JAXBElement<Flights> createFlightsElement(List<Flight> flightList) {
        ObjectFactory objectFactory = new ObjectFactory();
        Flights flights = objectFactory.createFlights();
        flights.setTime(ZonedDateTime.now());
        flights.setAirport("OSL");
        flightList.forEach(flight -> flights.getFlight().add(flight));
        return objectFactory.createFlights(flights);
    }

    public List<Flight> generateFlightsFromFeedDump(@ExchangeProperty(PROPERTY_STATIC_FLIGHTS_XML_FILE) String xmlFile) throws JAXBException {
        ArrayList<Flight> generatedFlights = Lists.newArrayList();
        Flights flightStructure = generateObjectsFromXml(xmlFile, Flights.class);
        List<Flight> flights = flightStructure.getFlight();
        generatedFlights.addAll(flights);
        return generatedFlights;
    }

    public List<Flight> generateStaticFlights() throws JAXBException {
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

    public void cleanNetexOutputPath() {
        Path netexOutputPath = Paths.get(generatedOutputPath);
        PathMatcher matcher = netexOutputPath.getFileSystem().getPathMatcher(XML_GLOB);
        Predicate<Path> isRegularFile = Files::isRegularFile;

        if (Files.exists(netexOutputPath) && Files.isDirectory(netexOutputPath)) {
            try (Stream<Path> stream = Files.list(netexOutputPath)) {
                stream.filter(isRegularFile.and(path -> matcher.matches(path.getFileName()))).forEach(path -> {
                    try {
                        LOG.info("Deleting '{}'", path.getFileName());
                        Files.delete(path);
                    } catch (IOException e) {
                        LOG.info("Failed to delete '{}'", path.toAbsolutePath());
                        throw new RuntimeException("File path delete error : " + path.getFileName());
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public String generateFilename(Exchange exchange) {
        @SuppressWarnings("unchecked")
        JAXBElement<PublicationDeliveryStructure> publicationDelivery = (JAXBElement<PublicationDeliveryStructure>) exchange.getIn().getBody();

        List<ServiceFrame> collect = publicationDelivery.getValue().getDataObjects().getCompositeFrameOrCommonFrame().stream()
                .map(JAXBElement::getValue)
                .filter(CompositeFrame.class::isInstance)
                .map(e -> (CompositeFrame) e)
                .map(e -> e.getFrames().getCommonFrame())
                .map(e -> e.stream()
                        .map(JAXBElement::getValue)
                        .filter(ServiceFrame.class::isInstance)
                        .map(ex -> (ServiceFrame) ex)
                        .toList())
                .flatMap(Collection::stream)
                .toList();

        ServiceFrame sf = collect.get(0);

        Line line = ((Line) sf.getLines().getLine_().get(0).getValue());

        String networkName;
        if (sf.getNetwork() != null) {
            networkName = sf.getNetwork().getName().getValue();
        } else {
            String networkIdRef = line.getRepresentedByGroupRef().getRef();
            networkName = NetexObjectIdCreator.getObjectIdSuffix(networkIdRef);
        }

        String filename = networkName + "-" + line.getName().getValue().replace('/', '_');

        return utftoasci(filename);
    }

    private static String utftoasci(String s) {
        final StringBuilder sb = new StringBuilder(s.length() * 2);

        final StringCharacterIterator iterator = new StringCharacterIterator(s);

        char ch = iterator.current();

        while (ch != CharacterIterator.DONE) {
            sb.append(convertToAsciiChar(ch));
            ch = iterator.next();
        }
        return sb.toString().replaceAll("[^\\p{ASCII}]", "");
    }

    private static String convertToAsciiChar(char ch) {
        if (Character.getNumericValue(ch) <= 0) {
            final String s = Character.toString(ch);
            if (SPECIAL_ASCII_MAPPING.containsKey(s)) {
                return SPECIAL_ASCII_MAPPING.get(s);
            }
        }
        return Character.toString(ch);
    }

    public void compressNetexFiles(Exchange exchange, @Header(Exchange.FILE_NAME) String compressedFileName) {
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

    public static boolean isValidFlight(StopVisitType stopVisitType, Flight newFlight) {
        if (!isScheduledPassengerFlight(newFlight)) {
            return false;
        }

        if (stopVisitType == StopVisitType.ARRIVAL) {
            return AirportIATA.OSL.name().equalsIgnoreCase(newFlight.getDepartureStation());
        } else if (stopVisitType == StopVisitType.DEPARTURE) {
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

    public static boolean isCommonDesignator(String airlineIata) {
        if (EnumUtils.isValidEnum(AirlineDesignator.class, airlineIata.toUpperCase())) {
            AirlineDesignator designator = AirlineDesignator.valueOf(airlineIata.toUpperCase());
            boolean isCommonDesignator= AirlineDesignator.commonDesignators.contains(designator);
            if (!isCommonDesignator) {
                LOG.info("Received uncommon airline.designator: {}", airlineIata);
            }
            return isCommonDesignator;
        }
        return false;
    }

}
