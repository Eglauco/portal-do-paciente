/** Escopo da credencial de IA que o formulário configura. */
export type EscopoIaInput = { tipo: 'unidade'; unidadeId: number } | { tipo: 'inquilino' };

/** Status da credencial de um escopo (a api-key nunca vem em claro — só {@code apiKeyPreenchida}). */
export interface StatusIa {
  configurado: boolean;
  apiKeyPreenchida: boolean;
  modeloModeracao: string;
  modeloChat: string;
  modeloProntuario: string;
  /** Modelos padrão da plataforma (usados quando o do cliente fica em branco) — mostrados como dica. */
  modeloModeracaoPadrao: string;
  modeloChatPadrao: string;
  modeloProntuarioPadrao: string;
  ativo: boolean;
}

/** Corpo de salvar (api-key em branco MANTÉM a atual; modelos em branco usam o padrão da plataforma). */
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
