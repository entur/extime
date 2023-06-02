package no.rutebanken.extime.routes.stop;

import no.rutebanken.extime.loader.NetexDatasetLoader;
import no.rutebanken.extime.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.entur.netex.index.api.NetexEntitiesIndex;
import org.rutebanken.netex.model.KeyValueStructure;
import org.rutebanken.netex.model.Quay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.function.Function;
import java.util.stream.Collectors;

import static no.rutebanken.extime.Constants.HEADER_MESSAGE_CORRELATION_ID;
import static no.rutebanken.extime.Constants.HEADER_MESSAGE_FILE_HANDLE;
import static no.rutebanken.extime.converter.CommonDataToNetexConverter.PROPERTY_NSR_QUAY_MAP;

/**
 * Refresh the stop area repository.
 */
@Component
public class StopAreaRepositoryRouteBuilder extends BaseRouteBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(StopAreaRepositoryRouteBuilder.class);

    private final String airportsExportFilename;

    private final NetexDatasetLoader netexDatasetLoader;

    public StopAreaRepositoryRouteBuilder(
            @Value("${extime.netex.airports.export.filename:tiamat/Airports_latest.zip}") String airportsExportFilename,
            NetexDatasetLoader netexDatasetLoader) {
        super();
        this.airportsExportFilename = airportsExportFilename;
        this.netexDatasetLoader = netexDatasetLoader;
    }

    @Override
    public void configure() {
        super.configure();

        from("direct:refreshStops")
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.DEBUG, "correlationId=${header." + HEADER_MESSAGE_CORRELATION_ID + "} Refreshing stop areas.")
                .to("direct:downloadNetexStopDataset")
                .process(this::loadNetexEntitiesIndex)
                .process(this::buildAvinorLocalReferenceToQuayMap)
                .log(LoggingLevel.DEBUG, "correlationId=${header." + HEADER_MESSAGE_CORRELATION_ID + "} Refreshed stop areas.")
                .routeId("stop-area-refresh");

        from("direct:downloadNetexStopDataset")
                .log(LoggingLevel.INFO, "correlationId=${header." + HEADER_MESSAGE_CORRELATION_ID + "} Downloading NeTEx Stop dataset")
                .setHeader(HEADER_MESSAGE_FILE_HANDLE, constant(airportsExportFilename))
                .to("direct:getMardukBlob")
                .filter(body().isNull())
                .log(LoggingLevel.ERROR, "correlationId=${header." + HEADER_MESSAGE_CORRELATION_ID + "} NeTEx Stopfile not found")
                .stop()
                //end filter
                .end()
                .routeId("download-netex-stop-dataset");
    }

    private void loadNetexEntitiesIndex(Exchange exchange) {
        LOGGER.info("Loading NeTEx entries index for airports");

        InputStream stopDataset = exchange.getIn().getBody(InputStream.class);
        NetexEntitiesIndex index = netexDatasetLoader.load(stopDataset);
        exchange.getIn().setBody(index);
    }

    private void buildAvinorLocalReferenceToQuayMap(Exchange exchange) {

        NetexEntitiesIndex index = exchange.getIn().getBody(NetexEntitiesIndex.class);

        Function<Quay, String> findAvinorLocalReference = quay -> quay.getKeyList().getKeyValue().stream()
                .filter(keyValueStructure -> keyValueStructure.getKey().equals("imported-id"))
                .map(KeyValueStructure::getValue)
                .findFirst()
                .orElseThrow();

        // Building Map like ["AVI:Quay:234" -> Quay]
        exchange.setProperty(PROPERTY_NSR_QUAY_MAP,
                index.getQuayIndex().getAllVersions()
                        .keySet()
                        .stream()
                        .map(quays -> index.getQuayIndex().getLatestVersion(quays))
                        .collect(Collectors.toMap(findAvinorLocalReference, Function.identity()))
        );
    }
}

