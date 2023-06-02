package no.rutebanken.extime.util;

import java.io.IOException;

public class ExtimeException extends RuntimeException {
    public ExtimeException(IOException e) {
        super(e);
    }

    public ExtimeException(String s) {
        super(s);
    }

    public ExtimeException(String s, Exception e) {
        super(s, e);
    }
}