package com.livealerts.server.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Server -> client acknowledgement that a previous message of {@code forType} was accepted. */
public record AckMessage(String type, String forType) implements ProtocolMessage {

    public static final String TYPE = "Ack";

    /** See {@link ConnectMessage#ConnectMessage(String)} for why {@code type} isn't a creator param. */
    @JsonCreator
    public AckMessage(@JsonProperty("forType") String forType) {
        this(TYPE, forType);
    }
}
