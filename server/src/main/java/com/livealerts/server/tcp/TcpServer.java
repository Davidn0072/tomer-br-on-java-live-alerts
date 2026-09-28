package com.livealerts.server.tcp;

import com.livealerts.server.protocol.MessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Accepts TCP connections and hands each one to its own {@link ClientHandler} thread, so one
 * misbehaving or slow client never blocks another. Started/stopped by Spring's lifecycle
 * ({@link SmartLifecycle}); also directly instantiable (bypassing Spring) for tests.
 */
@Component
public class TcpServer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(TcpServer.class);

    private final int configuredPort;
    private final MessageCodec codec;
    private final MessageReceivedListener listener;

    private ServerSocket serverSocket;
    private ExecutorService connectionExecutor;
    private Thread acceptThread;
    private volatile boolean running = false;

    public TcpServer(@Value("${app.tcp.port}") int configuredPort,
                      MessageCodec codec,
                      MessageReceivedListener listener) {
        this.configuredPort = configuredPort;
        this.codec = codec;
        this.listener = listener;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        try {
            serverSocket = new ServerSocket(configuredPort);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to bind TCP server on port " + configuredPort, e);
        }
        connectionExecutor = Executors.newCachedThreadPool();
        running = true;
        acceptThread = new Thread(this::acceptLoop, "tcp-accept");
        acceptThread.start();
        log.info("TCP server listening on port {}", serverSocket.getLocalPort());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                connectionExecutor.submit(new ClientHandler(socket, codec, listener));
            } catch (IOException e) {
                if (running) {
                    log.warn("Error accepting TCP connection", e);
                }
                // else: socket closed by stop() — expected, let the loop exit.
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.warn("Error closing TCP server socket", e);
        }
        connectionExecutor.shutdownNow();
        try {
            acceptThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("TCP server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** The actual bound port — useful in tests that start on port 0 (an OS-assigned free port). */
    public int getPort() {
        if (serverSocket == null) {
            throw new IllegalStateException("TCP server is not started");
        }
        return serverSocket.getLocalPort();
    }
}
