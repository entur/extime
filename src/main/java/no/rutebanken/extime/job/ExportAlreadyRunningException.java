package no.rutebanken.extime.job;

/**
 * An export was requested while one was already in progress.
 */
public class ExportAlreadyRunningException extends RuntimeException {

    public ExportAlreadyRunningException() {
        super("An Avinor timetable export is already running");
    }
}
