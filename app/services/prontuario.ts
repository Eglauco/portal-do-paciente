import { fetchMeu } from '@/services/sessao';

interface Ref {
  id: number;
  nome: string;
}

export interface DocumentoApi {
  id: number;
  nome: string;
  url?: string | null;
}

/** Item da listagem de prontuários. */
interface ProntuarioItem {
  id: number;
  numeroAtendimento: string;
  agendamentoId: number;
  paciente: Ref;
  especialidade: Ref;
  unidadeSaude: Ref;
  dataHora: string;
  documentos: number;
}

/** Detalhe do prontuário (com a lista de documentos). */
export interface ProntuarioDetalhe {
  id: number;
  numeroAtendimento: string;
  agendamentoId: number;
  paciente: Ref;
  especialidade: Ref;
  profissionalSaude: Ref;
  unidadeSaude: Ref;
  dataHora: string;
  documentos: DocumentoApi[];
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

/**
 * Lista todos os prontuários com seus documentos (mais recentes primeiro).
 * A listagem traz apenas a contagem de documentos, então buscamos o detalhe
 * de cada prontuário para obter os documentos.
 */
export async function listarProntuarios(): Promise<ProntuarioDetalhe[]> {
  const resposta = await fetchMeu('/meu/prontuarios?page=0&size=100');
  const pagina = await comoJson<Pagina<ProntuarioItem>>(resposta);

  const detalhes = await Promise.all(
    pagina.content.map((p) =>
      fetchMeu(`/meu/prontuarios/${p.id}`).then((r) => comoJson<ProntuarioDetalhe>(r)),
    ),
  );

  // Ordena por data do atendimento (mais recente primeiro).
  return detalhes.sort((a, b) => b.dataHora.localeCompare(a.dataHora));
}
