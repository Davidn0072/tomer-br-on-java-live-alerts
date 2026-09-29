package com.livealerts.emulator.protocol;

/** Thrown when a line read off the TCP socket is not a valid, recognized protocol message. */
public class MalformedMessageException extends Exception {

    public MalformedMessageException(String message) {
        super(message);
    }

    public MalformedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
