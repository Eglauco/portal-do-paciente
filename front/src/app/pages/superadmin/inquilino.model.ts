/** Inquilino (cliente) da plataforma multi-tenant, como o console de super-admin o enxerga. */
export interface Inquilino {
  id: number;
  nome: string;
  schemaName: string;
  situacao: string;
}

/** Dados para cadastrar um inquilino: o inquilino + o schema + a 1ª unidade e o 1º admin. */
export interface CriarInquilino {
  nome: string;
  schemaName: string;
  unidadeNome: string;
  adminNome: string;
  adminEmail: string;
  adminSenha: string;
}
