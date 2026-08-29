import { fetchMeu } from '@/services/sessao';

export type StatusChat = 'NAO_LIDA' | 'AGUARDANDO_RESPOSTA' | 'EM_ATENDIMENTO' | 'RESOLVIDO';
export type Remetente = 'PACIENTE' | 'UNIDADE';

interface Ref {
  id: number;
  nome: string;
}

/** Item da lista de conversas. */
export interface ChatItem {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  status: StatusChat;
  statusDescricao: string;
  ultimaMensagem: string | null;
  ultimaMensagemDe: Remetente | null;
  ultimaMensagemEm: string | null;
  naoLidas: number;
  atualizadoEm: string;
}

export interface Mensagem {
  id: number;
  remetente: Remetente;
  texto: string;
  enviadaEm: string;
  lida: boolean;
  entregue: boolean;
  /** Só no cliente: mensagem otimista ainda não confirmada pelo servidor (mostra o relógio). */
  pendente?: boolean;
}

export interface ChatDetalhe {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  status: StatusChat;
  statusDescricao: string;
  mensagens: Mensagem[];
}

interface Pagina<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

async function comoJson<T>(resposta: Response): Promise<T> {
  if (!resposta.ok) {
    throw new Error(`Falha na requisição (${resposta.status})`);
  }
  return resposta.json() as Promise<T>;
}

/** Lista as conversas do paciente logado (mais recentes primeiro). */
export async function listarChats(): Promise<ChatItem[]> {
  const resposta = await fetchMeu('/meu/chats?page=0&size=100');
  const pagina = await comoJson<Pagina<ChatItem>>(resposta);
  return pagina.content;
}

/** Abre uma conversa do paciente logado com o histórico de mensagens. */
export async function buscarConversa(id: number | string): Promise<ChatDetalhe> {
  const resposta = await fetchMeu(`/meu/chats/${id}`);
  return comoJson<ChatDetalhe>(resposta);
}

/** Envia uma mensagem em nome do paciente logado. */
export async function enviarMensagemPaciente(id: number | string, texto: string): Promise<ChatDetalhe> {
  const resposta = await fetchMeu(`/meu/chats/${id}/mensagem`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ texto }),
  });
  return comoJson<ChatDetalhe>(resposta);
}

/** Confirma ao servidor que as mensagens da unidade chegaram neste aparelho (2º "check" no admin). */
export async function confirmarEntrega(id: number | string): Promise<void> {
  try {
    await fetchMeu(`/meu/chats/${id}/entregue`, { method: 'POST' });
  } catch {
    // entrega é best-effort; não deve quebrar a conversa
  }
}
