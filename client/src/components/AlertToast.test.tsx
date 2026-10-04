import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AlertToast } from './AlertToast';

const message = {
  id: 1,
  clientId: 'emulator-1',
  text: 'hello',
  receivedAt: '2026-01-01T00:00:00Z',
  type: 'SendMessage' as const,
};

const clearScreenMessage = {
  id: 2,
  clientId: 'emulator-1',
  text: null,
  receivedAt: '2026-01-01T00:00:00Z',
  type: 'ClearScreen' as const,
};

describe('AlertToast', () => {
  it('shows the message content', () => {
    render(<AlertToast message={message} onDismiss={() => {}} />);

    expect(screen.getByText(/new message from emulator-1/i)).toBeInTheDocument();
    expect(screen.getByText('hello')).toBeInTheDocument();
  });

  it('shows "Screen was cleared" for a ClearScreen message instead of the normal content', () => {
    render(<AlertToast message={clearScreenMessage} onDismiss={() => {}} />);

    expect(screen.getByText(/screen was cleared/i)).toBeInTheDocument();
    expect(screen.queryByText(/new message from/i)).not.toBeInTheDocument();
  });

  it('calls onDismiss when the close button is clicked', async () => {
    const user = userEvent.setup();
    const onDismiss = vi.fn();
    render(<AlertToast message={message} onDismiss={onDismiss} />);

    await user.click(screen.getByRole('button', { name: /dismiss/i }));

    expect(onDismiss).toHaveBeenCalledTimes(1);
  });

  describe('auto-dismiss', () => {
    afterEach(() => {
      vi.useRealTimers();
    });

    it('calls onDismiss automatically after the auto-dismiss delay', () => {
      vi.useFakeTimers();
      const onDismiss = vi.fn();
      render(<AlertToast message={message} onDismiss={onDismiss} />);

      act(() => {
        vi.advanceTimersByTime(6000);
      });

      expect(onDismiss).toHaveBeenCalledTimes(1);
    });
  });
});
