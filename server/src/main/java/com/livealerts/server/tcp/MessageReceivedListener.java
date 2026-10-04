package com.livealerts.server.tcp;

import com.livealerts.server.protocol.ProtocolMessage;

/**
 * Notified whenever a client's {@code SendMessage} or {@code ClearScreen} is accepted (the two
 * message types that get persisted — {@code Connect}/{@code Disconnect} don't). Persistence
 * hooks in here.
 */
@FunctionalInterface
public interface MessageReceivedListener {

    void onMessageReceived(ProtocolMessage message);
}
