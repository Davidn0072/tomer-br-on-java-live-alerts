package com.livealerts.server.storage;

/** Notified whenever a message finishes persisting. The WebSocket broadcaster hooks in here. */
@FunctionalInterface
public interface MessagePersistedListener {

    void onMessagePersisted(StoredMessage message);
}
