/** Frente (origem) de um uso de IA. */
export type UsoIaTipo =
  | 'PRONTUARIO_DOCUMENTO'
  | 'PRONTUARIO_RESUMO'
  | 'CHAT_MENSAGEM'
  | 'MODERACAO_COMENTARIO';

/** Uma linha do ledger de uso de IA (só consulta). */
export interface UsoIa {
  id: number;
  tipo: UsoIaTipo;
  tipoDescricao: string;
  descricao: string;
  modeloIa?: string | null;
  tokensEntrada?: number | null;
  tokensSaida?: number | null;
  custoUsd?: number | null;
  /** Rota do front para acessar a origem (ex.: /prontuarios/45); null se não aplicável. */
  rota?: string | null;
  /** ISO date-time. */
  criadoEm: string;
}

/** Totais do filtro atual (para bater com a fatura). */
export interface UsoIaTotais {
  custoUsd: number;
  tokensEntrada: number;
  tokensSaida: number;
  registros: number;
}

export interface UsoIaFiltro {
  tipo?: UsoIaTipo | null;
  /** yyyy-MM-dd */
  de?: string | null;
  /** yyyy-MM-dd */
  ate?: string | null;
  busca?: string | null;
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
