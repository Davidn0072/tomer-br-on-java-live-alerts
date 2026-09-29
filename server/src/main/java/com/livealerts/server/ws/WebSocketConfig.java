package com.livealerts.server.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers {@link AlertBroadcaster} at {@code /ws/alerts}. Origins are left open ({@code *})
 * since the reverse-proxy setup that will front this in docker-compose isn't decided yet
 * (PLAN.md §8) — tighten once it is.
 */
@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {

    private final AlertBroadcaster alertBroadcaster;

    WebSocketConfig(AlertBroadcaster alertBroadcaster) {
        this.alertBroadcaster = alertBroadcaster;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertBroadcaster, "/ws/alerts").setAllowedOrigins("*");
    }
}
