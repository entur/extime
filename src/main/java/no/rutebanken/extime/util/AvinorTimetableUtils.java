package no.rutebanken.extime.util;

import com.google.common.collect.Maps;
import no.avinor.flydata.xjc.model.scheduled.Airport;
import no.rutebanken.extime.model.*;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.commons.lang3.EnumUtils;
import org.rutebanken.netex.model.CompositeFrame;
import org.rutebanken.netex.model.Line;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.ServiceFrame;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.zeroturnaround.zip.ZipUtil;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Component
public class AvinorTimetableUtils {

    private static final String XML_GLOB = "glob:*.xml";

    @Value("${netex.generated.output.path}")
    private String generatedOutputPath;

    @Value("${netex.compressed.output.path}")
    private String compressedOutputPath;

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

    public List<FlightEvent> generateFlightEventsFromFeedDump(String inputDir) throws IOException {
        try (Stream<Path> list = Files.list(Path.of(inputDir))) {
            return list
                    .map(path -> generateObjectsFromXml(path.toFile(), Airport.class))
                    .map(airport -> new FlightEventMapper().mapToFlightEvent(airport))
                    .flatMap(Collection::stream)
                    .toList();
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

        ServiceFrame sf = collect.getFirst();

        Line line = ((Line) sf.getLines().getLine_().getFirst().getValue());

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
                throw new ExtimeException("Error while compressing NeTEx files", e);
            }
        }

        try (Stream<Path> stream = Files.list(netexOutputPath)) {
            File[] files = stream
                    .filter(isRegularFile.and(path -> matcher.matches(path.getFileName())))
                    .map(Path::toFile)
                    .toArray(File[]::new);
            ZipUtil.packEntries(files, zipOutputFilePath.toFile());
        } catch (IOException e) {
            throw new ExtimeException("Error while compressing NeTEx files", e);
        }

        exchange.getIn().setHeader(Exchange.FILE_NAME_PRODUCED, zipOutputFilePath.toAbsolutePath());
    }



    public static boolean isValidFlight(StopVisitType stopVisitType, FlightLeg newFlight) {
        if (stopVisitType == StopVisitType.ARRIVAL) {
            return AirportIATA.OSL.name().equals(newFlight.getDepartureAirport());
        } else if (stopVisitType == StopVisitType.DEPARTURE) {
            return isDomesticFlight(newFlight);
        }

        return false;
    }

    public static boolean isDomesticFlight(FlightLeg flight) {
        return isValidDepartureAndArrival(flight.getDepartureAirport(), flight.getArrivalAirport());
    }

    public static boolean isValidDepartureAndArrival(String departureIATA, String arrivalIATA) {
        return EnumUtils.isValidEnum(AirportIATA.class, departureIATA)
                && EnumUtils.isValidEnum(AirportIATA.class, arrivalIATA);
    }

    private <T> T generateObjectsFromXml(File file , Class<T> clazz)   {
        try {
            return JAXBContext.newInstance(clazz).createUnmarshaller().unmarshal(
                    new StreamSource(new FileInputStream(file)), clazz).getValue();
        } catch (JAXBException | FileNotFoundException e) {
            throw new ExtimeException("Error while unmarshalling JAXB files", e);
        }
    }


}
