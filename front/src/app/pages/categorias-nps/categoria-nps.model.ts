export interface CategoriaNps {
  id?: number;
  nome: string;
  ativo: boolean;
}

export interface CategoriaNpsFiltro {
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
