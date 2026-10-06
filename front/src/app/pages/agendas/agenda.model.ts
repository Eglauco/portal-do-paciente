import { EstadoEntrega, Pagina, Ref, StatusAgendamento } from '../agendamentos/agendamento.model';

export type { EstadoEntrega, Pagina, Ref, StatusAgendamento };

/** Linha da LISTA de agendas (slot + quantos horários/pacientes). */
export interface AgendaResumo {
  id: number;
  /** yyyy-MM-dd */
  data: string;
  especialidade: Ref;
  profissionalSaude: Ref;
  procedimento: Ref;
  unidadeSaude: Ref;
  totalHorarios: number;
}

/** Um Horário (marcação de um paciente) dentro de uma agenda. */
export interface Horario {
  id: number;
  agendaId: number;
  dataHora: string;
  /** HH:mm:ss */
  horaInicio: string;
  horaFim?: string | null;
  paciente: Ref;
  pacienteCpf?: string | null;
  statusAgendamento: StatusAgendamento;
  statusDescricao?: string;
  faltaJustificada?: boolean;
  justificativaFalta?: string | null;
  motivosFalta?: Ref[];
  horasCancelamento?: number;
  entregaResumo?: EstadoEntrega | null;
  entregaResumoDescricao?: string | null;
}

/** Detalhe da agenda: o slot + seus horários. */
export interface Agenda {
  id: number;
  data: string;
  especialidade: Ref;
  profissionalSaude: Ref;
  procedimento: Ref;
  unidadeSaude: Ref;
  horarios: Horario[];
}

export interface AgendaRequest {
  data: string;
  profissionalSaudeId: number;
  especialidadeId: number;
  procedimentoId: number;
  unidadeSaudeId: number;
}

export interface HorarioRequest {
  agendaId: number;
  pacienteId: number;
  /** HH:mm */
  horaInicio: string;
  horaFim?: string | null;
  statusAgendamento?: StatusAgendamento;
}

export interface AgendaFiltro {
  data: string | null;
  profissionalNome: string | null;
  especialidadeNome: string | null;
}

export function agendaFiltroVazio(): AgendaFiltro {
  return { data: null, profissionalNome: null, especialidadeNome: null };
}
