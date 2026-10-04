package com.livealerts.server.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Client -> server notice that every other client's view should clear its message list. */
public record ClearScreenMessage(String type, String clientId) implements ProtocolMessage {

    public static final String TYPE = "ClearScreen";

    /** See {@link ConnectMessage#ConnectMessage(String)} for why {@code type} isn't a creator param. */
    @JsonCreator
    public ClearScreenMessage(@JsonProperty("clientId") String clientId) {
        this(TYPE, clientId);
    }
}
