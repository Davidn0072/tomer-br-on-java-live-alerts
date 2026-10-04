package com.livealerts.server.tcp;

import com.livealerts.server.protocol.AckMessage;
import com.livealerts.server.protocol.ClearScreenMessage;
import com.livealerts.server.protocol.ConnectMessage;
import com.livealerts.server.protocol.DisconnectMessage;
import com.livealerts.server.protocol.ErrorMessage;
import com.livealerts.server.protocol.MalformedMessageException;
import com.livealerts.server.protocol.MessageCodec;
import com.livealerts.server.protocol.ProtocolMessage;
import com.livealerts.server.protocol.SendMessageMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Owns one client's TCP connection end to end: reads newline-delimited JSON lines, replies with
 * {@code Ack}/{@code Error}, and returns cleanly on EOF, abrupt disconnect, or malformed input.
 * A bad line never closes the connection — only the client closing its end (or an I/O failure)
 * does; the server keeps serving every other connection regardless of what happens here.
 */
final class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final MessageCodec codec;
    private final MessageReceivedListener listener;

    ClientHandler(Socket socket, MessageCodec codec, MessageReceivedListener listener) {
        this.socket = socket;
        this.codec = codec;
        this.listener = listener;
    }

    @Override
    public void run() {
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        try (socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = socket.getOutputStream()) {

            String line;
            while ((line = reader.readLine()) != null) {
                handleLine(line, out);
            }
            log.info("Connection {} closed by client", remote);
        } catch (IOException e) {
            log.info("Connection {} closed abruptly: {}", remote, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Unexpected error handling connection {}", remote, e);
        }
    }

    private void handleLine(String line, OutputStream out) throws IOException {
        ProtocolMessage message;
        try {
            message = codec.decode(line);
        } catch (MalformedMessageException e) {
            log.info("Malformed message from {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
            send(out, new ErrorMessage(e.getMessage()));
            return;
        }
        dispatch(message, out);
    }

    private void dispatch(ProtocolMessage message, OutputStream out) throws IOException {
        if (message instanceof ConnectMessage connect) {
            log.info("Client connected: {}", connect.clientId());
            send(out, new AckMessage(ConnectMessage.TYPE));
        } else if (message instanceof DisconnectMessage disconnect) {
            log.info("Client {} disconnecting: {}", disconnect.clientId(), disconnect.reason());
            send(out, new AckMessage(DisconnectMessage.TYPE));
        } else if (message instanceof SendMessageMessage sendMessage) {
            log.info("Message from {}: {}", sendMessage.clientId(), sendMessage.text());
            listener.onMessageReceived(sendMessage);
            send(out, new AckMessage(SendMessageMessage.TYPE));
        } else if (message instanceof ClearScreenMessage clearScreen) {
            log.info("Clear-screen requested by {}", clearScreen.clientId());
            listener.onMessageReceived(clearScreen);
            send(out, new AckMessage(ClearScreenMessage.TYPE));
        } else {
            log.info("Unexpected message type from client: {}", message.type());
            send(out, new ErrorMessage("Unexpected message type from client: " + message.type()));
        }
    }

    private void send(OutputStream out, ProtocolMessage message) throws IOException {
        out.write(codec.encode(message).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
