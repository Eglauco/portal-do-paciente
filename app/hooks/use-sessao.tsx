import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

import { registrarParaPush } from '@/services/notificacoes';
import {
  ativar as ativarServico,
  carregarSessao,
  registrarInvalidacao,
  sair as sairServico,
  type SessaoPaciente,
} from '@/services/sessao';

interface SessaoContexto {
  /** Sessão do paciente logado, ou null se não há ninguém logado. */
  sessao: SessaoPaciente | null;
  /** Enquanto lê a sessão guardada no aparelho (evita piscar a tela de login). */
  carregando: boolean;
  ativar: (telefone: string, codigo: string) => Promise<void>;
  sair: () => Promise<void>;
}

const Contexto = createContext<SessaoContexto | undefined>(undefined);

export function SessaoProvider({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<SessaoPaciente | null>(null);
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    let vivo = true;
    carregarSessao().then((s) => {
      if (!vivo) return;
      setSessao(s);
      setCarregando(false);
    });
    // Se o backend recusar o token (401), a sessão local é encerrada e o app volta ao login.
    registrarInvalidacao(() => setSessao(null));
    return () => {
      vivo = false;
      registrarInvalidacao(null);
    };
  }, []);

  async function ativar(telefone: string, codigo: string) {
    setSessao(await ativarServico(telefone, codigo));
    // Revincula o token de push a este paciente (agora que há sessão).
    registrarParaPush().catch(() => {});
  }

  async function sair() {
    await sairServico();
    setSessao(null);
  }

  return (
    <Contexto.Provider value={{ sessao, carregando, ativar, sair }}>{children}</Contexto.Provider>
  );
}

export function useSessao(): SessaoContexto {
  const ctx = useContext(Contexto);
  if (!ctx) {
    throw new Error('useSessao deve ser usado dentro de <SessaoProvider>');
  }
  return ctx;
}
