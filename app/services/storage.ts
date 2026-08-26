import { API_URL } from '@/constants/api';

/** Pede ao backend uma URL assinada e temporária para baixar/abrir o arquivo. */
export async function urlDownload(url: string): Promise<string> {
  const resposta = await fetch(`${API_URL}/storage/download-url?url=${encodeURIComponent(url)}`);
  if (!resposta.ok) {
    throw new Error(`Falha ao gerar o link (${resposta.status})`);
  }
  const dados = (await resposta.json()) as { url: string };
  return dados.url;
}
