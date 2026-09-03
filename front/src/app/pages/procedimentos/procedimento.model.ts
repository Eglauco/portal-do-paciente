export interface Procedimento {
  id?: number;
  nome: string;
  preparo?: string;
  /** Antecedência mínima (em horas) para o paciente poder cancelar o agendamento. */
  horasCancelamento: number;
  /** Horas após a presença do paciente para disparar o NPS (0 = na hora). */
  horasNps: number;
}

export interface ProcedimentoFiltro {
  codigo?: string;
  nome?: string;
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
