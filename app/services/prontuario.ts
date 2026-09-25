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

/** Termo (TCLE) a assinar dentro de um prontuário. */
export interface TermoAssinaturaApi {
  id: number;
  nome: string;
  status: 'PENDENTE' | 'EM_CONFIRMACAO' | 'ASSINADO' | 'TENTAR_NOVAMENTE' | 'CANCELADO';
  statusDescricao: string;
  criadoEm: string;
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
  /** Termos (TCLE) pendentes/assinados deste atendimento. */
  termos: TermoAssinaturaApi[];
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

/**
 * Inicia a assinatura de TODOS os termos pendentes de um atendimento (prontuário) numa cerimônia só
 * e devolve o sign_url da ZapSign. 1 termo = documento único; vários = assinatura em lote (mesmo link).
 * O backend cria os documentos com as variáveis já substituídas.
 */
export async function iniciarAssinaturaLote(prontuarioId: number): Promise<string> {
  const resposta = await fetchMeu(`/meu/termos/prontuario/${prontuarioId}/assinar`, { method: 'POST' });
  const dados = await comoJson<{ signUrl: string }>(resposta);
  return dados.signUrl;
}

/**
 * Marca os termos do atendimento como "Assinatura em confirmação" — chamado assim que a cerimônia
 * conclui (evento zs-doc-signed), para o botão sumir na hora e não deixar reassinar.
 */
export async function marcarEmConfirmacao(prontuarioId: number): Promise<TermoAssinaturaApi[]> {
  const resposta = await fetchMeu(`/meu/termos/prontuario/${prontuarioId}/em-confirmacao`, { method: 'POST' });
  return comoJson<TermoAssinaturaApi[]>(resposta);
}

/** Endpoint leve: relê os termos do atendimento (usado no loop de conferência da tela). */
export async function conferirTermos(prontuarioId: number): Promise<TermoAssinaturaApi[]> {
  const resposta = await fetchMeu(`/meu/termos/prontuario/${prontuarioId}`);
  return comoJson<TermoAssinaturaApi[]>(resposta);
}
