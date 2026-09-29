import type { StoredMessage } from '../types';

export async function fetchMessages(): Promise<StoredMessage[]> {
  const response = await fetch('/api/messages');
  if (!response.ok) {
    throw new Error(`Failed to fetch messages: ${response.status}`);
  }
  return response.json();
}

export async function fetchMessageById(id: number): Promise<StoredMessage> {
  const response = await fetch(`/api/messages/${id}`);
  if (!response.ok) {
    throw new Error(`Failed to fetch message ${id}: ${response.status}`);
  }
  return response.json();
}
