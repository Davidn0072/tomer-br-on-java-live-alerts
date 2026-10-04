package com.livealerts.server.storage;

import com.livealerts.server.protocol.ClearScreenMessage;
import com.livealerts.server.protocol.SendMessageMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistingMessageReceivedListenerTest {

    private final MessageRepository repository = mock(MessageRepository.class);
    private final MessagePersistedListener persistedListener = mock(MessagePersistedListener.class);
    private final PersistingMessageReceivedListener listener =
            new PersistingMessageReceivedListener(repository, persistedListener);

    @Test
    void savesTheMessageAndNotifiesThePersistedListenerWithTheSavedEntity() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onMessageReceived(new SendMessageMessage("emulator-1", "hello"));

        ArgumentCaptor<StoredMessage> captor = ArgumentCaptor.forClass(StoredMessage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo("emulator-1");
        assertThat(captor.getValue().getText()).isEqualTo("hello");

        verify(persistedListener).onMessagePersisted(captor.getValue());
    }

    @Test
    void savesAClearScreenMessageWithNoTextAndTheClearScreenType() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        listener.onMessageReceived(new ClearScreenMessage("emulator-1"));

        ArgumentCaptor<StoredMessage> captor = ArgumentCaptor.forClass(StoredMessage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo("emulator-1");
        assertThat(captor.getValue().getText()).isNull();
        assertThat(captor.getValue().getType()).isEqualTo(ClearScreenMessage.TYPE);

        verify(persistedListener).onMessagePersisted(captor.getValue());
    }
}
