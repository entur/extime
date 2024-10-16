package no.rutebanken.extime.xml;

import no.avinor.flydata.xjc.model.scheduled.Flight;
import no.rutebanken.extime.util.AvinorTimetableUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.xml.bind.JAXBException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

class DateTimeUnmarshallerTest {


    @Test
    void unmarshalScheduledFlight() throws JAXBException {
        List<Flight> flights = new AvinorTimetableUtils().generateFlightsFromFeedDump("/xml/scheduled-flights.xml");

        Flight first = flights.getFirst();
        Assertions.assertThat(first.getDateOfOperation()).isNotNull().isEqualTo(ZonedDateTime.of(LocalDate.of(2016, 6, 15).atStartOfDay(), ZoneId.of("UTC")));
        Assertions.assertThat(first.getStd()).isNotNull().isEqualTo(LocalTime.of(9, 30));
        Assertions.assertThat(first.getSta()).isNotNull().isEqualTo(LocalTime.of(10, 50));
    }

}
