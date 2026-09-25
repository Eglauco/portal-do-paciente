/** Documento de Termo de Consentimento (TCLE) de um procedimento (Word no S3). */
export interface TermoProcedimento {
  id: number;
  nome: string;
  url: string;
  contentType: string | null;
  criadoEm: string;
}

export interface TermoProcedimentoRequest {
  nome: string;
  url: string;
  contentType: string | null;
}

/** Variável dinâmica que o backend substitui no Word ao enviar para a assinatura. */
export interface VariavelTermo {
  token: string;
  descricao: string;
  exemplo: string;
  grupo: string;
}
