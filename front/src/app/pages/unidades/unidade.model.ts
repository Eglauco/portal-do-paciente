/** Item do FAQ da unidade (par pergunta + resposta) que alimenta a assistente virtual. */
export interface UnidadeFaqItem {
  id?: number;
  pergunta: string;
  resposta: string;
}

export interface Unidade {
  id?: number;
  nome: string;
  /** FAQ que a assistente virtual usa no chat desta unidade (não vem no grid). */
  faq?: UnidadeFaqItem[];
}

export interface UnidadeFiltro {
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
