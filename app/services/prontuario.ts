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
  status: 'PENDENTE' | 'EM_CONFIRMACAO' | 'AGUARDANDO_PROFISSIONAL' | 'ASSINADO' | 'TENTAR_NOVAMENTE' | 'CANCELADO';
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
    throw new Error(await mensagemDeErro(resposta));
  }
  return resposta.json() as Promise<T>;
}

/** Extrai a mensagem de negócio do backend ({"message": "..."}) ou cai num texto genérico. */
async function mensagemDeErro(resposta: Response): Promise<string> {
  try {
    const corpo = (await resposta.json()) as { message?: unknown };
    if (typeof corpo?.message === 'string' && corpo.message.trim()) {
      return corpo.message;
    }
  } catch {
    // corpo não-JSON ou vazio: usa o genérico
  }
  return `Falha na requisição (${resposta.status})`;
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

/** Retorno do início da assinatura: os link(s) da cerimônia + o provedor usado. */
export interface InicioAssinatura {
  /** Um link (ZapSign / Autentique combinado / 1 termo) ou vários (Autentique separado, 1 por termo). */
  signUrls: string[];
  /** 'ZAPSIGN' | 'AUTENTIQUE' — decide como o app detecta o fim da cerimônia. */
  provedor: string;
}

/**
 * Inicia a assinatura de TODOS os termos pendentes de um atendimento (prontuário) e devolve os link(s) da
 * cerimônia + o provedor. O backend cria os documentos com as variáveis já substituídas. Na ZapSign é sempre
 * um link (cerimônia única); na Autentique pode ser um (combinado) ou vários (separado, um por termo).
 */
export async function iniciarAssinaturaLote(prontuarioId: number): Promise<InicioAssinatura> {
  const resposta = await fetchMeu(`/meu/termos/prontuario/${prontuarioId}/assinar`, { method: 'POST' });
  const dados = await comoJson<{ signUrl?: string; signUrls?: string[]; provedor?: string }>(resposta);
  const signUrls =
    dados.signUrls && dados.signUrls.length > 0 ? dados.signUrls : dados.signUrl ? [dados.signUrl] : [];
  return { signUrls, provedor: dados.provedor ?? 'ZAPSIGN' };
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

/** Cancela a confirmação: volta os termos "em confirmação" para pendente (quando não assinou de fato). */
export async function cancelarAssinatura(prontuarioId: number): Promise<void> {
  await fetchMeu(`/meu/termos/prontuario/${prontuarioId}/cancelar`, { method: 'POST' });
}
