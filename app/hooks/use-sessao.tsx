import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

import { registrarParaPush } from '@/services/notificacoes';
import {
  ativar as ativarServico,
  carregarSessao,
  definirSenha as definirSenhaServico,
  iniciarLogin as iniciarLoginServico,
  loginPorSenha as loginPorSenhaServico,
  registrarInvalidacao,
  registrarTrocaPerfil,
  sair as sairServico,
  solicitarCodigo as solicitarCodigoServico,
  trocarPerfil as trocarPerfilServico,
  type InicioLogin,
  type SessaoPaciente,
} from '@/services/sessao';

interface SessaoContexto {
  /** Sessão do paciente logado, ou null se não há ninguém logado. */
  sessao: SessaoPaciente | null;
  /** Enquanto lê a sessão guardada no aparelho (evita piscar a tela de login). */
  carregando: boolean;
  /**
   * Pré-checagem do login (sem SMS): confere a identidade e diz se a conta já tem senha
   * (para entrar sem SMS) e se o login por senha está bloqueado por tentativas.
   */
  iniciarLogin: (cpf: string, dataNascimento: string, telefone: string) => Promise<InicioLogin>;
  /** Login por senha (PIN), sem SMS. Guarda a sessão (leva à tela de perfis). */
  loginPorSenha: (cpf: string, dataNascimento: string, telefone: string, senha: string) => Promise<void>;
  /**
   * Pede o código de ativação por SMS a partir do telefone + CPF + data de nascimento
   * (ISO "AAAA-MM-DD"); devolve o telefone mascarado do dono.
   */
  solicitarCodigo: (cpf: string, dataNascimento: string, telefone: string) => Promise<string>;
  ativar: (cpf: string, dataNascimento: string, codigo: string, telefone: string) => Promise<void>;
  /** Define a senha (PIN) inicial após o OTP (obrigatório antes de escolher o perfil). */
  definirSenha: (senha: string) => Promise<void>;
  /** Escolhe o perfil ativo (tela "Selecionar Perfil"). Nunca refaz OTP. */
  trocarPerfil: (pacienteId: number) => Promise<void>;
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
    // Permite trocar de perfil de fora do React (ex.: toque em notificação de outro perfil).
    registrarTrocaPerfil(trocarPerfil);
    return () => {
      vivo = false;
      registrarInvalidacao(null);
      registrarTrocaPerfil(null);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Vincula o token de push a este paciente sempre que há sessão (login novo ou
  // sessão restaurada no arranque).
  useEffect(() => {
    if (sessao) registrarParaPush().catch(() => {});
  }, [sessao?.pacienteId]);

  async function iniciarLogin(cpf: string, dataNascimento: string, telefone: string) {
    return iniciarLoginServico(cpf, dataNascimento, telefone);
  }

  async function loginPorSenha(cpf: string, dataNascimento: string, telefone: string, senha: string) {
    setSessao(await loginPorSenhaServico(cpf, dataNascimento, telefone, senha));
  }

  async function solicitarCodigo(cpf: string, dataNascimento: string, telefone: string) {
    return solicitarCodigoServico(cpf, dataNascimento, telefone);
  }

  async function ativar(cpf: string, dataNascimento: string, codigo: string, telefone: string) {
    setSessao(await ativarServico(cpf, dataNascimento, codigo, telefone));
  }

  async function definirSenha(senha: string) {
    const atualizada = await definirSenhaServico(senha);
    if (atualizada) setSessao({ ...atualizada });
  }

  async function trocarPerfil(pacienteId: number) {
    setSessao(await trocarPerfilServico(pacienteId));
  }

  async function sair() {
    await sairServico();
    setSessao(null);
  }

  return (
    <Contexto.Provider
      value={{
        sessao,
        carregando,
        iniciarLogin,
        loginPorSenha,
        solicitarCodigo,
        ativar,
        definirSenha,
        trocarPerfil,
        sair,
      }}>
      {children}
    </Contexto.Provider>
  );
}

export function useSessao(): SessaoContexto {
  const ctx = useContext(Contexto);
  if (!ctx) {
    throw new Error('useSessao deve ser usado dentro de <SessaoProvider>');
  }
  return ctx;
}
