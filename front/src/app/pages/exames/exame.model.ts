export interface Exame {
  id?: number;
  nome: string;
  /** Código do exame em um sistema externo (integração); único quando preenchido. */
  codigoIntegracao?: string | null;
  /**
   * ConfiguracaoAgenda vinculado — usado no lançamento automático do agendamento na importação do SIRESP. Opcional.
   * Na resposta vem com {id, nome}; ao salvar basta o {id}.
   */
  configuracaoAgenda?: { id: number; nome?: string } | null;
}

export interface ExameFiltro {
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
