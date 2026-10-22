/** Escopo da credencial de IA que o formulário configura. */
export type EscopoIaInput = { tipo: 'unidade'; unidadeId: number } | { tipo: 'inquilino' };

/** Status da credencial de um escopo (a api-key nunca vem em claro — só {@code apiKeyPreenchida}). */
export interface StatusIa {
  configurado: boolean;
  apiKeyPreenchida: boolean;
  /** Provedor de IA do escopo (ANTHROPIC, OPENAI, …). */
  provedor: string;
  /** URL base opcional (Azure/compatível). */
  baseUrl: string | null;
  modeloModeracao: string;
  modeloChat: string;
  modeloProntuario: string;
  ativo: boolean;
}

/** Corpo de salvar (api-key em branco MANTÉM a atual; provedor + 3 modelos obrigatórios; baseUrl opcional). */
export interface FormIa {
  provedor: string;
  apiKey: string;
  baseUrl: string | null;
  modeloModeracao: string;
  modeloChat: string;
  modeloProntuario: string;
  ativo: boolean;
}

export interface ResultadoTesteIa {
  ok: boolean;
  mensagem: string;
}
