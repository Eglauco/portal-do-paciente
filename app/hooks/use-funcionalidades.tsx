import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

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
 * Provê o kill switch global de telas a todo o app. No boot aplica o cache na hora e busca o
 * mapa atual de GET /funcionalidades; o padrão é tudo habilitado, então nada some no caso comum.
 * A mudança do admin vale no próximo abrir do app (mesmo padrão do {@link TemaProvider}).
 */
export function FuncionalidadesProvider({ children }: { children: ReactNode }) {
  const [telas, setTelas] = useState<TelasHabilitadas>(() => todasHabilitadas());

  useEffect(() => {
    let vivo = true;
    telasEmCache().then((t) => {
      if (vivo && t) setTelas(t);
    });
    buscarTelas().then((t) => {
      if (vivo && t) setTelas(t);
    });
    return () => {
      vivo = false;
    };
  }, []);

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
