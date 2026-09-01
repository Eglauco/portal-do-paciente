import { fetchMeu } from '@/services/sessao';

export type StatusManifestacao = 'AGUARDANDO_SAU' | 'AGUARDANDO_PACIENTE' | 'FECHADA';
export type AutorManifestacao = 'PACIENTE' | 'SAU';

interface Ref {
  id: number;
  nome: string;
}

/** Tipo de manifestação (vem do cadastro do admin). */
export interface TipoManifestacao {
  id: number;
  nome: string;
  descricao?: string | null;
  ativo: boolean;
}

/** Item da lista de manifestações do paciente. */
export interface ManifestacaoItem {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  tipo: Ref;
  status: StatusManifestacao;
  statusDescricao: string;
  ultimaMensagem: string | null;
  ultimaMensagemDe: AutorManifestacao | null;
  atualizadoEm: string;
  criadoEm: string;
}

export interface MensagemSau {
  id: number;
  autor: AutorManifestacao;
  autorNome: string;
  texto: string;
  criadoEm: string;
}

export interface ManifestacaoDetalhe {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  tipo: Ref;
  status: StatusManifestacao;
  statusDescricao: string;
  mensagens: MensagemSau[];
}

export interface UnidadeRef {
  id: number;
  nome: string;
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

/** Unidades disponíveis para o paciente escolher ao abrir uma manifestação. */
export async function listarUnidades(): Promise<UnidadeRef[]> {
  const resposta = await fetchMeu('/meu/sau/unidades');
  return comoJson<UnidadeRef[]>(resposta);
}

/** Tipos ATIVOS disponíveis para o paciente escolher (cadastro do admin). */
export async function listarTipos(): Promise<TipoManifestacao[]> {
  const resposta = await fetchMeu('/meu/sau/tipos');
  return comoJson<TipoManifestacao[]>(resposta);
}

/** Manifestações do paciente logado (mais recentes primeiro). */
export async function listarManifestacoes(): Promise<ManifestacaoItem[]> {
  const resposta = await fetchMeu('/meu/sau?page=0&size=100');
  const pagina = await comoJson<Pagina<ManifestacaoItem>>(resposta);
  return pagina.content;
}

/** Abre a manifestação e retorna já com a thread. */
export async function abrirManifestacao(
  tipoId: number,
  unidadeId: number,
  texto: string,
): Promise<ManifestacaoDetalhe> {
  const resposta = await fetchMeu('/meu/sau', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tipoId, unidadeId, texto }),
  });
  return comoJson<ManifestacaoDetalhe>(resposta);
}

/** Detalhe (thread) de uma manifestação do paciente. */
export async function buscarManifestacao(id: number | string): Promise<ManifestacaoDetalhe> {
  const resposta = await fetchMeu(`/meu/sau/${id}`);
  return comoJson<ManifestacaoDetalhe>(resposta);
}

/** Paciente responde na thread (reabre se estava fechada). */
export async function responderManifestacao(
  id: number | string,
  texto: string,
): Promise<ManifestacaoDetalhe> {
  const resposta = await fetchMeu(`/meu/sau/${id}/mensagem`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ texto }),
  });
  return comoJson<ManifestacaoDetalhe>(resposta);
}
