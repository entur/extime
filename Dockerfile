FROM openjdk:11-jre
WORKDIR /deployments
COPY target/extime-*-SNAPSHOT.jar extime.jar
CMD java $JAVA_OPTIONS -jar extime.jar
