/** Origem do modelo do termo: arquivo .docx nosso (qualquer provedor) ou modelo pronto no ZapSign. */
export type OrigemModeloTermo = 'ARQUIVO' | 'ZAPSIGN_MODELO';

/** Documento de Termo de Consentimento (TCLE) de um configuracaoAgenda. */
export interface TermoConfiguracaoAgenda {
  id: number;
  nome: string;
  origemModelo: OrigemModeloTermo;
  url: string | null;
  contentType: string | null;
  providerTemplateToken: string | null;
  modeloProviderNome: string | null;
  /** Se true, o profissional de saúde do atendimento também assina (após o paciente). */
  profissionalAssina: boolean;
  /** Só com profissionalAssina: profissional assina com certificado digital (ICP) em vez de em tela. */
  profissionalCertificado: boolean;
  criadoEm: string;
}

export interface TermoConfiguracaoAgendaRequest {
  nome: string;
  origemModelo: OrigemModeloTermo;
  url: string | null;
  contentType: string | null;
  providerTemplateToken: string | null;
  modeloProviderNome: string | null;
  profissionalAssina: boolean;
  profissionalCertificado: boolean;
}

/** Variável dinâmica que o backend substitui no Word ao enviar para a assinatura. */
export interface VariavelTermo {
  token: string;
  descricao: string;
  exemplo: string;
  grupo: string;
}

/** Modelo (template) do ZapSign — para o seletor no cadastro do termo. */
export interface ModeloZapSign {
  token: string;
  nome: string;
  tipo: string;
  ativo: boolean;
}

/** Variável ({{...}}) que um modelo do ZapSign espera + se o POP sabe preenchê-la. */
export interface VariavelModeloZapSign {
  variable: string;
  label: string;
  required: boolean;
  conhecida: boolean;
}

/** Detalhe de um modelo do ZapSign: nome + variáveis (com marcação de conhecidas). */
export interface ModeloZapSignDetalhe {
  token: string;
  nome: string;
  variaveis: VariavelModeloZapSign[];
}
