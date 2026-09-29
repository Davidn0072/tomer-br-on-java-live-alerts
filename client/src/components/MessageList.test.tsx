import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MessageList } from './MessageList';

describe('MessageList', () => {
  it('shows an empty state when there are no messages', () => {
    render(<MessageList messages={[]} />);

    expect(screen.getByText(/no messages yet/i)).toBeInTheDocument();
  });

  it('renders each message with its client and text', () => {
    render(
      <MessageList
        messages={[
          { id: 1, clientId: 'emulator-1', text: 'hello', receivedAt: '2026-01-01T00:00:00Z' },
          { id: 2, clientId: 'emulator-2', text: 'world', receivedAt: '2026-01-01T00:01:00Z' },
        ]}
      />,
    );

    expect(screen.getByText('hello')).toBeInTheDocument();
    expect(screen.getByText('world')).toBeInTheDocument();
    expect(screen.getByText('emulator-1')).toBeInTheDocument();
    expect(screen.getByText('emulator-2')).toBeInTheDocument();
  });
});
