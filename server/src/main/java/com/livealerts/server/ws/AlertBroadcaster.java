package com.livealerts.server.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.livealerts.server.storage.MessagePersistedListener;
import com.livealerts.server.storage.StoredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks connected browser WebSocket sessions and pushes a lightweight {@link AlertMessage}
 * whenever a message is persisted. A session that fails to send (closed, network drop) is
 * dropped and never stops the broadcast reaching every other session.
 */
@Component
public class AlertBroadcaster extends TextWebSocketHandler implements MessagePersistedListener {

    private static final Logger log = LoggerFactory.getLogger(AlertBroadcaster.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper;

    public AlertBroadcaster(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Browser connected: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Browser disconnected: {} ({})", session.getId(), status);
    }

    @Override
    public void onMessagePersisted(StoredMessage message) {
        TextMessage payload = new TextMessage(encode(message));
        for (WebSocketSession session : sessions) {
            sendQuietly(session, payload);
        }
    }

    private String encode(StoredMessage message) {
        try {
            return mapper.writeValueAsString(new AlertMessage(message.getId(), message.getReceivedAt()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode alert for message id=" + message.getId(), e);
        }
    }

    private void sendQuietly(WebSocketSession session, TextMessage payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(payload);
        } catch (IOException e) {
            log.warn("Failed to send alert to session {}, dropping it", session.getId(), e);
            sessions.remove(session);
        }
    }
}
