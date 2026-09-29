package com.livealerts.emulator;

import com.livealerts.emulator.protocol.ConnectMessage;
import com.livealerts.emulator.protocol.DisconnectMessage;
import com.livealerts.emulator.protocol.MessageCodec;
import com.livealerts.emulator.protocol.SendMessageMessage;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the TCP connection to the server on a dedicated thread: connects, sends {@code Connect}
 * on every (re)connect, and reconnects with a fixed backoff whenever the connection drops for
 * any reason (server restart, network blip) — with no manual step required, per the exercise's
 * "recover from restarts" requirement. Callers just call {@link #sendMessage(String)}; if
 * nothing is connected right now, the message is dropped and logged rather than queued — the
 * next scheduled or manual trigger will succeed once reconnected.
 */
final class ConnectionManager {

    private static final long RECONNECT_DELAY_MS = 3000;

    private final String host;
    private final int port;
    private final String clientId;
    private final MessageCodec codec = new MessageCodec();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Connection> current = new AtomicReference<>();
    private Thread connectionThread;

    ConnectionManager(String host, int port, String clientId) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
    }

    void start() {
        running.set(true);
        connectionThread = new Thread(this::connectionLoop, "connection-loop");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    void stop() {
        running.set(false);
        Connection connection = current.getAndSet(null);
        if (connection != null) {
            connection.send(new DisconnectMessage(clientId, "shutting down"));
            connection.close();
        }
        connectionThread.interrupt();
        try {
            connectionThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void sendMessage(String text) {
        Connection connection = current.get();
        if (connection == null) {
            Log.warn("Not connected, dropping message: " + text);
            return;
        }
        connection.send(new SendMessageMessage(clientId, text));
    }

    private void connectionLoop() {
        while (running.get()) {
            try {
                Connection connection = Connection.open(host, port, codec);
                current.set(connection);
                Log.info("Connected to " + host + ":" + port + " as " + clientId);
                connection.send(new ConnectMessage(clientId));
                connection.readUntilClosed();
            } catch (IOException e) {
                Log.warn("Connection error: " + e.getMessage());
            } finally {
                current.set(null);
            }
            if (running.get()) {
                sleep(RECONNECT_DELAY_MS);
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
