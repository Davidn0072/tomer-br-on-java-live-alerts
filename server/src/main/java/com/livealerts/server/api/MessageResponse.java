package com.livealerts.server.api;

import com.livealerts.server.storage.StoredMessage;

import java.time.Instant;

/** What the REST API returns for one stored message. {@code type} is {@code "SendMessage"} or {@code "ClearScreen"}. */
public record MessageResponse(Long id, String clientId, String text, Instant receivedAt, String type) {

    static MessageResponse from(StoredMessage message) {
        return new MessageResponse(
                message.getId(), message.getClientId(), message.getText(), message.getReceivedAt(), message.getType());
    }
}
