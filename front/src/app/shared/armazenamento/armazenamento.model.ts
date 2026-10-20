/** Escopo do S3 que o formulário configura. */
export type EscopoS3Input = { tipo: 'unidade'; unidadeId: number } | { tipo: 'inquilino' };

/** Status do S3 de um escopo (a secret-key nunca vem em claro — só {@code secretPreenchido}). */
export interface StatusS3 {
  configurado: boolean;
  bucket: string;
  region: string;
  publicEndpoint: string;
  internalEndpoint: string;
  accessKey: string;
  secretPreenchido: boolean;
  ativo: boolean;
}

/** Corpo de salvar (secret em branco MANTÉM a atual; internalEndpoint em branco cai no público). */
export interface FormS3 {
  bucket: string;
  region: string;
  publicEndpoint: string;
  internalEndpoint: string;
  accessKey: string;
  secretKey: string;
  ativo: boolean;
}

export interface ResultadoTesteS3 {
  ok: boolean;
  mensagem: string;
}
