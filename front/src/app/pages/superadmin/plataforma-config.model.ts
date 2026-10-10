/** Identidade da plataforma (defaults do login + fallback dos inquilinos), gerida pelo super-admin. */
export interface ConfigPlataforma {
  /** Cor primária #RRGGBB (semente do tema). */
  corPrimaria: string | null;
  nomePlataforma: string | null;
  loginTitulo: string | null;
  loginSubtitulo: string | null;
  /** URL canônica da logomarca (para reenviar no salvar); null = sem logo. */
  logoUrl: string | null;
  /** URL assinada da logomarca (para preview na tela; bucket privado). */
  logoUrlVisualizacao: string | null;
  /** URL canônica da imagem de fundo do login; null = sem imagem. */
  loginFundoUrl: string | null;
  /** URL assinada da imagem de fundo (preview). */
  loginFundoUrlVisualizacao: string | null;
}

/** Payload de gravação (PUT): só os valores editáveis; imagens = URL canônica. */
export interface SalvarConfigPlataforma {
  corPrimaria: string;
  nomePlataforma: string | null;
  loginTitulo: string | null;
  loginSubtitulo: string | null;
  logoUrl: string | null;
  loginFundoUrl: string | null;
}
