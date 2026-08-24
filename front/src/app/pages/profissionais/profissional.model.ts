export interface ProfissionalSaude {
  id?: number;
  nome: string;
}

export interface ProfissionalSaudeFiltro {
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
