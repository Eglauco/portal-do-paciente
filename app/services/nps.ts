import { API_URL } from '@/constants/api';

export type StatusNps = 'PENDENTE' | 'RESPONDIDO' | 'EXPIRADO';

interface Ref {
  id: number;
  nome: string;
}

/** Item da listagem de NPS. */
export interface NpsItem {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  especialidade: Ref;
  dataHora: string;
  status: StatusNps;
  statusDescricao: string;
  media?: number | null;
  criadoEm: string;
}

/** Categoria de NPS ativa (o paciente dá uma nota 0 a 10 para cada). */
export interface CategoriaNps {
  id: number;
  nome: string;
}

/** Nota dada a uma categoria dentro de uma avaliação. */
export interface CategoriaNota {
  categoriaId: number;
  categoria: string;
  nota: number;
}

/** Detalhe do NPS (inclui dados do atendimento e notas por categoria). */
export interface NpsDetalhe {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  especialidade: Ref;
  profissionalSaude: Ref;
  procedimento: Ref;
  dataHora: string;
  status: StatusNps;
  statusDescricao: string;
  media?: number | null;
  notas: CategoriaNota[];
  observacao?: string | null;
  criadoEm: string;
  respondidoEm?: string | null;
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

/** Lista todas as avaliações disponíveis (mais recentes primeiro). */
export async function listarNps(): Promise<NpsItem[]> {
  const resposta = await fetch(`${API_URL}/nps?page=0&size=100`);
  const pagina = await comoJson<Pagina<NpsItem>>(resposta);
  return pagina.content;
}

/** Detalha uma avaliação. */
export async function buscarNps(id: number | string): Promise<NpsDetalhe> {
  const resposta = await fetch(`${API_URL}/nps/${id}`);
  return comoJson<NpsDetalhe>(resposta);
}

/** Categorias de NPS ativas (para o paciente avaliar). */
export async function listarCategoriasNps(): Promise<CategoriaNps[]> {
  const resposta = await fetch(`${API_URL}/categoria-nps/ativos`);
  return comoJson<CategoriaNps[]>(resposta);
}

/** Responde uma avaliação (uma nota 0 a 10 por categoria + observação opcional). */
export async function responderNps(
  id: number | string,
  notas: { categoriaId: number; nota: number }[],
  observacao?: string | null,
): Promise<NpsDetalhe> {
  const resposta = await fetch(`${API_URL}/nps/${id}/responder`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ notas, observacao: observacao?.trim() || null }),
  });
  return comoJson<NpsDetalhe>(resposta);
}
