package no.rutebanken.extime.routes.avinor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import no.avinor.flydata.airlines.AirlineNames;
import no.avinor.flydata.traffic.Airport;
import no.rutebanken.extime.routes.BaseRouteBuilder;

/**
 * Performs deeper validation of GTFS files and distribute further.
 */
@Component
public class AvinorTimetableRouteBuilder extends BaseRouteBuilder {

	@Value("${extime.avinor.airports}")
	private String airports;
	
    @Override
    public void configure() throws Exception {
        super.configure();
        
        String[] airportsSplitted = airports.split(",");

        JaxbDataFormat feedFormat = new JaxbDataFormat("no.avinor.flydata.traffic");
        JaxbDataFormat airlinesFormat = new JaxbDataFormat("no.avinor.flydata.airlines");
        JaxbDataFormat airportsFormat = new JaxbDataFormat("no.avinor.flydata.airports");
        
        // Cron task a few times a day
        from("quartz2://pollAvinorTimetable?fireNow=true&trigger.repeatCount=0")
        .log("Getting Avinor timetable")
        .setBody(constant(airportsSplitted))
        // For each airport in property extime.avinor.airports fetch data
        .split(body(),new ArrayListAggregationStrategy())
	        .parallelProcessing()
	        .setHeader(Exchange.HTTP_QUERY, simple("TimeFrom=0&TimeTo=24&direction=D&airport=${body}"))
	        .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
	        .to("http4://flydata.avinor.no/XmlFeed.asp")
	        .unmarshal(feedFormat)
        .end()
        // Filter away flights to other coutries (dom_int=D(omestic))
        .to("bean:avinorTimetableParser")
        // Fetch airline names for use as operator
      //  .enrich("direct:airlineNames",new AirlineNameAggregationStrategy())
        // Fetch airport names for use as stop name
      //  .enrich("direct:airportNames",new AirportNameAggregationStrategy())
        .to("bean:avinor2GTFSConverter")
        .to("activemq:queue:ExtimeTimetables")
        .log("Finished processing timetable")
        .routeId("avinorTimetable");
        
        from("direct:airlineNames")
        .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
        .to("http4://flydata.avinor.no/airlineNames.asp")
        .unmarshal(airlinesFormat)
        .routeId("avinorAirlineNames")
        .end();
        
        from("direct:airportNames")
        .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
        .to("http4://flydata.avinor.no/airportNames.asp")
        .unmarshal(airportsFormat)
        .routeId("avinorAirportNames")
        .end();
        
        
        
        
        // Combine/filter flights (example: SDN-SOG-OSL will be returned 3 times, one for each airport)
        
        // Serialize data to common format
        
        // Send to queue for exchange format serialization

    }
    
  
     
    //simply combines Exchange body values into an ArrayList<Object>
    class ArrayListAggregationStrategy implements AggregationStrategy {
        
        @SuppressWarnings("unchecked")
		public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Object newBody = newExchange.getIn().getBody();
            ArrayList<Object> list = null;
            if (oldExchange == null) {
                list = new ArrayList<Object>();
                list.add(newBody);
                newExchange.getIn().setBody(list);
                return newExchange;
            } else {
                list = oldExchange.getIn().getBody(ArrayList.class);
                list.add(newBody);
                return oldExchange;
            }
        }
    }

    class AirlineNameAggregationStrategy implements AggregationStrategy {
        
    	  public Exchange aggregate(Exchange original, Exchange resource) {
    	        @SuppressWarnings("unchecked")
				List<Airport> originalBody = (List<Airport>) original.getIn().getBody();
    	        Object mergeResult = originalBody; // TODO
    	        if (original.getPattern().isOutCapable()) {
    	            original.getOut().setBody(mergeResult);
    	        } else {
    	            original.getIn().setBody(mergeResult);
    	        }
    	        return original;
    	    }
    }

    class AirportNameAggregationStrategy implements AggregationStrategy {
        
    	  public Exchange aggregate(Exchange original, Exchange resource) {
    	        Object originalBody = original.getIn().getBody();
    	        Object resourceResponse = resource.getIn().getBody();
    	        Object mergeResult = originalBody; // TODO
    	        if (original.getPattern().isOutCapable()) {
    	            original.getOut().setBody(mergeResult);
    	        } else {
    	            original.getIn().setBody(mergeResult);
    	        }
    	        return original;
    	    }
    }

}
