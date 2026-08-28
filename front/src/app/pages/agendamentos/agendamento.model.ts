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
