import type { StoredMessage } from '../types';

interface MessageListProps {
  messages: StoredMessage[];
}

export function MessageList({ messages }: MessageListProps) {
  if (messages.length === 0) {
    return <p className="empty-state">No messages yet.</p>;
  }

  return (
    <ul className="message-list">
      {messages.map((message) => (
        <li key={message.id} className="message-item">
          <span className="message-client">{message.clientId}</span>
          <span className="message-text">{message.text}</span>
          <time className="message-time" dateTime={message.receivedAt}>
            {new Date(message.receivedAt).toLocaleString()}
          </time>
        </li>
      ))}
    </ul>
  );
}
