import { EstadoEntrega, Pagina, Ref, StatusAgendamento } from '../agendamentos/agendamento.model';

export type { EstadoEntrega, Pagina, Ref, StatusAgendamento };

/** Linha da LISTA de agendas (slot + quantos horários/pacientes). */
export interface AgendaResumo {
  id: number;
  /** yyyy-MM-dd */
  data: string;
  /** Nome da agenda (rótulo; pode vir da integração com um sistema externo). */
  nome?: string | null;
  /** Código de integração da agenda (ex.: ID_AGE_CONSULTA do CROSS) — rastreio. */
  codigoIntegracao?: string | null;
  especialidade: Ref;
  profissionalSaude: Ref;
  procedimento: Ref;
  unidadeSaude: Ref;
  totalHorarios: number;
}

/** Um Horário (marcação de um paciente) dentro de uma agenda. Traz o contexto da agenda embutido. */
export interface Horario {
  id: number;
  /** Código de integração do horário (CROSS ID_AGE_CONSULTA_HOR) — rastreio. */
  codigoIntegracao?: string | null;
  agendaId: number;
  /** Nome da agenda a que este horário pertence (rastreio). */
  agendaNome?: string | null;
  /** Código de integração da agenda (CROSS ID_AGE_CONSULTA) — rastreio. */
  agendaCodigoIntegracao?: string | null;
  /** yyyy-MM-dd (data da agenda). */
  data?: string;
  dataHora: string;
  /** HH:mm:ss */
  horaInicio: string;
  horaFim?: string | null;
  paciente: Ref;
  pacienteCpf?: string | null;
  /** Foto do paciente (URL direta do objeto no S3) — avatar na tela do horário. */
  pacienteFotoUrl?: string | null;
  especialidade?: Ref;
  profissionalSaude?: Ref;
  procedimento?: Ref;
  unidadeSaude?: Ref;
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
  /** Nome da agenda (rótulo; pode vir da integração com um sistema externo). */
  nome?: string | null;
  /** Código de integração da agenda (ex.: ID_AGE_CONSULTA do CROSS) — rastreio. */
  codigoIntegracao?: string | null;
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
  /** Nome da agenda (rótulo editável). O código de integração não é editável aqui. */
  nome?: string | null;
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
