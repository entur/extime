package no.rutebanken.extime.routes.avinor;

import no.rutebanken.extime.model.FlightRouteDataSet;
import no.rutebanken.netex.model.PublicationDeliveryStructure;
import no.rutebanken.netex.model.PublicationRequestStructure;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FlightRouteToNeTExConverter {

    public void convertRoutesToNeTExFormat(List<FlightRouteDataSet> flightRouteDataSets) {
        NetexFormatFactory factory = new NetexFormatFactory();
        PublicationDeliveryStructure publicationDeliveryStructure = factory.createPublicationDeliveryStructure();
        PublicationRequestStructure publicationRequestStructure = factory.createPublicationRequestStructure();
        publicationDeliveryStructure.setPublicationRequest(publicationRequestStructure);
        publicationDeliveryStructure.setDataObjects(factory.createDataObjects());
    }

}
