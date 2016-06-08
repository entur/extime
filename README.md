# extime

Components for fetching external timetable and realtime information from non compliant sources (non NeTEx nor SIRI)

For details, see the
initial project setup location:
  https://github.com/fabric8io/ipaas-quickstarts/

## Configuration

The following properties need to be provided for the application to run:

```
# Avinor timetable scheduler settings
avinor.timetable.scheduler.options=cron=0+0+0+*+*+?&trigger.timeZone=Europe/Oslo
avinor.timetable.periodto.daysahead=30
avinor.timetable.feed.endpoint=195.69.13.136/XmlFeedScheduled.asp

# Avinor realtime scheduler settings
avinor.realtime.scheduler.options=cron=0+0+3+*+*+?&trigger.timeZone=Europe/Oslo
avinor.realtime.feed.endpoint=flydata.avinor.no/XmlFeed.asp
avinor.realtime.departures.timefrom=0
avinor.realtime.departures.timeto=72
avinor.realtime.arrivals.timefrom=0
avinor.realtime.arrivals.timeto=96

# Avinor airport service settings
avinor.airport.feed.endpoint=flydata.avinor.no/airportNames.asp

# Logging settings
logging.level.no.rutebanken=DEBUG

```

## Building and running

* Build: `mvn clean install`
* Local run: `mvn spring-boot:run`
* Docker image: `mvn -Pf8-build`
* Run the docker image in docker on dev machine (you'll need to modify ports from 22 to 2224 and also to have the lamassu.pem file present in ~/.ssh):
     * `docker run -it --name extime -e JAVA_OPTIONS="-Xmx1280m -Dspring.profiles.active=dev" --link activemq --add-host=lamassu:127.0.0.1 -v ~/.ssh/lamassu.pem:/opt/jboss/.ssh/lamassu.pem:ro rutebanken/extime:0.0.1-SNAPSHOT`
* Run the docker image in docker inside vagrant:
     * `docker run -it --name extime -e JAVA_OPTIONS="-Xmx1280m -Dspring.profiles.active=dev" --link activemq --link lamassu -v ~/.ssh/lamassu.pem:/opt/jboss/.ssh/lamassu.pem:ro rutebanken/extime:0.0.1-SNAPSHOT`
* For more docker plugin goals, see: http://ro14nd.de/docker-maven-plugin/goals.html

## Readiness and liveness

For now, use the following health endpoint:

```

http://127.0.0.1:9001/health

```