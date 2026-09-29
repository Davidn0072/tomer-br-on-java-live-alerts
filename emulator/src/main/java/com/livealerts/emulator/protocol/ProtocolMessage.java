package com.livealerts.emulator.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Common shape of every message on the TCP wire protocol. Each concrete type carries its own
 * {@code type} discriminator so {@link MessageCodec} can deserialize a line into the right class.
 *
 * <p>Deliberately duplicated from the server's identical class rather than shared through a
 * Maven module: the protocol is five small records, and a shared module would complicate each
 * side's Docker build for little benefit at this project's size — see PLAN.md §2.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ConnectMessage.class, name = ConnectMessage.TYPE),
        @JsonSubTypes.Type(value = DisconnectMessage.class, name = DisconnectMessage.TYPE),
        @JsonSubTypes.Type(value = SendMessageMessage.class, name = SendMessageMessage.TYPE),
        @JsonSubTypes.Type(value = AckMessage.class, name = AckMessage.TYPE),
        @JsonSubTypes.Type(value = ErrorMessage.class, name = ErrorMessage.TYPE),
})
public sealed interface ProtocolMessage
        permits ConnectMessage, DisconnectMessage, SendMessageMessage, AckMessage, ErrorMessage {

    String type();
}
