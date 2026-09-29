package com.livealerts.emulator.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Client -> server handshake sent right after opening the TCP connection. */
public record ConnectMessage(String type, String clientId) implements ProtocolMessage {

    public static final String TYPE = "Connect";

    /**
     * Jackson resolves the concrete subtype from the wire's {@code type} field before this
     * runs, so the creator only needs the business fields — {@code type} is always this
     * class's own {@link #TYPE} constant, never whatever (if anything) came off the wire.
     */
    @JsonCreator
    public ConnectMessage(@JsonProperty("clientId") String clientId) {
        this(TYPE, clientId);
    }
}
