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
  nota?: number | null;
  criadoEm: string;
}

/** Detalhe do NPS (inclui dados do atendimento). */
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
  nota?: number | null;
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

/** Responde uma avaliação (nota 0 a 10 + observação opcional). */
export async function responderNps(
  id: number | string,
  nota: number,
  observacao?: string | null,
): Promise<NpsDetalhe> {
  const resposta = await fetch(`${API_URL}/nps/${id}/responder`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nota, observacao: observacao?.trim() || null }),
  });
  return comoJson<NpsDetalhe>(resposta);
}
