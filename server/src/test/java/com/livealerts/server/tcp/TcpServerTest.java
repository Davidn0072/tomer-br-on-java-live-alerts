package com.livealerts.server.tcp;

import com.livealerts.server.protocol.MessageCodec;
import com.livealerts.server.protocol.SendMessageMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link TcpServer} end to end over real sockets: no Spring context and no database,
 * since this layer doesn't depend on either. Covers the exercise's robustness requirement —
 * malformed JSON and abrupt disconnects must not take the server down.
 */
class TcpServerTest {

    private static final int SOCKET_TIMEOUT_MS = 2000;

    private final List<SendMessageMessage> receivedMessages = new CopyOnWriteArrayList<>();
    private TcpServer server;

    @BeforeEach
    void startServer() {
        server = new TcpServer(0, new MessageCodec(), receivedMessages::add);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void connectThenSendMessageIsAckedAndDeliveredToListener() throws IOException {
        try (Socket socket = connect()) {
            BufferedReader in = reader(socket);

            send(socket, "{\"type\":\"Connect\",\"clientId\":\"emulator-1\"}");
            assertThat(in.readLine()).isEqualTo("{\"type\":\"Ack\",\"forType\":\"Connect\"}");

            send(socket, "{\"type\":\"SendMessage\",\"clientId\":\"emulator-1\",\"text\":\"hello\"}");
            assertThat(in.readLine()).isEqualTo("{\"type\":\"Ack\",\"forType\":\"SendMessage\"}");
        }

        assertThat(receivedMessages)
                .containsExactly(new SendMessageMessage("emulator-1", "hello"));
    }

    @Test
    void disconnectMessageIsAcked() throws IOException {
        try (Socket socket = connect()) {
            BufferedReader in = reader(socket);

            send(socket, "{\"type\":\"Disconnect\",\"clientId\":\"emulator-1\",\"reason\":\"shutting down\"}");
            assertThat(in.readLine()).isEqualTo("{\"type\":\"Ack\",\"forType\":\"Disconnect\"}");
        }
    }

    @Test
    void malformedJsonGetsAnErrorReplyButConnectionStaysOpen() throws IOException {
        try (Socket socket = connect()) {
            BufferedReader in = reader(socket);

            send(socket, "{not valid json");
            String errorLine = in.readLine();
            assertThat(errorLine).contains("\"type\":\"Error\"");

            // The connection must still be usable after a malformed line.
            send(socket, "{\"type\":\"Connect\",\"clientId\":\"emulator-1\"}");
            assertThat(in.readLine()).isEqualTo("{\"type\":\"Ack\",\"forType\":\"Connect\"}");
        }
    }

    @Test
    void unknownMessageTypeGetsAnErrorReplyButConnectionStaysOpen() throws IOException {
        try (Socket socket = connect()) {
            BufferedReader in = reader(socket);

            send(socket, "{\"type\":\"Teleport\",\"clientId\":\"emulator-1\"}");
            assertThat(in.readLine()).contains("\"type\":\"Error\"");

            send(socket, "{\"type\":\"Connect\",\"clientId\":\"emulator-1\"}");
            assertThat(in.readLine()).isEqualTo("{\"type\":\"Ack\",\"forType\":\"Connect\"}");
        }
    }

    @Test
    void abruptDisconnectDoesNotCrashServerAndItKeepsAcceptingClients() throws IOException {
        try (Socket socket = connect()) {
            send(socket, "{\"type\":\"Connect\",\"clientId\":\"flaky-client\"}");
            reader(socket).readLine(); // wait for the Ack so the message was actually processed
            socket.setSoLinger(true, 0); // force an RST instead of a clean FIN on close()
        }

        // The server must still be able to serve a brand new connection afterwards.
        try (Socket socket = connect()) {
            BufferedReader in = reader(socket);
            send(socket, "{\"type\":\"Connect\",\"clientId\":\"next-client\"}");
            assertThat(in.readLine()).isEqualTo("{\"type\":\"Ack\",\"forType\":\"Connect\"}");
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket("localhost", server.getPort());
        socket.setSoTimeout(SOCKET_TIMEOUT_MS);
        return socket;
    }

    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    private static void send(Socket socket, String line) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
