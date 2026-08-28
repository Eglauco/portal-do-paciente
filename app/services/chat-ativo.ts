/**
 * Guarda qual conversa o paciente tem aberta no momento.
 * Usado para NÃO exibir a notificação de "nova mensagem" quando ele já está
 * exatamente naquela conversa (as mensagens chegam ao vivo pelo WebSocket).
 */
let chatAtivo: string | null = null;

export function setChatAtivo(id: string | number): void {
  chatAtivo = String(id);
}

export function limparChatAtivo(id: string | number): void {
  if (chatAtivo === String(id)) {
    chatAtivo = null;
  }
}

export function ehChatAtivo(id: string | number): boolean {
  return chatAtivo === String(id);
}
