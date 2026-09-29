package com.livealerts.emulator;

import com.livealerts.emulator.protocol.MalformedMessageException;
import com.livealerts.emulator.protocol.MessageCodec;
import com.livealerts.emulator.protocol.ProtocolMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** One open TCP connection to the server: thread-safe send, and a blocking read loop for replies. */
final class Connection {

    private final Socket socket;
    private final MessageCodec codec;
    private final BufferedReader reader;
    private final OutputStream out;

    private Connection(Socket socket, MessageCodec codec, BufferedReader reader, OutputStream out) {
        this.socket = socket;
        this.codec = codec;
        this.reader = reader;
        this.out = out;
    }

    static Connection open(String host, int port, MessageCodec codec) throws IOException {
        Socket socket = new Socket(host, port);
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = socket.getOutputStream();
        return new Connection(socket, codec, reader, out);
    }

    synchronized void send(ProtocolMessage message) {
        try {
            out.write(codec.encode(message).getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            Log.warn("Failed to send " + message.type() + ": " + e.getMessage());
        }
    }

    /** Blocks, logging every reply, until the server closes the connection or an I/O error occurs. */
    void readUntilClosed() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            try {
                ProtocolMessage response = codec.decode(line);
                Log.info("Server replied: " + response);
            } catch (MalformedMessageException e) {
                Log.warn("Malformed line from server: " + e.getMessage());
            }
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // already closing
        }
    }
}
