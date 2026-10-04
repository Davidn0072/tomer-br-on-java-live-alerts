export interface StoredMessage {
  id: number;
  clientId: string;
  text: string | null;
  receivedAt: string;
  type: 'SendMessage' | 'ClearScreen';
}

export interface NewMessageAlert {
  type: 'NewMessage';
  id: number;
  receivedAt: string;
}
