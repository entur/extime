package no.rutebanken.extime.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs an export on demand, for when waiting for tomorrow's cron is not an option.
 *
 * <p>Blocks until the export finishes, which takes well under a minute, and answers with the correlation
 * id so the run can be found in the logs and matched to marduk's import.
 *
 * <pre>
 * kubectl -n rorextime port-forward deployment/extime 8080:8080
 * curl -X POST http://localhost:8080/services/timetable/export
 * </pre>
 *
 * <p><strong>This is not a dry run.</strong> It uploads to the marduk exchange bucket and notifies
 * marduk, which replaces the current dataset. There is no mode that does otherwise.
 *
 * <p><strong>Unauthenticated.</strong> Extime has no OAuth2 setup, so reaching this needs cluster access:
 * from inside the pod, through {@code kubectl port-forward}, or from another pod via the {@code rorextime}
 * ClusterIP service, which the chart renders on port 80 with no NetworkPolicy in front of it. There is no
 * ingress, and if extime ever gets one this becomes an open button for publishing a new national flight
 * dataset and needs an authorization check first.
 */
@RestController
@RequestMapping("/services/timetable")
public class TimetableExportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimetableExportController.class);

    private final TimetableExportJob timetableExportJob;

    public TimetableExportController(TimetableExportJob timetableExportJob) {
        this.timetableExportJob = timetableExportJob;
    }

    @PostMapping("/export")
    public String export() {
        LOGGER.info("Avinor timetable export triggered manually");
        try {
            return "Export finished, correlationId=%s%n".formatted(timetableExportJob.export());
        } catch (ExportAlreadyRunningException e) {
            LOGGER.warn("Refused the manual export: one is already running");
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }
}
