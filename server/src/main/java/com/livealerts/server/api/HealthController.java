package com.livealerts.server.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Trivial liveness check for the docker-compose healthcheck and E2E readiness waits. */
@RestController
class HealthController {

    @GetMapping("/api/health")
    Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
