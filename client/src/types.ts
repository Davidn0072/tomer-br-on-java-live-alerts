export interface StoredMessage {
  id: number;
  clientId: string;
  text: string;
  receivedAt: string;
}

export interface NewMessageAlert {
  type: 'NewMessage';
  id: number;
  receivedAt: string;
}
