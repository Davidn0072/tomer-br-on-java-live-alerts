package com.livealerts.server.tcp;

import com.livealerts.server.protocol.SendMessageMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default listener until persistence exists (see PLAN.md §3 — MSSQL storage is the next piece
 * to wire in here).
 */
@Component
class LoggingMessageReceivedListener implements MessageReceivedListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessageReceivedListener.class);

    @Override
    public void onMessageReceived(SendMessageMessage message) {
        log.info("Received SendMessage from {}: {}", message.clientId(), message.text());
    }
}
