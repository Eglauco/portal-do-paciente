import { fetchMeu } from '@/services/sessao';

// Tipos gravados pelo backend (o chat NÃO entra nesta lista).
export type TipoNotificacao =
  | 'AGENDAMENTO'
  | 'FALTA'
  | 'NPS'
  | 'POSTAGEM'
  | 'PRONTUARIO'
  | 'SAU'
  | 'LEMBRETE';

/** Uma notificação do histórico do paciente (tela "Notificações"). */
export interface Notificacao {
  id: number;
  tipo: TipoNotificacao;
  titulo: string;
  corpo: string;
  referenciaId: number | null;
  lida: boolean;
  criadoEm: string;
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

/** Notificações do paciente logado (mais recentes primeiro). */
export async function listarNotificacoes(): Promise<Notificacao[]> {
  const resposta = await fetchMeu('/meu/notificacoes?page=0&size=100');
  const pagina = await comoJson<Pagina<Notificacao>>(resposta);
  return pagina.content;
}

/** Quantas ainda não foram lidas (contador do sino). */
export async function contarNaoLidas(): Promise<number> {
  const resposta = await fetchMeu('/meu/notificacoes/nao-lidas');
  const dados = await comoJson<{ total: number }>(resposta);
  return dados.total;
}

/** Marca a notificação como lida (ao tocar nela). Best-effort. */
export async function marcarNotificacaoLida(id: number): Promise<void> {
  const resposta = await fetchMeu(`/meu/notificacoes/${id}/lida`, { method: 'POST' });
  if (!resposta.ok) {
    throw new Error(`Falha ao marcar como lida (${resposta.status})`);
  }
}
