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
