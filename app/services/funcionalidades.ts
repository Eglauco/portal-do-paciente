import AsyncStorage from '@react-native-async-storage/async-storage';

import { API_URL } from '@/constants/api';
import type { FuncionalidadeApp } from '@/services/sessao';

/**
 * Kill switch global de telas: mapa tela → habilitada. `true` = visível; `false` = desligada
 * globalmente pelo admin (some para TODOS, inclusive o perfil próprio). Espelha o JSON de
 * GET /funcionalidades (`{ telas: { AGENDAMENTOS: true, ... } }`).
 */
export type TelasHabilitadas = Record<FuncionalidadeApp, boolean>;

const CHAVE = 'funcionalidades-telas';

/**
 * Fallback SEGURO: tudo habilitado. Usado enquanto carrega e em qualquer erro — nunca
 * escondemos uma tela por falha de rede (o kill switch só desliga com um `false` explícito).
 */
export function todasHabilitadas(): TelasHabilitadas {
  return {
    AGENDAMENTOS: true,
    CHAT: true,
    SAU: true,
    REDE_SOCIAL: true,
    MEU_PERFIL: true,
    PRONTUARIO: true,
    NPS: true,
  };
}

/**
 * Normaliza o mapa recebido: parte de tudo habilitado e só desliga o que vier explicitamente
 * `false`. Chave ausente/desconhecida → fica habilitada (não corta tela nova por engano).
 */
function normalizar(bruto: Partial<Record<string, boolean>> | null | undefined): TelasHabilitadas {
  const base = todasHabilitadas();
  if (!bruto) return base;
  for (const chave of Object.keys(base) as FuncionalidadeApp[]) {
    if (bruto[chave] === false) base[chave] = false;
  }
  return base;
}

/** Telas em cache (para aplicar instantaneamente no boot), ou null se não houver. */
export async function telasEmCache(): Promise<TelasHabilitadas | null> {
  try {
    const raw = await AsyncStorage.getItem(CHAVE);
    return raw ? normalizar(JSON.parse(raw) as Record<string, boolean>) : null;
  } catch {
    return null;
  }
}

/**
 * Busca o mapa público em GET /funcionalidades (o kill switch do admin) e o guarda em cache.
 * Retorna null em qualquer falha — o app segue com o cache/fallback (tudo habilitado).
 */
export async function buscarTelas(): Promise<TelasHabilitadas | null> {
  try {
    const resposta = await fetch(`${API_URL}/funcionalidades`);
    if (!resposta.ok) return null;
    const dados = (await resposta.json()) as { telas?: Record<string, boolean> };
    const telas = normalizar(dados.telas);
    try {
      await AsyncStorage.setItem(CHAVE, JSON.stringify(telas));
    } catch {
      /* sem cache: sem problema */
    }
    return telas;
  } catch {
    return null;
  }
}
