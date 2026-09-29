package com.livealerts.emulator.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * (De)serializes {@link ProtocolMessage}s to and from the newline-delimited JSON wire format:
 * one JSON object per line, terminated by {@code \n}. A line is read/written whole; this class
 * does not itself read from or write to a socket.
 */
public final class MessageCodec {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Serializes a message to a single line, including the trailing {@code \n} frame terminator. */
    public String encode(ProtocolMessage message) {
        try {
            return mapper.writeValueAsString(message) + "\n";
        } catch (JsonProcessingException e) {
            // Our own DTOs are always serializable; a failure here is a programming error.
            throw new IllegalStateException("Failed to encode protocol message: " + message, e);
        }
    }

    /**
     * Parses one line (without its frame terminator) into a {@link ProtocolMessage}.
     *
     * @throws MalformedMessageException if the line is blank, not valid JSON, or not a
     *                                    recognized message type
     */
    public ProtocolMessage decode(String line) throws MalformedMessageException {
        if (line == null || line.isBlank()) {
            throw new MalformedMessageException("Empty message line");
        }
        try {
            return mapper.readValue(line.strip(), ProtocolMessage.class);
        } catch (JsonProcessingException e) {
            throw new MalformedMessageException("Invalid protocol message: " + e.getOriginalMessage(), e);
        }
    }
}
