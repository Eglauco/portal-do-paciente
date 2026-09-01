export interface Procedimento {
  id?: number;
  nome: string;
  preparo?: string;
  /** Antecedência mínima (em horas) para o paciente poder cancelar o agendamento. */
  horasCancelamento: number;
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
