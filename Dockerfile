FROM adoptopenjdk/openjdk11:alpine-jre as builder
COPY target/extime-*-SNAPSHOT.jar application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM adoptopenjdk/openjdk11:alpine-jre
WORKDIR /deployments
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser
COPY --from=builder dependencies/ ./
COPY --from=builder snapshot-dependencies/ ./
COPY --from=builder spring-boot-loader/ ./
COPY --from=builder application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]

