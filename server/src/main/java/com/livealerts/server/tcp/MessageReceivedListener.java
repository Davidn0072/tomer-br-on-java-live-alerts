package com.livealerts.server.tcp;

import com.livealerts.server.protocol.SendMessageMessage;

/** Notified whenever a client's {@code SendMessage} is accepted. Persistence hooks in here. */
@FunctionalInterface
public interface MessageReceivedListener {

    void onMessageReceived(SendMessageMessage message);
}
