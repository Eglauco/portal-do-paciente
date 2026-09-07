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
  /** Há comentário de paciente novo (não visto pelo admin) para analisar. */
  novoComentario: boolean;
}

/** Detalhe (edição). */
export interface PostagemDetalhe {
  id: number;
  titulo: string;
  descricao: string | null;
  mostrarTotalCurtidas: boolean;
  habilitarComentarios: boolean;
  /** Validação de comentários novos por IA (Claude) ligada nesta postagem. */
  validarComentariosIa: boolean;
  unidadeSaude: Ref;
  url: string;
  criadoEm: string;
  totalCurtidas: number;
  totalComentarios: number;
  /** Até quando o admin já vira os comentários antes de abrir (marca "novo"); null = nunca abriu. */
  comentariosVistosEm: string | null;
}

/** Estado de moderação por IA (espelha o enum StatusModeracao no back). */
export type StatusModeracao = 'PUBLICADO' | 'PENDENTE' | 'REJEITADO';

export interface Comentario {
  id: number;
  autor: string;
  /** Foto (pré-assinada) do autor paciente, ou null se não tiver / não for paciente. */
  fotoUrl: string | null;
  /** Nome (abreviado) do responsável que comentou pelo paciente, ou null se foi o próprio. */
  responsavelNome: string | null;
  texto: string;
  criadoEm: string;
  /** Foi editado depois de publicado (mostra "editado"). */
  editado: boolean;
  /** É do admin logado (mostra editar). */
  meu: boolean;
  /** Ainda dentro da janela de edição de 15 min (calculado no servidor). */
  podeEditar: boolean;
  /** Moderação por IA: PENDENTE = em análise (oculto do público); REJEITADO = reprovado. */
  statusModeracao: StatusModeracao;
  /** Motivo da IA quando pendente/rejeitado (só o admin recebe). */
  motivoModeracao: string | null;
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
  /** Liga a validação de comentários novos por IA (Claude) antes de publicar. */
  validarComentariosIa: boolean;
  unidadeSaudeId: number;
  url: string;
}

export interface PostagemFiltro {
  titulo?: string | null;
  unidadeId?: number | null;
  comentarios?: boolean | null;
  /** true = só com comentário novo; false = só sem; null = todos. */
  novoComentario?: boolean | null;
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
