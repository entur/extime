package no.rutebanken.extime.util;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.model.StopVisitType;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Ignore // TODO fix this to be able to run tests again (springify)
public class AvinorTimetableUtilsTest {

    @Test
    public void testIsValidFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("OSL");
            setArrivalStation("BGO");
            setServiceType("J");
        }};

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsValidArrivalToOslFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("OSL");
            setArrivalStation("BGO");
            setServiceType("J");
        }};

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.ARRIVAL, flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsNotValidArrivalToOslFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("BGO");
            setArrivalStation("OSL");
            setServiceType("J");
        }};

        boolean isValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.ARRIVAL, flight);

        Assertions.assertThat(isValidFlight).isFalse();
    }

    @Test
    public void testIsNotValidCharterFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("OSL");
            setArrivalStation("BGO");
            setServiceType("C");
        }};

        boolean isNotValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsNotValidInternationalFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("LHR");
            setArrivalStation("EWR");
            setServiceType("J");
        }};

        boolean isNotValidFlight = AvinorTimetableUtils.isValidFlight(StopVisitType.DEPARTURE, flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsScheduledPassengerFlight() throws Exception {
        Flight flight = new Flight() {{
            setServiceType("J");
        }};

        boolean isValidFlight = AvinorTimetableUtils.isScheduledPassengerFlight(flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsNotScheduledPassengerFlight() throws Exception {
        Flight flight = new Flight() {{
            setServiceType("C");
        }};

        boolean isNotValidFlight = AvinorTimetableUtils.isScheduledPassengerFlight(flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsValidDomesticFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("OSL");
            setArrivalStation("BGO");
        }};

        boolean isValidFlight = AvinorTimetableUtils.isDomesticFlight(flight);

        Assertions.assertThat(isValidFlight).isTrue();
    }

    @Test
    public void testIsNotValidDomesticFlight() throws Exception {
        Flight flight = new Flight() {{
            setDepartureStation("LHR");
            setArrivalStation("EWR");
        }};

        boolean isNotValidFlight = AvinorTimetableUtils.isDomesticFlight(flight);

        Assertions.assertThat(isNotValidFlight).isFalse();
    }

    @Test
    public void testIsValidDepartureAndArrival() throws Exception {
        boolean bothValid = AvinorTimetableUtils.isValidDepartureAndArrival("OSL", "BGO");
        Assertions.assertThat(bothValid).isTrue();
    }

    @Test
    public void testIsNotValidDepartureAndArrival() throws Exception {
        boolean bothInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("LHR", "EWR");
        Assertions.assertThat(bothInvalid).isFalse();

        boolean firstInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("LHR", "OSL");
        Assertions.assertThat(firstInvalid).isFalse();

        boolean lastInvalid = AvinorTimetableUtils.isValidDepartureAndArrival("OSL", "EWR");
        Assertions.assertThat(lastInvalid).isFalse();
    }

    @Test
    public void testStreamTransformation() throws Exception {
        String[] myArray = new String[]{"bob", "alice", "paul", "ellie"};
        Stream<String> myStream = Arrays.stream(myArray);
        Stream<String> myNewStream = myStream.map(String::toUpperCase);
        String[] myNewArray = myNewStream.toArray(String[]::new);
        for (String s : myNewArray) {
            System.out.println(s);
        }

        // or with a list
        List<String> lowerCaseStrings = Arrays.asList("bob", "alice", "paul", "ellie");
        List<String> upperCaseStrings = lowerCaseStrings.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toList());
        upperCaseStrings.forEach(System.out::println);
    }

    @Test
    public void testDistinctWithFlatMap() throws Exception {
        Article article1 = new Article("Cool", "Carl", Arrays.asList("cool", "super", "smelly"));
        Article article2 = new Article("Boring", "John", Arrays.asList("mucho", "smelly", "badass"));
        Article article3 = new Article("Nitty", "Butler", Arrays.asList("snory", "cool", "lambda"));
        List<Article> articles = Arrays.asList(article1, article2, article3);

        Set<String> distinctTags = articles.stream()
                .flatMap(article -> article.getTags().stream())
                .collect(Collectors.toSet());

        distinctTags.forEach(System.out::println);
    }

    private class Article {

        private final String title;
        private final String author;
        private final List<String> tags;

        private Article(String title, String author, List<String> tags) {
            this.title = title;
            this.author = author;
            this.tags = tags;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public List<String> getTags() {
            return tags;
        }
    }

}