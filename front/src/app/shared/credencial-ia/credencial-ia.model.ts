/** Escopo da credencial de IA que o formulário configura. */
export type EscopoIaInput = { tipo: 'unidade'; unidadeId: number } | { tipo: 'inquilino' };

/** Status da credencial de um escopo (a api-key nunca vem em claro — só {@code apiKeyPreenchida}). */
export interface StatusIa {
  configurado: boolean;
  apiKeyPreenchida: boolean;
  modeloModeracao: string;
  modeloChat: string;
  modeloProntuario: string;
  ativo: boolean;
}

/** Corpo de salvar (api-key em branco MANTÉM a atual; os 3 modelos são obrigatórios). */
export interface FormIa {
  apiKey: string;
  modeloModeracao: string;
  modeloChat: string;
  modeloProntuario: string;
  ativo: boolean;
}

export interface ResultadoTesteIa {
  ok: boolean;
  mensagem: string;
}
