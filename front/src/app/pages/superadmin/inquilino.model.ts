/** Inquilino (cliente) da plataforma multi-tenant, como o console de super-admin o enxerga. */
export interface Inquilino {
  id: number;
  nome: string;
  schemaName: string;
  /** Rótulo do subdomínio (ex.: "nascente" em nascente.trilharsaude.app.br). Nulo nas linhas de plataforma. */
  subdominio: string | null;
  situacao: string;
}

/** Dados para cadastrar um inquilino: o inquilino + o schema + o subdomínio + a 1ª unidade e o 1º admin. */
export interface CriarInquilino {
  nome: string;
  schemaName: string;
  subdominio: string;
  unidadeNome: string;
  adminNome: string;
  adminEmail: string;
  adminSenha: string;
}
