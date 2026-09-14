export interface Conselho {
  id?: number;
  /** Sigla do conselho (ex.: CRM), separada do nome. */
  sigla: string;
  nome: string;
}

export interface ConselhoFiltro {
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
