package com.livealerts.server.storage;

import com.livealerts.server.protocol.ClearScreenMessage;
import com.livealerts.server.protocol.ProtocolMessage;
import com.livealerts.server.protocol.SendMessageMessage;
import com.livealerts.server.tcp.MessageReceivedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Bridges the TCP layer to persistence: every accepted {@code SendMessage} or {@code
 * ClearScreen} is stored in MSSQL as a {@link StoredMessage} row.
 */
@Component
class PersistingMessageReceivedListener implements MessageReceivedListener {

    private static final Logger log = LoggerFactory.getLogger(PersistingMessageReceivedListener.class);

    private final MessageRepository repository;
    private final MessagePersistedListener persistedListener;

    PersistingMessageReceivedListener(MessageRepository repository, MessagePersistedListener persistedListener) {
        this.repository = repository;
        this.persistedListener = persistedListener;
    }

    @Override
    public void onMessageReceived(ProtocolMessage message) {
        StoredMessage saved = repository.save(toStoredMessage(message));
        log.debug("Persisted message id={} from {}", saved.getId(), saved.getClientId());
        persistedListener.onMessagePersisted(saved);
    }

    private StoredMessage toStoredMessage(ProtocolMessage message) {
        if (message instanceof SendMessageMessage sendMessage) {
            return new StoredMessage(sendMessage.clientId(), sendMessage.text(), Instant.now());
        }
        if (message instanceof ClearScreenMessage clearScreen) {
            return new StoredMessage(clearScreen.clientId(), null, Instant.now(), ClearScreenMessage.TYPE);
        }
        throw new IllegalArgumentException("Unsupported message type for persistence: " + message.type());
    }
}
