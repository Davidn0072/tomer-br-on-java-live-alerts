package com.livealerts.emulator;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTP control surface — no framework, just the JDK's built-in {@link HttpServer} — so
 * the manual "send on demand" trigger (and Playwright E2E) has something to call from outside
 * the container: {@code POST /trigger} sends one message immediately.
 */
final class ControlServer {

    private final int port;
    private final Runnable onTrigger;
    private HttpServer server;

    ControlServer(int port, Runnable onTrigger) {
        this.port = port;
        this.onTrigger = onTrigger;
    }

    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/trigger", this::handleTrigger);
        server.createContext("/health", this::handleHealth);
        server.setExecutor(null);
        server.start();
        Log.info("Control server listening on port " + getPort());
    }

    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** The actual bound port — useful in tests that start on port 0 (an OS-assigned free port). */
    int getPort() {
        return server.getAddress().getPort();
    }

    private void handleTrigger(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        onTrigger.run();
        respondJson(exchange, 200, "{\"status\":\"triggered\"}");
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        respondJson(exchange, 200, "{\"status\":\"UP\"}");
    }

    private void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
