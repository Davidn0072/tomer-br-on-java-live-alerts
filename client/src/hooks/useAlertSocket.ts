import { useEffect, useRef, useState } from 'react';
import type { NewMessageAlert } from '../types';

export type ConnectionStatus = 'connecting' | 'open' | 'reconnecting';

const RECONNECT_DELAY_MS = 3000;

function buildWebSocketUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws/alerts`;
}

/**
 * Connects to the server's alert WebSocket and calls `onAlert` for every message it pushes.
 * Reconnects on its own after any drop (server restart, network blip) with a fixed delay — the
 * browser never needs a manual refresh, per the exercise's "recover from restarts" requirement.
 */
export function useAlertSocket(onAlert: (alert: NewMessageAlert) => void): ConnectionStatus {
  const [status, setStatus] = useState<ConnectionStatus>('connecting');
  const onAlertRef = useRef(onAlert);
  onAlertRef.current = onAlert;

  useEffect(() => {
    let socket: WebSocket | null = null;
    let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
    let stopped = false;

    function connect(isReconnect: boolean) {
      if (!isReconnect) {
        setStatus('connecting');
      }
      socket = new WebSocket(buildWebSocketUrl());

      socket.onopen = () => {
        setStatus('open');
      };

      socket.onmessage = (event) => {
        try {
          onAlertRef.current(JSON.parse(event.data));
        } catch {
          // The server only ever sends NewMessage alerts, but don't let an unexpected frame
          // crash the UI.
        }
      };

      socket.onclose = () => {
        if (stopped) return;
        setStatus('reconnecting');
        reconnectTimer = setTimeout(() => connect(true), RECONNECT_DELAY_MS);
      };

      socket.onerror = () => {
        socket?.close();
      };
    }

    connect(false);

    return () => {
      stopped = true;
      clearTimeout(reconnectTimer);
      socket?.close();
    };
  }, []);

  return status;
}
