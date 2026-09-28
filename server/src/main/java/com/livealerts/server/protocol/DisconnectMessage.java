package com.livealerts.server.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Client -> server notice sent right before the client closes the TCP connection on purpose. */
public record DisconnectMessage(String type, String clientId, String reason) implements ProtocolMessage {

    public static final String TYPE = "Disconnect";

    /** See {@link ConnectMessage#ConnectMessage(String)} for why {@code type} isn't a creator param. */
    @JsonCreator
    public DisconnectMessage(@JsonProperty("clientId") String clientId, @JsonProperty("reason") String reason) {
        this(TYPE, clientId, reason);
    }
}
