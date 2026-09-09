import AsyncStorage from '@react-native-async-storage/async-storage';

import { API_URL } from '@/constants/api';

/** Paleta do tema (espelha PaletaTema no back). Cores hex, exceto brandRgb ("r, g, b"). */
export interface PaletaTema {
  brand: string;
  brandDeep: string;
  brandPine: string;
  glow: string;
  onBrand: string;
  brandRgb: string;
  /** Fundo da aplicação: tom bem claro da marca. */
  bg: string;
}

const CHAVE = 'tema-paleta';

/** Paleta em cache (para aplicar instantaneamente no boot), ou null se não houver. */
export async function paletaEmCache(): Promise<PaletaTema | null> {
  try {
    const raw = await AsyncStorage.getItem(CHAVE);
    return raw ? (JSON.parse(raw) as PaletaTema) : null;
  } catch {
    return null;
  }
}

/**
 * Busca a paleta pública em GET /tema (a cor da plataforma, derivada no backend) e a
 * guarda em cache. Retorna null em qualquer falha — o app segue com o tema padrão/cacheado.
 */
export async function buscarPaleta(): Promise<PaletaTema | null> {
  try {
    const resposta = await fetch(`${API_URL}/tema`);
    if (!resposta.ok) return null;
    const paleta = (await resposta.json()) as PaletaTema;
    try {
      await AsyncStorage.setItem(CHAVE, JSON.stringify(paleta));
    } catch {
      /* sem cache: sem problema */
    }
    return paleta;
  } catch {
    return null;
  }
}
