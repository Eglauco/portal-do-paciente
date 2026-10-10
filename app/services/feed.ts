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

/** Estado de moderação por IA. O paciente só recebe PUBLICADO (feed) ou PENDENTE (o próprio). */
export type StatusModeracao = 'PUBLICADO' | 'PENDENTE' | 'REJEITADO';

export interface Comentario {
  id: number;
  autor: string;
  /** Foto (pré-assinada) do autor, ou null se não tiver / não for paciente. */
  fotoUrl: string | null;
  /** Nome (abreviado) do responsável que comentou pelo paciente, ou null se foi o próprio. */
  responsavelNome: string | null;
  texto: string;
  criadoEm: string;
  /** Foi editado depois de publicado (mostra o selo "editado"). */
  editado: boolean;
  /** É do paciente logado (mostra editar/excluir). */
  meu: boolean;
  /** Ainda dentro da janela de edição (calculado no servidor; controla o botão "Editar"). */
  podeEditar: boolean;
  /** PENDENTE = em análise por IA (só o autor vê o próprio); PUBLICADO = visível a todos. */
  statusModeracao: StatusModeracao;
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
    throw new Error(await mensagemErro(resposta));
  }
  return resposta.json() as Promise<T>;
}

/**
 * Mensagem amigável do corpo de erro do backend (campo "message" — exposto via
 * server.error.include-message), para mostrar ao paciente o motivo (ex.: idade mínima para
 * comentar). Sem mensagem utilizável, cai num texto genérico com o status.
 */
async function mensagemErro(resposta: Response): Promise<string> {
  try {
    const corpo = await resposta.json();
    const msg = corpo?.message;
    if (typeof msg === 'string' && msg.trim()) return msg.trim();
  } catch {
    // corpo não-JSON/vazio: usa o genérico abaixo
  }
  return `Falha na requisição (${resposta.status})`;
}

/** Feed do paciente logado (só as postagens das unidades a que ele tem acesso). */
export async function listarFeed(dispositivoId: string): Promise<Postagem[]> {
  const params = new URLSearchParams({ dispositivoId, page: '0', size: '50' });
  // Autenticado: o backend filtra pelas unidades do paciente (o token vai no fetchMeu).
  const resposta = await fetchMeu(`/feed?${params.toString()}`);
  const pagina = await comoJson<Pagina<Postagem>>(resposta);
  return pagina.content;
}

/** Detalhe de uma postagem específica (formato do feed). */
export async function buscarPostagem(id: number | string, dispositivoId: string): Promise<Postagem> {
  const params = new URLSearchParams({ dispositivoId });
  const resposta = await fetchMeu(`/feed/${id}?${params.toString()}`);
  return comoJson<Postagem>(resposta);
}

/** Curte ou descurte (toggle) a postagem. Envia o token para o backend resolver o INQUILINO certo. */
export async function curtir(postagemId: number, dispositivoId: string): Promise<CurtirResultado> {
  // /curtir é permitAll, mas o TenantFilter precisa do claim "inq" do JWT para achar o schema do
  // inquilino (Postagem/Curtida NÃO são tabelas de plataforma). Sem token, cairia no 'principal'.
  await carregarSessao();
  const resposta = await fetch(`${API_URL}/postagem/${postagemId}/curtir`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
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
  // Enviamos o token MANUALMENTE (sem a lógica de logout do fetchMeu): nunca deslogar o paciente só
  // por LER comentários. NÃO relemos anônimo no 401: sem token o TenantFilter cairia no 'principal' e
  // devolveria os comentários do INQUILINO ERRADO. Token expirado → a leitura falha (sem vazar outro tenant).
  await carregarSessao();
  const resposta = await fetch(url, { headers: authHeaders() });
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

export async function comentar(postagemId: number | string, texto: string): Promise<Comentario> {
  // O autor exibido é resolvido no servidor pelo token do paciente; não vai no corpo.
  const resposta = await fetchMeu(`/postagem/${postagemId}/comentarios`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ texto }),
  });
  return comoJson<Comentario>(resposta);
}

/** Responde a um comentário (outro paciente pode ajudar a tirar a dúvida). */
export async function responder(
  postagemId: number | string,
  comentarioId: number,
  texto: string,
): Promise<Comentario> {
  const resposta = await fetchMeu(
    `/postagem/${postagemId}/comentarios/${comentarioId}/responder`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ texto }),
    },
  );
  return comoJson<Comentario>(resposta);
}
