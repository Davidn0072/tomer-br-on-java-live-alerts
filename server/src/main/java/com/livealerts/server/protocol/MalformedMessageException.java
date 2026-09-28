package com.livealerts.server.protocol;

/**
 * Thrown when a line read off the TCP socket is not a valid, recognized protocol message.
 * Checked on purpose: callers reading the socket must decide how to react (e.g. reply with an
 * {@link ErrorMessage} and keep the connection open) instead of letting it propagate and take
 * down the listener.
 */
public class MalformedMessageException extends Exception {

    public MalformedMessageException(String message) {
        super(message);
    }

    public MalformedMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
