export type StatusAgendamento =
  | 'AGUARDANDO_CONFIRMACAO_PACIENTE'
  | 'PACIENTE_CONFIRMOU'
  | 'CANCELADO_PELA_UNIDADE'
  | 'CANCELADO_PELO_PACIENTE'
  | 'FALTA_PACIENTE'
  | 'PRESENCA_PACIENTE';

export interface Ref {
  id: number;
  nome: string;
}

/** Estado da entrega da notificação do agendamento ao destino (espelha EstadoEntrega no backend). */
export type EstadoEntrega =
  | 'PACIENTE_SEM_APLICATIVO'
  | 'NOTIFICACAO_ENVIADA'
  | 'NOTIFICACAO_ENTREGUE'
  | 'SEM_NOTIFICACAO_ATIVA';

/** Formato de resposta da API. */
export interface Agendamento {
  id?: number;
  dataHora: string;
  especialidade: Ref;
  profissionalSaude: Ref;
  procedimento: Ref;
  paciente: Ref;
  unidadeSaude: Ref;
  statusAgendamento: StatusAgendamento;
  statusDescricao?: string;
  /** Justificativa da falta (preenchida pelo paciente no app). */
  faltaJustificada?: boolean;
  justificativaFalta?: string | null;
  motivosFalta?: Ref[];
  /** Resumo da entrega da notificação (null = sem dado / agendamento anterior à funcionalidade). */
  entregaResumo?: EstadoEntrega | null;
  entregaResumoDescricao?: string | null;
}

/**
 * Um evento de entrega da notificação (espelha AgendamentoEntregaResponse). A tabela é
 * append-only: cada mudança de estado é um evento; o front agrupa por pessoa (tipo+responsavelId).
 */
export interface AgendamentoEntrega {
  id: number;
  tipo: 'PACIENTE' | 'RESPONSAVEL';
  responsavelId: number | null;
  nome: string;
  telefone: string | null;
  estado: EstadoEntrega;
  estadoDescricao: string;
  criadoEm: string;
}

/** Filtros da tela de listagem de agendamentos (todos opcionais). */
export interface AgendamentoFiltro {
  status: StatusAgendamento | null;
  /** Busca parcial pelo nome do paciente. */
  nome: string | null;
  /** Busca parcial pelo nome da especialidade. */
  especialidadeNome: string | null;
  /** Busca parcial pelo nome do profissional. */
  profissionalNome: string | null;
  /** Estado do resumo de entrega da notificação. */
  entregaResumo: EstadoEntrega | null;
  /** Dia único (yyyy-MM-dd) sobre a data/hora do agendamento. */
  data: string | null;
}

/** Filtro sem nenhum critério (todos os agendamentos). */
export function filtroVazio(): AgendamentoFiltro {
  return {
    status: null,
    nome: null,
    especialidadeNome: null,
    profissionalNome: null,
    entregaResumo: null,
    data: null,
  };
}

/** Formato de envio (criação/edição). */
export interface AgendamentoRequest {
  dataHora: string;
  especialidadeId: number;
  profissionalSaudeId: number;
  procedimentoId: number;
  pacienteId: number;
  unidadeSaudeId: number;
  statusAgendamento?: StatusAgendamento;
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

export const STATUS_OPTIONS: { value: StatusAgendamento; label: string }[] = [
  { value: 'AGUARDANDO_CONFIRMACAO_PACIENTE', label: 'Aguardando confirmação do paciente' },
  { value: 'PACIENTE_CONFIRMOU', label: 'Paciente confirmou' },
  { value: 'CANCELADO_PELA_UNIDADE', label: 'Cancelado pela unidade' },
  { value: 'CANCELADO_PELO_PACIENTE', label: 'Cancelado pelo paciente' },
  { value: 'FALTA_PACIENTE', label: 'Falta do paciente' },
  { value: 'PRESENCA_PACIENTE', label: 'Presença do paciente' },
];

export function statusLabel(valor: StatusAgendamento): string {
  return STATUS_OPTIONS.find((o) => o.value === valor)?.label ?? valor;
}

/** Rótulo curto do estado de entrega (para o pill da lista e o painel de destinatários). */
export function entregaLabel(estado: EstadoEntrega | null | undefined): string {
  switch (estado) {
    case 'NOTIFICACAO_ENTREGUE':
      return 'Notificação entregue';
    case 'NOTIFICACAO_ENVIADA':
      return 'Notificação enviada';
    case 'SEM_NOTIFICACAO_ATIVA':
      return 'Sem notificação ativa';
    case 'PACIENTE_SEM_APLICATIVO':
      return 'Sem aplicativo';
    default:
      return '—';
  }
}

/** Quem fez a troca de status registrada no log. */
export type AutorLog = 'PACIENTE' | 'RESPONSAVEL' | 'UNIDADE';

/** Item da linha do tempo de status do agendamento (espelha AgendamentoLogResponse no backend). */
export interface AgendamentoLog {
  id: number;
  autor: AutorLog;
  /** Nome do paciente (quando autor = PACIENTE). */
  pacienteNome: string | null;
  /** Nome do responsável (quando autor = RESPONSAVEL). */
  responsavelNome: string | null;
  /** Nome do atendente (quando autor = UNIDADE). */
  usuarioNome: string | null;
  statusAnterior: StatusAgendamento | null;
  statusAnteriorDescricao: string | null;
  statusNovo: StatusAgendamento;
  statusNovoDescricao: string | null;
  criadoEm: string;
}
