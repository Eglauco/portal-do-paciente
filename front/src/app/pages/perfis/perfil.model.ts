/** Referência leve de unidade (id + nome). */
export interface Ref {
  id: number;
  nome: string;
}

/** Uma tela do catálogo (chave do enum + rótulo do menu). */
export interface TelaOpcao {
  chave: string;
  descricao: string;
}

/** Perfil de acesso: telas liberadas + unidades que enxerga. */
export interface Perfil {
  id?: number;
  nome: string;
  telas: string[];
  unidades: Ref[];
}

/** Cadastro/edição de um perfil. */
export interface PerfilRequest {
  nome: string;
  telas: string[];
  unidadeIds: number[];
}

/** Filtros da listagem de perfis. */
export interface PerfilFiltro {
  codigo?: string;
  nome?: string;
}

/** Resposta paginada padrão dos CRUDs. */
export interface Pagina<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
