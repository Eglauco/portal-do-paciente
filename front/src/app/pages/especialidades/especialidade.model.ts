export interface Especialidade {
  id?: number;
  nome: string;
  /** Código da especialidade em um sistema externo (integração); único quando preenchido. */
  codigoIntegracao?: string | null;
}

export interface EspecialidadeFiltro {
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
