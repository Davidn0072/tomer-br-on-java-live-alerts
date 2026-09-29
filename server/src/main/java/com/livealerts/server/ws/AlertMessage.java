package com.livealerts.server.ws;

import java.time.Instant;

/**
 * The WebSocket payload pushed to browsers. Deliberately carries no message body — the client
 * fetches the full message through the REST API, never through this channel.
 */
public record AlertMessage(String type, Long id, Instant receivedAt) {

    public static final String TYPE = "NewMessage";

    public AlertMessage(Long id, Instant receivedAt) {
        this(TYPE, id, receivedAt);
    }
}
