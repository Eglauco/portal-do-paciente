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
  /** Foto (pré-assinada) do paciente para o avatar; null se não tiver. */
  pacienteFotoUrl?: string | null;
  unidadeSaude: Ref;
  status: StatusChat;
  statusDescricao: string;
  ultimaMensagem?: string | null;
  ultimaMensagemDe?: Remetente | null;
  ultimaMensagemEm?: string | null;
  naoLidas: number;
  atualizadoEm: string;
  responsavelId?: number | null;
  responsavelNome?: string | null;
}

export interface Mensagem {
  id: number;
  remetente: Remetente;
  texto: string;
  enviadaEm: string;
  lida: boolean;
  entregue: boolean;
  /** Nome do atendente que enviou (só nas mensagens da unidade). */
  atendenteNome?: string | null;
  /** Só no cliente: mensagem otimista ainda não confirmada pelo servidor (mostra o relógio). */
  pendente?: boolean;
  /** Só no cliente: o envio falhou depois das retentativas (mostra "reenviar"). */
  falha?: boolean;
  /** Id gerado pelo cliente (idempotência do reenvio). */
  clienteId?: string;
}

/** Id único gerado pelo cliente para tornar o reenvio idempotente. */
export function novoClienteId(): string {
  return `c-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export interface ChatDetalhe {
  id: number;
  paciente: Ref;
  /** Foto (pré-assinada) do paciente para o avatar; null se não tiver. */
  pacienteFotoUrl?: string | null;
  unidadeSaude: Ref;
  status: StatusChat;
  statusDescricao: string;
  /**
   * false quando o paciente não tem mais sessão no app: bloqueia o envio da
   * unidade. Ausente/indefinido em respostas antigas — nesse caso NÃO se bloqueia
   * (o back é a autoridade e responde 422 quando preciso).
   */
  pacienteUsandoApp?: boolean;
  /** Atendente responsável pela conversa (só ele pode enviar). null = ninguém assumiu. */
  responsavelId?: number | null;
  responsavelNome?: string | null;
  mensagens: Mensagem[];
}

export interface ChatFiltro {
  pacienteId?: number | null;
  unidadeId?: number | null;
  responsavelId?: number | null;
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

/** Auditoria (log) da conversa — espelha ChatLogResponse no backend. */
export type TipoLogChat = 'VISUALIZOU' | 'ASSUMIU' | 'TRANSFERIU' | 'RESOLVEU' | 'REABRIU' | 'STATUS_ALTERADO';

export interface ChatLog {
  id: number;
  tipo: TipoLogChat;
  usuarioNome: string | null;
  destinoNome: string | null;
  statusAnterior: StatusChat | null;
  statusNovo: StatusChat | null;
  criadoEm: string;
}
