export interface TipoManifestacao {
  id?: number;
  nome: string;
  descricao?: string | null;
  ativo: boolean;
}

export interface TipoManifestacaoFiltro {
  nome?: string;
  ativo?: boolean | null;
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
