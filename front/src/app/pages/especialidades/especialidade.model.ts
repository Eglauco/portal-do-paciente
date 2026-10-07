export interface Especialidade {
  id?: number;
  nome: string;
  /** Código da especialidade em um sistema externo (integração); único quando preenchido. */
  codigoIntegracao?: string | null;
  /**
   * Procedimento vinculado — usado no lançamento automático do agendamento na importação do SIRESP. Opcional.
   * Na resposta vem com {id, nome}; ao salvar basta o {id}.
   */
  procedimento?: { id: number; nome?: string } | null;
}

export interface EspecialidadeFiltro {
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
