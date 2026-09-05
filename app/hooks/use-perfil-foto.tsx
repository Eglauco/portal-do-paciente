import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

import { useSessao } from '@/hooks/use-sessao';
import { carregarPerfil } from '@/services/perfil';

interface PerfilFotoContexto {
  /** URL (pré-assinada) da foto do paciente, ou null se não houver. */
  fotoUrl: string | null;
  /** Atualiza a foto compartilhada (ex.: após trocar no "Meu perfil"). */
  definirFoto: (url: string | null) => void;
}

const Contexto = createContext<PerfilFotoContexto | undefined>(undefined);

/**
 * Guarda a foto do paciente para compartilhar entre o cabeçalho (TopBar, em todas
 * as abas) e o "Meu perfil". Busca uma vez por sessão — o perfil atualiza esta
 * foto ao trocá-la, então o cabeçalho reflete a mudança sem novo fetch.
 */
export function PerfilFotoProvider({ children }: { children: ReactNode }) {
  const { sessao } = useSessao();
  const [fotoUrl, setFotoUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!sessao) {
      setFotoUrl(null);
      return;
    }
    let vivo = true;
    carregarPerfil()
      .then((p) => {
        if (vivo) setFotoUrl(p.fotoUrl);
      })
      .catch(() => {}); // silencioso: cai no ícone padrão
    return () => {
      vivo = false;
    };
  }, [sessao?.pacienteId]);

  return <Contexto.Provider value={{ fotoUrl, definirFoto: setFotoUrl }}>{children}</Contexto.Provider>;
}

export function usePerfilFoto(): PerfilFotoContexto {
  const ctx = useContext(Contexto);
  if (!ctx) {
    throw new Error('usePerfilFoto deve ser usado dentro de <PerfilFotoProvider>');
  }
  return ctx;
}
