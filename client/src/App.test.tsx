import { act, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

class FakeWebSocket {
  static instances: FakeWebSocket[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;

  constructor(public url: string) {
    FakeWebSocket.instances.push(this);
  }

  close() {}
}

function stubFetchResponses(responsesByUrl: Record<string, unknown>) {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => Promise.resolve({ ok: true, json: () => Promise.resolve(responsesByUrl[url]) })),
  );
}

describe('App', () => {
  beforeEach(() => {
    FakeWebSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('on initial load, only shows messages after the most recent ClearScreen', async () => {
    stubFetchResponses({
      '/api/messages': [
        { id: 3, clientId: 'emulator-1', text: 'after clear', receivedAt: '2026-01-01T00:02:00Z', type: 'SendMessage' },
        { id: 2, clientId: 'emulator-1', text: null, receivedAt: '2026-01-01T00:01:00Z', type: 'ClearScreen' },
        { id: 1, clientId: 'emulator-1', text: 'before clear', receivedAt: '2026-01-01T00:00:00Z', type: 'SendMessage' },
      ],
    });

    render(<App />);

    await waitFor(() => expect(screen.getByText('after clear')).toBeInTheDocument());
    expect(screen.queryByText('before clear')).not.toBeInTheDocument();
  });

  it('clears the message list and toasts when a ClearScreen alert arrives live', async () => {
    stubFetchResponses({
      '/api/messages': [
        { id: 1, clientId: 'emulator-1', text: 'hello', receivedAt: '2026-01-01T00:00:00Z', type: 'SendMessage' },
      ],
      '/api/messages/2': {
        id: 2,
        clientId: 'emulator-1',
        text: null,
        receivedAt: '2026-01-01T00:01:00Z',
        type: 'ClearScreen',
      },
    });

    render(<App />);
    await waitFor(() => expect(screen.getByText('hello')).toBeInTheDocument());

    act(() => {
      FakeWebSocket.instances[0].onmessage?.({
        data: '{"type":"NewMessage","id":2,"receivedAt":"2026-01-01T00:01:00Z"}',
      });
    });

    await waitFor(() => expect(screen.getByText(/no messages yet/i)).toBeInTheDocument());
    expect(screen.getByText(/screen was cleared/i)).toBeInTheDocument();
  });

  it('still prepends a normal SendMessage alert without clearing the list', async () => {
    stubFetchResponses({
      '/api/messages': [
        { id: 1, clientId: 'emulator-1', text: 'first', receivedAt: '2026-01-01T00:00:00Z', type: 'SendMessage' },
      ],
      '/api/messages/2': {
        id: 2,
        clientId: 'emulator-1',
        text: 'second',
        receivedAt: '2026-01-01T00:01:00Z',
        type: 'SendMessage',
      },
    });

    render(<App />);
    await waitFor(() => expect(screen.getByText('first')).toBeInTheDocument());

    act(() => {
      FakeWebSocket.instances[0].onmessage?.({
        data: '{"type":"NewMessage","id":2,"receivedAt":"2026-01-01T00:01:00Z"}',
      });
    });

    const list = screen.getByRole('list');
    await waitFor(() => expect(within(list).getByText('second')).toBeInTheDocument());
    expect(within(list).getByText('first')).toBeInTheDocument();
  });
});
