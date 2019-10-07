FROM openjdk:11-jre
WORKDIR /deployments
ADD target/extime-*-SNAPSHOT.jar extime.jar

EXPOSE 8776
CMD java $JAVA_OPTIONS -jar extime.jar

