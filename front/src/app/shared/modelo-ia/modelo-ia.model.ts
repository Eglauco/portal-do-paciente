/**
 * Modelo de IA (Claude/Anthropic) cadastrado na plataforma: a VERSÃO exata do modelo + o preço
 * por milhão de tokens (entrada/saída). É a fonte dos selects de modelo no cadastro da credencial
 * de IA e do cálculo de custo de uso. Fica em shared/ porque é usado tanto pela tela Modelos de IA
 * (pages/modelos-ia) quanto pelo formulário de credencial (shared/credencial-ia).
 */
export interface ModeloIa {
  id?: number;
  /** Provedor a que o modelo pertence (ANTHROPIC, OPENAI, …). */
  provedor: string;
  /** Id do modelo na API do provedor (ex.: "claude-opus-5", "gpt-4o"). Único. */
  modeloId: string;
  /** Nome amigável para exibir (ex.: "Claude Opus 5"). */
  nome: string;
  /** Preço de ENTRADA em US$ por 1 milhão de tokens. */
  precoEntradaUsdMtok: number;
  /** Preço de SAÍDA em US$ por 1 milhão de tokens. */
  precoSaidaUsdMtok: number;
  ativo: boolean;
  /** O modelo aceita PDF nativo como anexo? (análise do prontuário manda o PDF; se false, o front avisa). */
  suportaPdf: boolean;
}
