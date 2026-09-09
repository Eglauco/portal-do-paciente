/** Tipo do valor (espelha TipoConfiguracao no back) — define qual campo de valor usar. */
export type TipoConfiguracao = 'BOOLEANO' | 'NUMERICO' | 'TEXTO' | 'COR';

export interface Configuracao {
  id: number;
  nome: string;
  descricao?: string | null;
  chave: string;
  tipoConfiguracao: TipoConfiguracao;
  valorBooleano?: boolean | null;
  valorTexto?: string | null;
  valorNumerico?: number | null;
  /** Valor do tipo COR: hex #RRGGBB. */
  valorCor?: string | null;
  atualizadoEm?: string | null;
  /** Nome de quem fez a última alteração (auditoria). */
  atualizadoPorNome?: string | null;
}

/** Payload de edição — só o valor (o servidor aplica o campo do tipo do registro). */
export interface ConfiguracaoValor {
  valorBooleano?: boolean | null;
  valorTexto?: string | null;
  valorNumerico?: number | null;
  valorCor?: string | null;
}

export interface ConfiguracaoFiltro {
  busca?: string;
  tipo?: TipoConfiguracao | null;
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
