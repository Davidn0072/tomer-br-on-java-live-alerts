import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchMessageById, fetchMessages } from './messages';

describe('messages API client', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('fetchMessages returns the parsed JSON body on success', async () => {
    const payload = [{ id: 1, clientId: 'emulator-1', text: 'hi', receivedAt: '2026-01-01T00:00:00Z' }];
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(payload) }),
    );

    await expect(fetchMessages()).resolves.toEqual(payload);
    expect(fetch).toHaveBeenCalledWith('/api/messages');
  });

  it('fetchMessages throws when the response is not ok', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }));

    await expect(fetchMessages()).rejects.toThrow('500');
  });

  it('fetchMessageById requests the specific message by id', async () => {
    const payload = { id: 42, clientId: 'emulator-1', text: 'hi', receivedAt: '2026-01-01T00:00:00Z' };
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: () => Promise.resolve(payload) }),
    );

    await expect(fetchMessageById(42)).resolves.toEqual(payload);
    expect(fetch).toHaveBeenCalledWith('/api/messages/42');
  });

  it('fetchMessageById throws on a 404', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }));

    await expect(fetchMessageById(404)).rejects.toThrow('404');
  });
});
