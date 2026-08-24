export type StatusChat = 'NAO_LIDA' | 'AGUARDANDO_RESPOSTA' | 'EM_ATENDIMENTO' | 'RESOLVIDO';
export type Remetente = 'PACIENTE' | 'UNIDADE';

export interface Ref {
  id: number;
  nome: string;
}

/** Item da listagem (lista de conversas). */
export interface Chat {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  status: StatusChat;
  statusDescricao: string;
  ultimaMensagem?: string | null;
  ultimaMensagemDe?: Remetente | null;
  ultimaMensagemEm?: string | null;
  naoLidas: number;
  atualizadoEm: string;
}

export interface Mensagem {
  id: number;
  remetente: Remetente;
  texto: string;
  enviadaEm: string;
  lida: boolean;
}

export interface ChatDetalhe {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  status: StatusChat;
  statusDescricao: string;
  mensagens: Mensagem[];
}

export interface ChatFiltro {
  pacienteId?: number | null;
  unidadeId?: number | null;
  status?: StatusChat | null;
  naoResolvidas?: boolean;
}

export interface Pagina<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export const STATUS_OPTIONS: { value: StatusChat; label: string }[] = [
  { value: 'NAO_LIDA', label: 'Não lida' },
  { value: 'AGUARDANDO_RESPOSTA', label: 'Aguardando resposta' },
  { value: 'EM_ATENDIMENTO', label: 'Em atendimento' },
  { value: 'RESOLVIDO', label: 'Resolvido' },
];

export function statusLabel(valor: StatusChat): string {
  return STATUS_OPTIONS.find((o) => o.value === valor)?.label ?? valor;
}
