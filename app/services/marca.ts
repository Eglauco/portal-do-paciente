import AsyncStorage from '@react-native-async-storage/async-storage';

import { API_URL, INQUILINO, inquilinoHeader } from '@/constants/api';
import { authHeaders } from '@/services/sessao';

/**
 * Marca (white-label) da tela de login: nome + logo. O backend (GET /marca) resolve pelo inquilino
 * (pós-login, ou pré-login via X-Inquilino assado) com fallback à plataforma e ao padrão.
 */
export interface Marca {
  /** Nome exibido no wordmark do login (config NOME_PLATAFORMA → inquilino/plataforma/padrão). */
  nomePlataforma: string;
  /** URL (assinada, 24h) da logomarca (config LOGO_PLATAFORMA); null → o app usa o ícone padrão. */
  logoUrl: string | null;
}

// Cache escopado pelo contexto de marca do PRÉ-LOGIN: inquilino assado (A-baked) ou "plataforma"
// (app compartilhado). Sem o escopo, trocar o inquilino do build — ou ir de A-baked para compartilhado —
// mostraria a marca da clínica anterior (ex.: "Portal nascente" no app compartilhado) até um fetch novo
// dar certo, e PARA SEMPRE se o fetch falhar. Com a chave por inquilino, o miss cai no padrão, não no stale.
const CHAVE = `marca-dados:${INQUILINO ?? 'plataforma'}`;

/** Marca em cache (para pintar o wordmark na hora no boot, sem flash), ou null. */
export async function marcaEmCache(): Promise<Marca | null> {
  try {
    const raw = await AsyncStorage.getItem(CHAVE);
    return raw ? (JSON.parse(raw) as Marca) : null;
  } catch {
    return null;
  }
}

/**
 * Busca a marca em GET /marca e a guarda em cache. Envia o X-Inquilino assado (branding da clínica no
 * pré-login) e o token quando logado. Retorna null em qualquer falha — o app segue com o cache/padrão.
 */
export async function buscarMarca(): Promise<Marca | null> {
  try {
    const resposta = await fetch(`${API_URL}/marca`, { headers: { ...inquilinoHeader(), ...authHeaders() } });
    if (!resposta.ok) return null;
    const dados = (await resposta.json()) as { nomePlataforma?: string; logoUrl?: string | null };
    const marca: Marca = {
      nomePlataforma: dados.nomePlataforma?.trim() || 'Portal do Paciente',
      logoUrl: dados.logoUrl ?? null,
    };
    try {
      await AsyncStorage.setItem(CHAVE, JSON.stringify(marca));
    } catch {
      /* sem cache: sem problema */
    }
    return marca;
  } catch {
    return null;
  }
}
