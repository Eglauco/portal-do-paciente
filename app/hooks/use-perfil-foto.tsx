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
 * Guarda a foto do paciente para compartilhar entre o cabeçalho (TopBar, em todas as abas) e o
 * "Meu perfil". Rebusca a cada TROCA de token (perfil OU clínica) — o token é único por sessão/clínica;
 * o id do paciente NÃO serve de chave porque colide entre clínicas (ids por-schema no Design B), o que
 * deixaria a foto do perfil anterior no cabeçalho ao trocar de unidade. O "Meu perfil" atualiza esta
 * foto ao trocá-la (mesmo token) sem novo fetch.
 */
export function PerfilFotoProvider({ children }: { children: ReactNode }) {
  const { sessao } = useSessao();
  const [fotoUrl, setFotoUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!sessao) {
      setFotoUrl(null);
      return;
    }
    // Zera ANTES de buscar: na troca de perfil/clínica o cabeçalho nunca mostra a foto do perfil anterior
    // (evita exibir o rosto errado enquanto a nova foto não chega).
    setFotoUrl(null);
    let vivo = true;
    carregarPerfil()
      .then((p) => {
        if (vivo) setFotoUrl(p.fotoUrl);
      })
      .catch(() => {}); // silencioso: cai no ícone padrão
    return () => {
      vivo = false;
    };
    // Chave de propósito: só o TOKEN (perfil/clínica). Não depende do objeto `sessao` inteiro, que muda
    // de identidade a cada setSessao e rebuscaria à toa.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessao?.token]);

  return <Contexto.Provider value={{ fotoUrl, definirFoto: setFotoUrl }}>{children}</Contexto.Provider>;
}

export function usePerfilFoto(): PerfilFotoContexto {
  const ctx = useContext(Contexto);
  if (!ctx) {
    throw new Error('usePerfilFoto deve ser usado dentro de <PerfilFotoProvider>');
  }
  return ctx;
}
