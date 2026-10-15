import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

import { registrarParaPush } from '@/services/notificacoes';
import {
  ativar as ativarServico,
  carregarSessao,
  definirSenha as definirSenhaServico,
  iniciarLogin as iniciarLoginServico,
  loginPorSenha as loginPorSenhaServico,
  minhasClinicas as minhasClinicasServico,
  preChecagem as preChecagemServico,
  registrarInvalidacao,
  registrarTrocaPerfil,
  sair as sairServico,
  solicitarCodigo as solicitarCodigoServico,
  trocarClinica as trocarClinicaServico,
  trocarPerfil as trocarPerfilServico,
  type ClinicaLogin,
  type InicioLogin,
  type InicioOtp,
  type ResultadoLogin,
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
  /**
   * Pré-checagem SÓ por CPF: diz se o CPF já tem senha (PIN) central, para rotear a tela
   * (senha × primeiro acesso). Não revela se é paciente desta clínica (anti-enumeração).
   */
  preChecagem: (cpf: string) => Promise<{ temSenha: boolean }>;
  /**
   * Login por senha (PIN), sem SMS. No app A-baked (ou com a clínica já escolhida) guarda a sessão; no app
   * compartilhado com o CPF em >1 clínica, devolve a lista pra escolher (o chamador abre o seletor).
   */
  loginPorSenha: (cpf: string, senha: string, inquilinoId?: number) => Promise<ResultadoLogin>;
  /**
   * Pede o código de ativação por SMS a partir do telefone + CPF + data de nascimento
   * (ISO "AAAA-MM-DD"); devolve o telefone mascarado do dono.
   */
  solicitarCodigo: (cpf: string, dataNascimento: string, telefone: string) => Promise<InicioOtp>;
  ativar: (
    cpf: string,
    dataNascimento: string,
    codigo: string,
    telefone: string,
    inquilinoId?: number,
  ) => Promise<void>;
  /** Define a senha (PIN) inicial após o OTP (obrigatório antes de escolher o perfil). */
  definirSenha: (senha: string) => Promise<void>;
  /** Escolhe o perfil ativo (tela "Selecionar Perfil"). Nunca refaz OTP. */
  trocarPerfil: (pacienteId: number) => Promise<void>;
  /** Clínicas (ativas) do CPF logado — app compartilhado, para oferecer "trocar unidade". */
  minhasClinicas: () => Promise<ClinicaLogin[]>;
  /**
   * Troca a clínica da sessão (app compartilhado) sem refazer login. Devolve a sessão nova para a tela
   * decidir o destino: clínica de 1 perfil (perfilSelecionado=true) → app; mais de 1 → "Selecionar Perfil".
   */
  trocarClinica: (inquilinoId: number) => Promise<SessaoPaciente>;
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

  async function preChecagem(cpf: string) {
    return preChecagemServico(cpf);
  }

  async function loginPorSenha(cpf: string, senha: string, inquilinoId?: number) {
    const resultado = await loginPorSenhaServico(cpf, senha, inquilinoId);
    if ('sessao' in resultado) {
      setSessao(resultado.sessao);
    }
    return resultado;
  }

  async function solicitarCodigo(cpf: string, dataNascimento: string, telefone: string) {
    return solicitarCodigoServico(cpf, dataNascimento, telefone);
  }

  async function ativar(
    cpf: string,
    dataNascimento: string,
    codigo: string,
    telefone: string,
    inquilinoId?: number,
  ) {
    setSessao(await ativarServico(cpf, dataNascimento, codigo, telefone, inquilinoId));
  }

  async function definirSenha(senha: string) {
    const atualizada = await definirSenhaServico(senha);
    if (atualizada) setSessao({ ...atualizada });
  }

  async function trocarPerfil(pacienteId: number) {
    setSessao(await trocarPerfilServico(pacienteId));
  }

  async function minhasClinicas() {
    return minhasClinicasServico();
  }

  async function trocarClinica(inquilinoId: number) {
    const nova = await trocarClinicaServico(inquilinoId);
    setSessao(nova);
    return nova;
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
        preChecagem,
        loginPorSenha,
        solicitarCodigo,
        ativar,
        definirSenha,
        trocarPerfil,
        minhasClinicas,
        trocarClinica,
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
