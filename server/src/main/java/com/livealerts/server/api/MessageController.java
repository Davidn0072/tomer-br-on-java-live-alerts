package com.livealerts.server.api;

import com.livealerts.server.storage.MessageRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only REST API the client uses to fetch message data after a WebSocket alert — the
 * client never talks to MSSQL directly.
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageRepository repository;

    public MessageController(MessageRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<MessageResponse> listMessages() {
        return repository.findAllByOrderByReceivedAtDesc().stream()
                .map(MessageResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MessageResponse> getMessage(@PathVariable Long id) {
        return repository.findById(id)
                .map(MessageResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
