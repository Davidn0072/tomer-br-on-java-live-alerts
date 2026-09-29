package com.livealerts.emulator.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Server -> client notice that the previous line on the wire could not be processed. */
public record ErrorMessage(String type, String message) implements ProtocolMessage {

    public static final String TYPE = "Error";

    /** See {@link ConnectMessage#ConnectMessage(String)} for why {@code type} isn't a creator param. */
    @JsonCreator
    public ErrorMessage(@JsonProperty("message") String message) {
        this(TYPE, message);
    }
}
