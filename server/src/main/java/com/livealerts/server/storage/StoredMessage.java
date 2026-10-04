package com.livealerts.server.storage;

import com.livealerts.server.protocol.SendMessageMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * A single message received from a client, as stored in MSSQL — either a {@code SendMessage}
 * (has {@code text}) or a {@code ClearScreen} (no {@code text}, just a marker row). {@code type}
 * holds the same discriminator string as the originating {@link com.livealerts.server.protocol.ProtocolMessage}
 * (e.g. {@code "SendMessage"}, {@code "ClearScreen"}) rather than a separate enum, consistent
 * with how every other {@code type} field in this codebase is a plain string.
 */
@Entity
@Table(name = "messages")
public class StoredMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "text", length = 4000)
    private String text;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    protected StoredMessage() {
        // required by JPA
    }

    /** Convenience constructor for the common case: a {@code SendMessage} row (text required). */
    public StoredMessage(String clientId, String text, Instant receivedAt) {
        this(clientId, Objects.requireNonNull(text, "text"), receivedAt, SendMessageMessage.TYPE);
    }

    public StoredMessage(String clientId, String text, Instant receivedAt, String type) {
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.text = text;
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
        this.type = Objects.requireNonNull(type, "type");
    }

    public Long getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public String getText() {
        return text;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getType() {
        return type;
    }
}
