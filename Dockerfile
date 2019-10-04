FROM openjdk:11-jre

WORKDIR /deployments

ADD target/extime-*-SNAPSHOT.jar extime.jar
ADD target/prometheus-agent/*.jar jmx_prometheus_javaagent.jar
RUN printf "lowercaseOutputName: true\nlowercaseOutputLabelNames: true\n" > prometheus.yaml

EXPOSE 8776
CMD java $JAVA_OPTIONS -javaagent:jmx_prometheus_javaagent.jar=9777:prometheus.yaml -jar extime.jar

