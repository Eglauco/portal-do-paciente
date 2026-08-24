export interface Procedimento {
  id?: number;
  nome: string;
  preparo?: string;
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
