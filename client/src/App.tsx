import { useCallback, useEffect, useState } from 'react';
import { fetchMessageById, fetchMessages } from './api/messages';
import { AlertToast } from './components/AlertToast';
import { ConnectionStatus } from './components/ConnectionStatus';
import { MessageList } from './components/MessageList';
import { useAlertSocket } from './hooks/useAlertSocket';
import type { NewMessageAlert, StoredMessage } from './types';

/**
 * `messages` is newest-first. On a fresh load, only the SendMessage rows after the most recent
 * ClearScreen should render, so a page reload shows the same thing a tab left open would —
 * see CLEAR_SCREEN_FEATURE.md's confirmed refresh semantics.
 */
function visibleAfterLastClearScreen(messages: StoredMessage[]): StoredMessage[] {
  const lastClearIndex = messages.findIndex((message) => message.type === 'ClearScreen');
  const afterClear = lastClearIndex === -1 ? messages : messages.slice(0, lastClearIndex);
  return afterClear.filter((message) => message.type !== 'ClearScreen');
}

export default function App() {
  const [messages, setMessages] = useState<StoredMessage[]>([]);
  const [activeAlert, setActiveAlert] = useState<StoredMessage | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    fetchMessages()
      .then((all) => setMessages(visibleAfterLastClearScreen(all)))
      .catch(() => setLoadError('Could not load messages.'));
  }, []);

  const handleAlert = useCallback((alert: NewMessageAlert) => {
    fetchMessageById(alert.id)
      .then((message) => {
        if (message.type === 'ClearScreen') {
          setMessages([]);
        } else {
          setMessages((current) => (current.some((m) => m.id === message.id) ? current : [message, ...current]));
        }
        setActiveAlert(message);
      })
      .catch(() => {
        // The WS push was only ever a nudge; a failed follow-up fetch isn't fatal.
      });
  }, []);

  const status = useAlertSocket(handleAlert);

  return (
    <main className="app">
      <header className="app-header">
        <h1>Live Alerts</h1>
        <ConnectionStatus status={status} />
      </header>

      {activeAlert && <AlertToast message={activeAlert} onDismiss={() => setActiveAlert(null)} />}

      {loadError && <p className="error-banner">{loadError}</p>}

      <section>
        <h2>Messages</h2>
        <MessageList messages={messages} />
      </section>
    </main>
  );
}
