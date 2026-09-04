export interface Usuario {
  id?: number;
  nome: string;
  email: string;
  unidade?: { id: number; nome: string } | null;
  /** Perfis de acesso do usuário (a permissão efetiva é a união deles). */
  perfis?: { id: number; nome: string }[];
}

/** Dados de criação/edição. `senha` é obrigatória na criação e opcional na edição. */
export interface UsuarioRequest {
  nome: string;
  email: string;
  senha?: string;
  unidadeSaudeId: number;
  perfilIds: number[];
}

export interface UsuarioFiltro {
  codigo?: string;
  nome?: string;
  email?: string;
}

export interface Pagina<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
