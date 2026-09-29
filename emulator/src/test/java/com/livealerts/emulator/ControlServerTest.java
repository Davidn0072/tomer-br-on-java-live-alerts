package com.livealerts.emulator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ControlServerTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private ControlServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void postTriggerRunsTheCallbackAndReturns200() throws Exception {
        AtomicInteger triggerCount = new AtomicInteger();
        server = new ControlServer(0, triggerCount::incrementAndGet);
        server.start();

        HttpResponse<String> response = post("/trigger");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("triggered");
        assertThat(triggerCount.get()).isEqualTo(1);
    }

    @Test
    void getTriggerIsRejectedAndDoesNotRunTheCallback() throws Exception {
        AtomicInteger triggerCount = new AtomicInteger();
        server = new ControlServer(0, triggerCount::incrementAndGet);
        server.start();

        HttpResponse<String> response = get("/trigger");

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(triggerCount.get()).isZero();
    }

    @Test
    void healthReturnsUp() throws Exception {
        server = new ControlServer(0, () -> { });
        server.start();

        HttpResponse<String> response = get("/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
