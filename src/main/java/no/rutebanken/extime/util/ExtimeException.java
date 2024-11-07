package no.rutebanken.extime.util;

public class ExtimeException extends RuntimeException {

    public ExtimeException(String s, Exception e) {
        super(s, e);
    }

    public ExtimeException(String message) {
        super(message);
    }
}