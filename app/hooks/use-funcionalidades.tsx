import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

import { useSessao } from '@/hooks/use-sessao';
import {
  buscarTelas,
  telasEmCache,
  todasHabilitadas,
  type TelasHabilitadas,
} from '@/services/funcionalidades';
import type { FuncionalidadeApp } from '@/services/sessao';

interface FuncionalidadesContexto {
  /** Mapa tela → habilitada globalmente pelo admin (default: tudo habilitado). */
  telas: TelasHabilitadas;
  /**
   * true se a tela está ligada globalmente. Default true (nunca esconde por erro/carregamento);
   * o kill switch só devolve false quando o backend mandou `false` para aquela tela.
   */
  telaHabilitada: (func: FuncionalidadeApp) => boolean;
}

const Contexto = createContext<FuncionalidadesContexto>({
  telas: todasHabilitadas(),
  telaHabilitada: () => true,
});

/**
 * Provê o kill switch global de telas a todo o app. Aplica o cache na hora (sem flash) e busca o mapa
 * de GET /funcionalidades seguindo a SESSÃO: logado, manda o token → o backend resolve o INQUILINO e
 * devolve o kill switch DELE; deslogado → tudo habilitado. Re-busca a cada login/logout/troca de perfil
 * (mudança do token). DEVE ficar DENTRO do {@code SessaoProvider} (usa {@link useSessao}).
 */
export function FuncionalidadesProvider({ children }: { children: ReactNode }) {
  const [telas, setTelas] = useState<TelasHabilitadas>(() => todasHabilitadas());
  const { sessao, carregando } = useSessao();

  // Cache local (sem flash) — uma vez, no arranque.
  useEffect(() => {
    let vivo = true;
    telasEmCache().then((t) => {
      if (vivo && t) setTelas(t);
    });
    return () => {
      vivo = false;
    };
  }, []);

  // Re-busca quando a sessão resolve/muda (token): kill switch do inquilino logado (com token já no lugar).
  useEffect(() => {
    if (carregando) return;
    let vivo = true;
    buscarTelas().then((t) => {
      if (vivo && t) setTelas(t);
    });
    return () => {
      vivo = false;
    };
  }, [carregando, sessao?.token]);

  const valor = useMemo<FuncionalidadesContexto>(
    () => ({ telas, telaHabilitada: (func) => telas[func] ?? true }),
    [telas],
  );
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

/** Kill switch global de telas. Fora de um FuncionalidadesProvider, tudo é habilitado. */
export function useFuncionalidades(): FuncionalidadesContexto {
  return useContext(Contexto);
}
