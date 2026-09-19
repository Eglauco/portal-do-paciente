export interface TipoDocumentoProntuario {
  id?: number;
  nome: string;
  promptResumo?: string | null;
  promptValidacao?: string | null;
  ativo: boolean;
}

/** Item enxuto do seletor de tipo no upload de documentos (GET /ativos). */
export interface TipoDocumentoProntuarioAtivo {
  id: number;
  nome: string;
}

export interface TipoDocumentoProntuarioFiltro {
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
