package com.livealerts.server.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCodecTest {

    private final MessageCodec codec = new MessageCodec();

    @Test
    void roundTripsConnectMessage() throws MalformedMessageException {
        ConnectMessage original = new ConnectMessage("emulator-1");

        ProtocolMessage decoded = codec.decode(codec.encode(original));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripsDisconnectMessage() throws MalformedMessageException {
        DisconnectMessage original = new DisconnectMessage("emulator-1", "shutting down");

        ProtocolMessage decoded = codec.decode(codec.encode(original));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripsSendMessageMessage() throws MalformedMessageException {
        SendMessageMessage original = new SendMessageMessage("emulator-1", "hello from the emulator");

        ProtocolMessage decoded = codec.decode(codec.encode(original));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripsAckMessage() throws MalformedMessageException {
        AckMessage original = new AckMessage(SendMessageMessage.TYPE);

        ProtocolMessage decoded = codec.decode(codec.encode(original));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void roundTripsErrorMessage() throws MalformedMessageException {
        ErrorMessage original = new ErrorMessage("could not parse previous line");

        ProtocolMessage decoded = codec.decode(codec.encode(original));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void encodeProducesExactlyOneNewlineDelimitedLine() {
        String encoded = codec.encode(new ConnectMessage("emulator-1"));

        assertThat(encoded).endsWith("\n");
        assertThat(encoded.stripTrailing()).doesNotContain("\n");
    }

    @Test
    void decodeRejectsBlankLine() {
        assertThatThrownBy(() -> codec.decode("   "))
                .isInstanceOf(MalformedMessageException.class);
    }

    @Test
    void decodeRejectsNullLine() {
        assertThatThrownBy(() -> codec.decode(null))
                .isInstanceOf(MalformedMessageException.class);
    }

    @Test
    void decodeRejectsInvalidJson() {
        assertThatThrownBy(() -> codec.decode("{not valid json"))
                .isInstanceOf(MalformedMessageException.class);
    }

    @Test
    void decodeRejectsJsonWithoutTypeField() {
        assertThatThrownBy(() -> codec.decode("{\"clientId\":\"emulator-1\"}"))
                .isInstanceOf(MalformedMessageException.class);
    }

    @Test
    void decodeRejectsUnknownMessageType() {
        assertThatThrownBy(() -> codec.decode("{\"type\":\"Teleport\",\"clientId\":\"emulator-1\"}"))
                .isInstanceOf(MalformedMessageException.class);
    }

    @Test
    void decodeRejectsJsonArrayInsteadOfObject() {
        assertThatThrownBy(() -> codec.decode("[1,2,3]"))
                .isInstanceOf(MalformedMessageException.class);
    }
}
