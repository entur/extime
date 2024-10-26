# extime [![CircleCI](https://circleci.com/gh/entur/extime/tree/master.svg?style=svg)](https://circleci.com/gh/rutebanken/extime/tree/master)

Extime fetches flights information provided by [Avinor](https://avinor.no), the company operating most Norwegian airports, and converts them into NeTEx-compliant timetables.

Avinor publishes flights information through its [flight data web service portal](https://avinor.no/konsern/tjenester/flydata), using a custom XML format.

These dataset are downloaded, combined and converted into NeTEx timetables compliant with the [Nordic NeTEx Profile](https://enturas.atlassian.net/wiki/spaces/PUBLIC/pages/728891481/Nordic+NeTEx+Profile).
Ultimately the flight timetables are injected into the [Norwegian National Journey Planner](https://en-tur.no) and used to provide multi-modal journey planning. 

# Dataflows

## Input
Extime makes use of the following dataset provided by Avinor:
- Flights timetables: https://flydata.avinor.no/XmlFeed.asp
- Airport names: https://flydata.avinor.no/airportNames.asp
- Airline names: https://flydata.avinor.no/airlineNames.asp

## Output
Extimes converts the flight data into a set of NeTEx documents packaged as a zip file following the [Nordic NeTEx Profile delivery format](https://enturas.atlassian.net/wiki/spaces/PUBLIC/pages/728563782/General+information+NeTEx#Generalinformation:NeTEx-PublicationDeliveryExchanginginformation).

# Scope
Extime extracts and converts only a subset of the flights published by Avinor. Flight are filtered through a whitelist of airports and a whitelist of airlines.
 
# Reference data
Extime generates NeTEx timetables that reference airports as NeTEx **StopPlaces** and **Quays**. These StopPlaces and Quays should be declared in the [Norwegian Stop Place Register](https://stoppested.entur.org).

By convention, flights arrive/depart from a **Quay** identified in the Stop Register  by a local reference following the format:
```
AVI:Quay:[Airport code]
```
where [Airport code] is the IATA code of the corresponding airport.

Example for Oslo airport:
```
AVI:Quay:OSL
```

# Configuration

## Airport and airlines
Airports and airlines are configured in:

- `no.rutebanken.extime.model.AirportIATA`: Airports whitelist

- `no.rutebanken.extime.model.AirlineIATA`: Airlines whitelist

- `no.rutebanken.extime.model.AirlineDesignator`: Airlines whitelist

- `netex-static-data.yml`: Metadata for airports and airlines
 
## General Application configuration
General application configuration parameters are set in the Spring Boot configuration file `application.properties`

Main parameters:

| Parameter name                      |                           Description                           | 
|-------------------------------------|:---------------------------------------------------------------:|
| avinor.timetable.feed.endpoint      |                   Flights timetables endpoint                   |
| avinor.airport.feed.endpoint        |                     Airport names endpoint                      |
| avinor.airline.feed.endpoint        |                     Airline names endpoint                      |
| avinor.timetable.scheduler.consumer | Configuration of the frequency of the Flight information import |
| avinor.timetable.period.forward     |       Time window for which flight data are imported            |

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

# Building and running

* Build: `mvn clean install`
* Local run, three different run modes are available: 
    * Dump mode, used to dump remotely fetched data to file for later reuse.
        * Enable dump mode in your local configuration file : `avinor.timetable.dump.enabled=true`
        * Choose your local directory, used to save the dump file, in property : `avinor.timetable.dump.output.path=$HOME/dev/git/extime/target/dump`
        * Configure the time window needed to fetch data for, starting from 00:01 AM the current date, by changing the property: `avinor.timetable.period.forward`, default is 14 days
        * Run : `mvn spring-boot:run -Dspring.profiles.active=dev -Dspring.config.location=$HOME/config/extime_application.properties`
    * Offline mode, uses a dump file, produced by above mode, instead of fetching from remote. Should only be used in dev environment. (Great for debugging!)
        * N.B. To run this mode, the application expects the availability of a dump file, produced in dump mode.
        * If you have dump mode enabled, disable in your local configuration file : `avinor.timetable.dump.enabled=false`
        * Run : `mvn spring-boot:run -Dspring.profiles.active=dev -Dspring.config.location=$HOME/config/extime_application.properties -Davinor.timetable.dump.file=$HOME/dev/git/extime/target/dump/dump.xml`
    * Normal mode, fetches all data from remote feed. This is the normal mode used in test and prod environment.
        * Configure the time window needed to fetch data for, starting from 00:01 AM the current date, by changing the property: `avinor.timetable.period.forward`, default is 14 days 
        * Run : `mvn spring-boot:run -Dspring.profiles.active=dev -Dspring.config.location=$HOME/config/extime_application.properties`
* Local debug run with Maven, to enable maven debugging, add the following configuration to the `spring-boot-maven-plugin` plugin in pm.xml:
```
<configuration>
    <jvmArguments>-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005</jvmArguments>
</configuration>
```
* Run the docker image in docker on dev machine:
     * `docker run -it --name extime -e JAVA_OPTIONS="-Xmx1280m -Dspring.profiles.active=dev" rutebanken/extime:0.0.1-SNAPSHOT`
* 
# Readiness and liveness

For now, use the following health endpoint:

```

http://127.0.0.1:9001/health

```
