package com.livealerts.emulator.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Client -> server payload to persist and alert the browser about. */
public record SendMessageMessage(String type, String clientId, String text) implements ProtocolMessage {

    public static final String TYPE = "SendMessage";

    /** See {@link ConnectMessage#ConnectMessage(String)} for why {@code type} isn't a creator param. */
    @JsonCreator
    public SendMessageMessage(@JsonProperty("clientId") String clientId, @JsonProperty("text") String text) {
        this(TYPE, clientId, text);
    }
}
