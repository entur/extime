# Extime

Extime fetches flights information provided by [Avinor](https://avinor.no), the company operating most Norwegian airports, and converts them into NeTEx-compliant timetables.

Avinor publishes flights information through its [flight data web service portal](https://avinor.no/konsern/tjenester/flydata), using a custom XML format.

These datasets are downloaded, combined and converted into NeTEx timetables compliant with the [Nordic NeTEx Profile](https://enturas.atlassian.net/wiki/spaces/PUBLIC/pages/728891481/Nordic+NeTEx+Profile).
Ultimately the flight timetables are injected into the [Norwegian National Journey Planner](https://en-tur.no) and used to provide multi-modal journey planning. 

# Dataflows

## Input
Extime makes use of the following dataset provided by Avinor:
- Flights timetables: https://asrv.avinor.no/XmlFeedScheduled/v1.0

## Output
Extimes converts the flight data into a set of NeTEx documents packaged as a zip file following the [Nordic NeTEx Profile delivery format](https://enturas.atlassian.net/wiki/spaces/PUBLIC/pages/728563782/General+information+NeTEx#Generalinformation:NeTEx-PublicationDeliveryExchanginginformation).

# Scope
Extime extracts and converts only a subset of the flights published by Avinor. Flight are filtered through a whitelist of airports and a whitelist of airlines.
 
# Reference data
Extime generates NeTEx timetables that reference airports as NeTEx **StopPlaces** and **Quays**. These StopPlaces and Quays should be declared in the [Norwegian Stop Place Register](https://stoppested.entur.org).

# Configuration

## Airport and airlines
Airports and airlines are configured in:

- `no.rutebanken.extime.model.AirportIATA`: Airports whitelist

- `no.rutebanken.extime.model.AirlineIATA`: Airlines whitelist

- `netex-static-data.yml`: Metadata for airports and airlines
 
## General Application configuration
General application configuration parameters are set in the Spring Boot configuration file `application.properties`

Main parameters:

| Parameter name                      |                           Description                           | 
|-------------------------------------|:---------------------------------------------------------------:|
| avinor.timetable.feed.endpoint      |                   Flights timetables endpoint                   |
| avinor.timetable.scheduler.consumer | Configuration of the frequency of the Flight information import |
| avinor.timetable.period.forward     |      Time window for which future flight data are imported      |
| avinor.timetable.period.back        |       Time window for which past flight data are imported       |

## Workflow overview
- For each whitelisted airport, Extime sends a query to the Avinor REST API and retrieves all departure for passenger flights over a given time period.
- The API returns a list of _Flight_ objects that represent a single departure.
- The _Flight_ objects are successively mapped to _FlightEvent_  and _FlightLeg_ object.
- A heuristic searches among all _FlightLegs_ those that are part of a multi-leg flight:
  - they share the same airline and flight number,
  - a given leg arrives at the same airport as the one the next leg departs from,
  - the layover between the two legs is less than 3 hours.
- Single-leg and multi-leg flights are mapped to _ScheduledFlight_ objects.
- _ScheduledFlights_ are grouped together to build NeTEx ServiceJourneys, JourneyPatterns, Routes and Lines

## System integration

### Production environment

In the production environment, Extime uploads the generated NeTEx archive in a common storage area on Google Storage.

Once the archive is uploaded, Extime notifies [Marduk](https://github.com/entur/marduk) that a new delivery is available by publishing a message on a Google PubSub topic.

Access to Google Storage and Google PubSub is configured through the properties `blobstore.gcs.*` and `spring.cloud.gcp.pubsub.*` respectively.

### Development environment 
In a development environment:
 - Google Storage can be substituted by the local file system. Use the following properties:
```
spring.profiles.active=local-disk-blobstore
blobstore.local.folder=/path/to/local/directory
```
 
 - Google PubSub can be substituted by Google PubSub emulator. Use the following property:
```
spring.cloud.gcp.pubsub.emulatorHost=localhost:8085
```

# Local testing
Use the following properties to capture the XML data sent by the Avinor REST API:
```
avinor.timetable.dump.output=true
avinor.timetable.dump.output.path=/home/user/extime/data/dump
```
Use the following properties to use the dumped data as input:
```
avinor.timetable.dump.input=true
avinor.timetable.dump.input.path=/home/user/extime/data/input
```
# Readiness and liveness
Readiness and liveness probes are provided by Spring Boot Actuator (/actuator/health/readiness, /actuator/health/liveness)

