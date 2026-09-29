import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAlertSocket } from './useAlertSocket';

class FakeWebSocket {
  static instances: FakeWebSocket[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(public url: string) {
    FakeWebSocket.instances.push(this);
  }

  close() {
    this.closed = true;
    this.onclose?.();
  }
}

describe('useAlertSocket', () => {
  beforeEach(() => {
    FakeWebSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('starts in connecting and reports open once the socket connects', () => {
    const { result } = renderHook(() => useAlertSocket(() => {}));

    expect(result.current).toBe('connecting');

    act(() => {
      FakeWebSocket.instances[0].onopen?.();
    });

    expect(result.current).toBe('open');
  });

  it('parses incoming frames and forwards them to onAlert', () => {
    const onAlert = vi.fn();
    renderHook(() => useAlertSocket(onAlert));

    act(() => {
      FakeWebSocket.instances[0].onmessage?.({
        data: '{"type":"NewMessage","id":1,"receivedAt":"2026-01-01T00:00:00Z"}',
      });
    });

    expect(onAlert).toHaveBeenCalledWith({ type: 'NewMessage', id: 1, receivedAt: '2026-01-01T00:00:00Z' });
  });

  it('ignores a malformed frame instead of throwing', () => {
    const onAlert = vi.fn();
    renderHook(() => useAlertSocket(onAlert));

    expect(() => {
      act(() => {
        FakeWebSocket.instances[0].onmessage?.({ data: 'not json' });
      });
    }).not.toThrow();
    expect(onAlert).not.toHaveBeenCalled();
  });

  it('reconnects automatically after the connection drops', () => {
    const { result } = renderHook(() => useAlertSocket(() => {}));

    act(() => {
      FakeWebSocket.instances[0].onopen?.();
    });
    expect(result.current).toBe('open');

    act(() => {
      FakeWebSocket.instances[0].onclose?.();
    });
    expect(FakeWebSocket.instances).toHaveLength(1);
    expect(result.current).toBe('reconnecting');

    act(() => {
      vi.advanceTimersByTime(3000);
    });

    expect(FakeWebSocket.instances).toHaveLength(2);
  });

  it('closes the socket on unmount and does not schedule a reconnect', () => {
    const { unmount } = renderHook(() => useAlertSocket(() => {}));
    const socket = FakeWebSocket.instances[0];

    unmount();

    expect(socket.closed).toBe(true);

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(FakeWebSocket.instances).toHaveLength(1);
  });
});
