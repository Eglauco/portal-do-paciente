import { API_URL, inquilinoHeader } from '@/constants/api';

/**
 * Versão: número (semver) + data do release em ISO (yyyy-MM-dd). Espelha o JSON de GET /versao,
 * que é PÚBLICO (sem token), como /tema e /marca. Cada componente é dono só da SUA versão: o back
 * expõe a dele aqui; o app lê a própria do `app.json` (via expo-constants).
 */
export interface VersaoInfo {
  versao: string;
  /** Data do release em ISO yyyy-MM-dd (formate com {@link formatarData}). */
  data: string;
}

/**
 * Busca a versão do BACKEND em GET /versao. Endpoint PÚBLICO (sem auth), igual a /tema e /marca —
 * manda só o X-Inquilino assado (quando houver). Retorna null em qualquer falha (fail-soft): a tela
 * Sobre segue mostrando ao menos a versão instalada do app (expo-constants).
 */
export async function buscarVersaoBack(): Promise<VersaoInfo | null> {
  try {
    const resposta = await fetch(`${API_URL}/versao`, { headers: { ...inquilinoHeader() } });
    if (!resposta.ok) return null;
    return (await resposta.json()) as VersaoInfo;
  } catch {
    return null;
  }
}

/**
 * ISO yyyy-MM-dd → dd/MM/aaaa (função PURA). Faz split('-') e remonta a string: NÃO usa
 * `new Date('yyyy-MM-dd')`, que interpreta como UTC e desloca o dia conforme o fuso. Entrada
 * vazia/inválida → "-".
 */
export function formatarData(iso: string | null | undefined): string {
  if (!iso) return '-';
  const [ano, mes, dia] = iso.split('-');
  if (!ano || !mes || !dia) return '-';
  return `${dia}/${mes}/${ano}`;
}
