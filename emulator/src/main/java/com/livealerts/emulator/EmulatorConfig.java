package com.livealerts.emulator;

import java.util.UUID;

/** Runtime configuration, read from environment variables (matches docker-compose.yml). */
public record EmulatorConfig(
        String serverHost, int serverPort, String clientId, long intervalMs, int controlPort, int clearScreenEvery) {

    static EmulatorConfig fromEnv() {
        return new EmulatorConfig(
                env("SERVER_HOST", "localhost"),
                Integer.parseInt(env("SERVER_TCP_PORT", "5000")),
                env("EMULATOR_CLIENT_ID", "emulator-" + UUID.randomUUID().toString().substring(0, 8)),
                Long.parseLong(env("EMULATOR_INTERVAL_MS", "10000")),
                Integer.parseInt(env("EMULATOR_CONTROL_PORT", "9000")),
                Integer.parseInt(env("EMULATOR_CLEAR_SCREEN_EVERY", "5")));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
