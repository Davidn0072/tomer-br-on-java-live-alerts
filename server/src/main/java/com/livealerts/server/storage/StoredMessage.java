package com.livealerts.server.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/** A single {@code SendMessage} received from a client, as stored in MSSQL. */
@Entity
@Table(name = "messages")
public class StoredMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "text", nullable = false, length = 4000)
    private String text;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected StoredMessage() {
        // required by JPA
    }

    public StoredMessage(String clientId, String text, Instant receivedAt) {
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        this.text = Objects.requireNonNull(text, "text");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
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
}
