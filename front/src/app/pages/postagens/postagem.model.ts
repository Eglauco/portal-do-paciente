export interface Ref {
  id: number;
  nome: string;
}

/** Item da listagem de postagens. */
export interface Postagem {
  id: number;
  titulo: string;
  descricao: string | null;
  unidadeSaude: Ref;
  mostrarTotalCurtidas: boolean;
  habilitarComentarios: boolean;
  url: string;
  criadoEm: string;
  totalCurtidas: number;
  totalComentarios: number;
}

/** Detalhe (edição). */
export interface PostagemDetalhe {
  id: number;
  titulo: string;
  descricao: string | null;
  mostrarTotalCurtidas: boolean;
  habilitarComentarios: boolean;
  unidadeSaude: Ref;
  url: string;
  criadoEm: string;
  totalCurtidas: number;
  totalComentarios: number;
}

export interface Comentario {
  id: number;
  autor: string;
  texto: string;
  criadoEm: string;
  /** Foi editado depois de publicado (mostra "editado"). */
  editado: boolean;
  /** É do admin logado (mostra editar). */
  meu: boolean;
  /** Ainda dentro da janela de edição de 15 min (calculado no servidor). */
  podeEditar: boolean;
  respostas: Comentario[];
}

export interface PaginaComentarios {
  content: Comentario[];
  last: boolean;
  totalElements: number;
}

export interface PostagemRequest {
  titulo: string;
  descricao: string | null;
  mostrarTotalCurtidas: boolean;
  habilitarComentarios: boolean;
  unidadeSaudeId: number;
  url: string;
}

export interface PostagemFiltro {
  titulo?: string | null;
  unidadeId?: number | null;
  comentarios?: boolean | null;
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
