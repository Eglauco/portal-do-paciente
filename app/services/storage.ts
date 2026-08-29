import { fetchMeu } from '@/services/sessao';

/**
 * Pede ao backend uma URL assinada e temporária para baixar/abrir um documento
 * do prontuário do paciente logado (o backend confere que o documento é dele).
 */
export async function urlDownload(url: string): Promise<string> {
  const resposta = await fetchMeu(`/meu/prontuarios/documento/download-url?url=${encodeURIComponent(url)}`);
  if (!resposta.ok) {
    throw new Error(`Falha ao gerar o link (${resposta.status})`);
  }
  const dados = (await resposta.json()) as { url: string };
  return dados.url;
}
