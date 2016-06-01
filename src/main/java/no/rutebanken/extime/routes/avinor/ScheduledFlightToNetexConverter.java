package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.model.ScheduledDirectFlight;
import no.rutebanken.extime.model.ScheduledStopoverFlight;
import no.rutebanken.netex.model.PublicationDeliveryStructure;
import org.springframework.stereotype.Component;

@Component(value = "scheduledFlightToNetexConverter")
public class ScheduledFlightToNetexConverter {

    public PublicationDeliveryStructure convertToNetex(ScheduledDirectFlight directFlight) {
        return null;
    }

    public PublicationDeliveryStructure convertToNetex(ScheduledStopoverFlight stopoverFlight) {
        return null;
    }

}
