package com.livealerts.server.storage;

import com.livealerts.server.protocol.SendMessageMessage;
import com.livealerts.server.tcp.MessageReceivedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Bridges the TCP layer to persistence: every accepted {@code SendMessage} is stored in MSSQL. */
@Component
class PersistingMessageReceivedListener implements MessageReceivedListener {

    private static final Logger log = LoggerFactory.getLogger(PersistingMessageReceivedListener.class);

    private final MessageRepository repository;

    PersistingMessageReceivedListener(MessageRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onMessageReceived(SendMessageMessage message) {
        StoredMessage saved = repository.save(
                new StoredMessage(message.clientId(), message.text(), Instant.now()));
        log.debug("Persisted message id={} from {}", saved.getId(), saved.getClientId());
    }
}
