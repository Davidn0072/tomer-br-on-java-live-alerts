package com.livealerts.server.api;

import com.livealerts.server.protocol.ClearScreenMessage;
import com.livealerts.server.protocol.SendMessageMessage;
import com.livealerts.server.storage.MessageRepository;
import com.livealerts.server.storage.StoredMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageRepository repository;

    @Test
    void listMessagesReturnsStoredMessagesNewestFirst() throws Exception {
        StoredMessage newer = messageWithId(2L, "emulator-1", "newer", Instant.parse("2026-01-01T00:00:10Z"));
        StoredMessage older = messageWithId(1L, "emulator-1", "older", Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findAllByOrderByReceivedAtDesc()).thenReturn(List.of(newer, older));

        mockMvc.perform(get("/api/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].text").value("newer"))
                .andExpect(jsonPath("$[1].id").value(1))
                .andExpect(jsonPath("$[1].text").value("older"));
    }

    @Test
    void listMessagesReturnsEmptyArrayWhenNoneStored() throws Exception {
        when(repository.findAllByOrderByReceivedAtDesc()).thenReturn(List.of());

        mockMvc.perform(get("/api/messages"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getMessageReturnsItWhenFound() throws Exception {
        StoredMessage message = messageWithId(42L, "emulator-1", "hello", Instant.parse("2026-01-01T00:00:00Z"));
        when(repository.findById(42L)).thenReturn(Optional.of(message));

        mockMvc.perform(get("/api/messages/{id}", 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.clientId").value("emulator-1"))
                .andExpect(jsonPath("$.text").value("hello"))
                .andExpect(jsonPath("$.type").value(SendMessageMessage.TYPE));
    }

    @Test
    void getMessageReturns404WhenNotFound() throws Exception {
        when(repository.findById(eq(404L))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/messages/{id}", 404))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMessageReturnsClearScreenTypeWithNoTextWhenFound() throws Exception {
        StoredMessage message = mock(StoredMessage.class);
        when(message.getId()).thenReturn(7L);
        when(message.getClientId()).thenReturn("emulator-1");
        when(message.getText()).thenReturn(null);
        when(message.getReceivedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(message.getType()).thenReturn(ClearScreenMessage.TYPE);
        when(repository.findById(7L)).thenReturn(Optional.of(message));

        mockMvc.perform(get("/api/messages/{id}", 7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.type").value(ClearScreenMessage.TYPE));
    }

    private static StoredMessage messageWithId(long id, String clientId, String text, Instant receivedAt) {
        StoredMessage message = mock(StoredMessage.class);
        when(message.getId()).thenReturn(id);
        when(message.getClientId()).thenReturn(clientId);
        when(message.getText()).thenReturn(text);
        when(message.getReceivedAt()).thenReturn(receivedAt);
        when(message.getType()).thenReturn(SendMessageMessage.TYPE);
        return message;
    }
}
