import { useEffect } from 'react';
import type { StoredMessage } from '../types';

interface AlertToastProps {
  message: StoredMessage;
  onDismiss: () => void;
}

const AUTO_DISMISS_MS = 6000;

export function AlertToast({ message, onDismiss }: AlertToastProps) {
  useEffect(() => {
    const timer = setTimeout(onDismiss, AUTO_DISMISS_MS);
    return () => clearTimeout(timer);
  }, [message.id, onDismiss]);

  return (
    <div className="alert-toast" role="alert">
      {message.type === 'ClearScreen' ? (
        <strong>Screen was cleared</strong>
      ) : (
        <>
          <strong>New message from {message.clientId}</strong>
          <p>{message.text}</p>
        </>
      )}
      <button type="button" onClick={onDismiss} aria-label="Dismiss">
        ×
      </button>
    </div>
  );
}
