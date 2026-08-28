export type StatusAgendamento =
  | 'confirmado'
  | 'aguardando'
  | 'realizado'
  | 'cancelado'
  | 'reagendado';

/** Status do agendamento no back-end (enum Java). */
export type StatusBackend =
  | 'AGUARDANDO_CONFIRMACAO_PACIENTE'
  | 'PACIENTE_CONFIRMOU'
  | 'CANCELADO_PELA_UNIDADE'
  | 'CANCELADO_PELO_PACIENTE'
  | 'FALTA_PACIENTE'
  | 'PRESENCA_PACIENTE';

/** Motivo da falta (id + texto). */
export interface MotivoFalta {
  id: number;
  motivo: string;
}

export interface Agendamento {
  id: string;
  dia: string;
  mes: string;
  semana: string;
  especialidade: string;
  profissional: string;
  hora: string;
  unidade: string;
  status: StatusAgendamento;
  /** Rótulo do status vindo do back-end (ex.: "Aguardando confirmação do paciente"). */
  statusLabel?: string;
  grupo: 'proximos' | 'concluidos';
  /** Status cru do back-end (para regras específicas, ex.: falta pendente de justificativa). */
  statusBackend: StatusBackend;
  /** Verdadeiro quando a falta já foi justificada pelo paciente. */
  faltaJustificada: boolean;
  justificativaFalta?: string | null;
  motivosFalta: MotivoFalta[];
}
