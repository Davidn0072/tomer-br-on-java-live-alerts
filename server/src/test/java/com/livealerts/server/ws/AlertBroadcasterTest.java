package com.livealerts.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.livealerts.server.storage.StoredMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertBroadcasterTest {

    private final AlertBroadcaster broadcaster =
            new AlertBroadcaster(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void broadcastsAnAlertWithNoMessageBodyToEveryOpenSession() throws IOException {
        WebSocketSession sessionA = openSession("a");
        WebSocketSession sessionB = openSession("b");
        broadcaster.afterConnectionEstablished(sessionA);
        broadcaster.afterConnectionEstablished(sessionB);

        broadcaster.onMessagePersisted(storedMessage(1L, Instant.parse("2026-01-01T00:00:00Z")));

        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(sessionA).sendMessage(captor.capture());
        verify(sessionB).sendMessage(any(TextMessage.class));

        String payload = captor.getValue().getPayload();
        assertThat(payload).contains("\"type\":\"NewMessage\"").contains("\"id\":1");
        assertThat(payload).doesNotContain("clientId").doesNotContain("text");
    }

    @Test
    void doesNotSendToAClosedSession() throws IOException {
        WebSocketSession closed = mock(WebSocketSession.class);
        when(closed.isOpen()).thenReturn(false);
        broadcaster.afterConnectionEstablished(closed);

        broadcaster.onMessagePersisted(storedMessage(1L, Instant.now()));

        verify(closed, never()).sendMessage(any());
    }

    @Test
    void oneFailingSessionDoesNotStopTheBroadcastToOthers() throws IOException {
        WebSocketSession failing = openSession("failing");
        doThrow(new IOException("boom")).when(failing).sendMessage(any());
        WebSocketSession healthy = openSession("healthy");
        broadcaster.afterConnectionEstablished(failing);
        broadcaster.afterConnectionEstablished(healthy);

        broadcaster.onMessagePersisted(storedMessage(1L, Instant.now()));

        verify(healthy).sendMessage(any(TextMessage.class));
    }

    @Test
    void aClosedConnectionStopsReceivingFutureBroadcasts() throws IOException {
        WebSocketSession session = openSession("a");
        broadcaster.afterConnectionEstablished(session);
        broadcaster.afterConnectionClosed(session, CloseStatus.NORMAL);

        broadcaster.onMessagePersisted(storedMessage(1L, Instant.now()));

        verify(session, never()).sendMessage(any());
    }

    private static WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private static StoredMessage storedMessage(long id, Instant receivedAt) {
        StoredMessage message = mock(StoredMessage.class);
        when(message.getId()).thenReturn(id);
        when(message.getReceivedAt()).thenReturn(receivedAt);
        return message;
    }
}
