import { API_URL } from '@/constants/api';
import { authHeaders, carregarSessao, fetchMeu } from '@/services/sessao';

interface Ref {
  id: number;
  nome: string;
}

export interface Postagem {
  id: number;
  titulo: string;
  descricao: string | null;
  unidadeSaude: Ref;
  url: string;
  mostrarTotalCurtidas: boolean;
  totalCurtidas: number;
  habilitarComentarios: boolean;
  totalComentarios: number;
  curtidoPorMim: boolean;
  criadoEm: string;
}

export interface Comentario {
  id: number;
  autor: string;
  texto: string;
  criadoEm: string;
  /** Foi editado depois de publicado (mostra o selo "editado"). */
  editado: boolean;
  /** É do paciente logado (mostra editar/excluir). */
  meu: boolean;
  /** Ainda dentro da janela de edição (calculado no servidor; controla o botão "Editar"). */
  podeEditar: boolean;
  respostas: Comentario[];
}

export interface CurtirResultado {
  curtido: boolean;
  totalCurtidas: number;
}

interface Pagina<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

async function comoJson<T>(resposta: Response): Promise<T> {
  if (!resposta.ok) {
    throw new Error(`Falha na requisição (${resposta.status})`);
  }
  return resposta.json() as Promise<T>;
}

/** Feed com todas as postagens (mais recentes primeiro). */
export async function listarFeed(dispositivoId: string): Promise<Postagem[]> {
  const params = new URLSearchParams({ dispositivoId, page: '0', size: '50' });
  const resposta = await fetch(`${API_URL}/feed?${params.toString()}`);
  const pagina = await comoJson<Pagina<Postagem>>(resposta);
  return pagina.content;
}

/** Detalhe de uma postagem específica (formato do feed). */
export async function buscarPostagem(id: number | string, dispositivoId: string): Promise<Postagem> {
  const params = new URLSearchParams({ dispositivoId });
  const resposta = await fetch(`${API_URL}/feed/${id}?${params.toString()}`);
  return comoJson<Postagem>(resposta);
}

/** Curte ou descurte (toggle) a postagem. */
export async function curtir(postagemId: number, dispositivoId: string): Promise<CurtirResultado> {
  const resposta = await fetch(`${API_URL}/postagem/${postagemId}/curtir`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ dispositivoId }),
  });
  return comoJson<CurtirResultado>(resposta);
}

export interface PaginaComentarios {
  content: Comentario[];
  last: boolean;
  totalElements: number;
}

export async function listarComentarios(
  postagemId: number | string,
  page = 0,
  size = 20,
): Promise<PaginaComentarios> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  const url = `${API_URL}/postagem/${postagemId}/comentarios?${params.toString()}`;
  // Leitura é pública; com o token, o backend marca "meu"/"podeEditar". Enviamos o
  // token MANUALMENTE (sem a lógica de logout do fetchMeu): se estiver expirado/rotacionado
  // (401), relemos como anônimo — nunca deslogar o paciente só por LER comentários.
  await carregarSessao();
  let resposta = await fetch(url, { headers: authHeaders() });
  if (resposta.status === 401) {
    resposta = await fetch(url);
  }
  return comoJson<PaginaComentarios>(resposta);
}

/** Edita o próprio comentário (permitido só até 15 min após criar). */
export async function editarComentario(
  postagemId: number | string,
  comentarioId: number,
  texto: string,
): Promise<Comentario> {
  const resposta = await fetchMeu(`/postagem/${postagemId}/comentarios/${comentarioId}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ texto }),
  });
  return comoJson<Comentario>(resposta);
}

/** Exclui o próprio comentário. Excluir o comentário-raiz remove também as respostas. */
export async function excluirComentario(postagemId: number | string, comentarioId: number): Promise<void> {
  const resposta = await fetchMeu(`/postagem/${postagemId}/comentarios/${comentarioId}`, { method: 'DELETE' });
  if (!resposta.ok) {
    throw new Error(`Falha na requisição (${resposta.status})`);
  }
}

export async function comentar(postagemId: number | string, autor: string, texto: string): Promise<Comentario> {
  // Autenticado: o backend define o autor pelo token (o nome enviado é ignorado).
  const resposta = await fetchMeu(`/postagem/${postagemId}/comentarios`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ autor, texto }),
  });
  return comoJson<Comentario>(resposta);
}

/** Responde a um comentário (outro paciente pode ajudar a tirar a dúvida). */
export async function responder(
  postagemId: number | string,
  comentarioId: number,
  autor: string,
  texto: string,
): Promise<Comentario> {
  const resposta = await fetchMeu(
    `/postagem/${postagemId}/comentarios/${comentarioId}/responder`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ autor, texto }),
    },
  );
  return comoJson<Comentario>(resposta);
}
