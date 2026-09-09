import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { Brand } from '@/constants/theme';
import { buscarPaleta, paletaEmCache, type PaletaTema } from '@/services/tema';

/**
 * Tema em runtime — as chaves de {@link Brand} + tons de superfície derivados da marca
 * ({@code brandTint}/{@code brandTintStrong}), para os micro-tons também seguirem a cor.
 */
export type Tema = typeof Brand & {
  /** Superfície bem clara tingida pela marca (fundos de ícone/avatar, pressed, cards). */
  brandTint: string;
  /** Superfície um pouco mais forte da marca (pills/realces suaves). */
  brandTintStrong: string;
};

// ---------- utilidades de cor (RN não tem color-mix) ----------

function canais(hex: string): [number, number, number] {
  const h = hex.replace('#', '');
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

/** Mistura duas cores hex: t=0 → a, t=1 → b. Ex.: mix(marca, '#FFFFFF', 0.9) = tint claro. */
export function mix(a: string, b: string, t: number): string {
  const ca = canais(a);
  const cb = canais(b);
  const m = ca.map((x, i) => Math.round(x + (cb[i] - x) * t));
  return `#${m.map((x) => x.toString(16).padStart(2, '0')).join('')}`;
}

/** Cor hex com opacidade → "rgba(r, g, b, alfa)". */
export function alpha(hex: string, a: number): string {
  const [r, g, b] = canais(hex);
  return `rgba(${r}, ${g}, ${b}, ${a})`;
}

/**
 * Aplica a paleta buscada sobre os tons FIXOS. Seguem a cor: brand/brandDeep/brandPine/
 * glow/onBrand + fundo (bg) + as superfícies tint derivadas. Neutros (surface/ink/muted/
 * line) e semânticos ficam fixos.
 */
function comPaleta(p: PaletaTema | null): Tema {
  const base = p
    ? {
        ...Brand,
        bg: p.bg,
        brand: p.brand,
        brandDeep: p.brandDeep,
        brandPine: p.brandPine,
        glow: p.glow,
        onBrand: p.onBrand,
      }
    : Brand;
  return {
    ...base,
    brandTint: mix(base.brand, '#FFFFFF', 0.9),
    brandTintStrong: mix(base.brand, '#FFFFFF', 0.82),
  };
}

const Contexto = createContext<Tema>(comPaleta(null));

/**
 * Provê o tema (cor da plataforma) a todo o app. No boot aplica a paleta em cache na hora e
 * busca a atual de GET /tema; o padrão é {@link Brand} (verde), então nada pisca no caso comum.
 * A mudança do admin vale no próximo abrir do app.
 */
export function TemaProvider({ children }: { children: ReactNode }) {
  const [paleta, setPaleta] = useState<PaletaTema | null>(null);

  useEffect(() => {
    let vivo = true;
    paletaEmCache().then((p) => {
      if (vivo && p) setPaleta(p);
    });
    buscarPaleta().then((p) => {
      if (vivo && p) setPaleta(p);
    });
    return () => {
      vivo = false;
    };
  }, []);

  const tema = useMemo(() => comPaleta(paleta), [paleta]);
  return <Contexto.Provider value={tema}>{children}</Contexto.Provider>;
}

/** Cores do tema atual. Fora de um TemaProvider, devolve o padrão derivado de {@link Brand}. */
export function useTema(): Tema {
  return useContext(Contexto);
}
