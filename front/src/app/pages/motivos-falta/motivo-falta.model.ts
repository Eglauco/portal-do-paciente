export interface MotivoFalta {
  id?: number;
  motivo: string;
  ativo: boolean;
}

export interface MotivoFaltaFiltro {
  codigo?: string;
  motivo?: string;
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
