package no.rutebanken.extime.routes.blobstore;

import no.rutebanken.extime.routes.BaseRouteBuilder;
import no.rutebanken.extime.services.MardukBlobStoreService;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.extime.Constants.HEADER_MESSAGE_CORRELATION_ID;
import static no.rutebanken.extime.Constants.HEADER_MESSAGE_FILE_HANDLE;

@Component
public class MardukBlobStoreRoute extends BaseRouteBuilder {

    private final MardukBlobStoreService mardukBlobStoreService;

    public MardukBlobStoreRoute(MardukBlobStoreService mardukBlobStoreService) {
        this.mardukBlobStoreService = mardukBlobStoreService;
    }

    @Override
    public void configure() {

        from("direct:getMardukBlob")
                .to(logDebugShowAll())
                .bean(mardukBlobStoreService, "getBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, "correlationId=${header." + HEADER_MESSAGE_CORRELATION_ID + "} Returning from fetching file ${header." + HEADER_MESSAGE_FILE_HANDLE + "} from Marduk bucket.")
                .routeId("blobstore-marduk-download");
    }
}