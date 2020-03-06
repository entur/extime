FROM adoptopenjdk/openjdk11:alpine-jre
WORKDIR /deployments
COPY target/extime-*-SNAPSHOT.jar extime.jar
CMD java $JAVA_OPTIONS -jar extime.jar
