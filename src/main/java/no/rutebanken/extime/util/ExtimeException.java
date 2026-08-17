package no.rutebanken.extime.util;

public class ExtimeException extends RuntimeException {

    public ExtimeException(String s, Throwable e) {
        super(s, e);
    }

    public ExtimeException(String message) {
        super(message);
    }
}