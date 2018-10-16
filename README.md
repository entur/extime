# extime [![CircleCI](https://circleci.com/gh/rutebanken/extime/tree/master.svg?style=svg&circle-token=b80134a1579275938f9bf86b7dde5df2fcfc1b5d)](https://circleci.com/gh/rutebanken/extime/tree/master)

Components for fetching external timetable and realtime information from non compliant sources (non NeTEx nor SIRI)

For details, see the
initial project setup location:
  https://github.com/fabric8io/ipaas-quickstarts/

## Configuration

The application is unable to run without configuration. This must be defined externally to the application in a .properties file.
Copy application.properties from the project in test/resources, to a local directory, e.g. {user.home}/config/extime_application.properties.
Customize the the local runtime behavior by changing the values in your local configuration file instead of chaning values in the project application.properties.

Typical application properties for dev environment:

```
spring.main.sources=no.rutebanken.extime

camel.springboot.name=extime

avinor.airports.large=BGO,BOO,SVG,TRD

avinor.timetable.scheduler.consumer=quartz2://avinorTimetableScheduler?fireNow=true&trigger.repeatCount=0
avinor.timetable.period.months=1
avinor.timetable.max.range=180
avinor.timetable.min.range=60
avinor.timetable.feed.endpoint=http://195.69.13.136/XmlFeedScheduled.asp

avinor.timetable.dump.enabled=false
avinor.timetable.dump.output.path=$HOME/dev/git/extime/target/flights

avinor.realtime.scheduler.options=quartz2://avinorRealtimeScheduler?fireNow=true&trigger.repeatCount=0
avinor.realtime.feed.endpoint=http4://flydata.avinor.no/XmlFeed.asp
avinor.realtime.departures.timefrom=0
avinor.realtime.departures.timeto=72
avinor.realtime.arrivals.timefrom=0
avinor.realtime.arrivals.timeto=96

avinor.airport.feed.endpoint=http://flydata.avinor.no/airportNames.asp
avinor.airline.feed.endpoint=http://flydata.avinor.no/airlineNames.asp

netex.generated.output.path=$HOME/dev/git/extime/target/netex
#netex.generated.output.path=$HOME/dev/temp/netex
netex.compressed.output.path=$HOME/dev/git/extime/target/marduk
netex.compressed.file.prefix=avinor-netex_

blobstore.gcs.bucket.name=marduk-test-exchange
blobstore.gcs.blob.path=inbound/received/avi/
blobstore.gcs.credential.path=$HOME/dev/config/Carbon-6120ee33c0d0.json
blobstore.gcs.project.id=carbon-1287
blobstore.gcs.provider.id=21

spring.activemq.broker-url=vm://localhost?jms.useAsyncSend=true&broker.persistent=false
spring.activemq.pooled=true
spring.activemq.user=admin
spring.activemq.password=***REMOVED***

queue.upload.destination.name=MardukInboundTestQueue

logging.level.org.apache=INFO
logging.level.org.apache.http.wire=INFO
logging.level.no.rutebanken=DEBUG

jolokia.config.debug=true

endpoints.jolokia.enabled=true
endpoints.jolokia.path=/jolokia
endpoints.jmx.enabled=true

spring.jmx.enabled=true

management.port=9001
management.address=127.0.0.1
```

## Building and running

* Build: `mvn clean install`
* Local run, three different run modes are available: 
    * Dump mode, used to dump remotely fetched data to file for later reuse.
        * Enable dump mode in your local configuration file : `avinor.timetable.dump.enabled=true`
        * Choose your local directory, used to save the dump file, in property : `avinor.timetable.dump.output.path=$HOME/dev/git/extime/target/dump`
        * Configure number of months needed to fetch data for, starting from 00:01 AM the current date, by changing the property: `avinor.timetable.period.months`, default is 1 month
        * Run : `mvn spring-boot:run -Dspring.profiles.active=dev -Dspring.config.location=$HOME/config/extime_application.properties`
    * Offline mode, uses a dump file, produced by above mode, instead of fetching from remote. Should only be used in dev environment. (Great for debugging!)
        * N.B. To run this mode, the application expects the availability of a dump file, produced in dump mode.
        * If you have dump mode enabled, disable in your local configuration file : `avinor.timetable.dump.enabled=false`
        * Run : `mvn spring-boot:run -Dspring.profiles.active=dev -Dspring.config.location=$HOME/config/extime_application.properties -Davinor.timetable.dump.file=$HOME/dev/git/extime/target/dump/dump.xml`
    * Normal mode, fetches all data from remote feed. This is the normal mode used in test and prod environment.
        * Configure number of months needed to fetch data for, starting from 00:01 AM the current date, by changing the property: `avinor.timetable.period.months`, default is 1 month 
        * Run : `mvn spring-boot:run -Dspring.profiles.active=dev -Dspring.config.location=$HOME/config/extime_application.properties`
* Local debug run with Maven, to enable maven debugging, add the following configuration to the `spring-boot-maven-plugin` plugin in pm.xml:
```
<configuration>
    <jvmArguments>-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005</jvmArguments>
</configuration>
```
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
