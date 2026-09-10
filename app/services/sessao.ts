import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

import { API_URL } from '@/constants/api';
import { obterDispositivoId } from './identidade';

const CHAVE = 'pop.sessaoPaciente';

/** Funcionalidade do app sujeita à permissão do responsável (espelha o enum do backend). */
export type FuncionalidadeApp =
  | 'AGENDAMENTOS'
  | 'CHAT'
  | 'SAU'
  | 'REDE_SOCIAL'
  | 'MEU_PERFIL'
  | 'PRONTUARIO'
  | 'NPS';

/** Nível de acesso a uma funcionalidade. */
export type NivelAcesso = 'SEM_ACESSO' | 'VISUALIZAR' | 'VISUALIZAR_LANCAR';

/** Um perfil (paciente) que a conta pode acessar na tela "Selecionar Perfil". */
export interface Perfil {
  pacienteId: number;
  nome: string;
  fotoUrl: string | null;
  proprio: boolean;
  /**
   * Nível por funcionalidade quando é um perfil DEPENDENTE (do backend). Perfil próprio
   * não traz (acesso total). Ausente = permissões ainda não carregadas (não trava a UI —
   * o backend é a autoridade); presente e sem a chave = SEM_ACESSO àquela funcionalidade.
   */
  permissoes?: Partial<Record<FuncionalidadeApp, NivelAcesso>>;
}

/**
 * Nível de acesso do perfil ATIVO numa funcionalidade. Perfil próprio (ou permissões
 * ainda não carregadas) → acesso total (o backend reforça); dependente → o que o
 * responsável recebeu, com ausência da chave = SEM_ACESSO.
 */
export function nivelAtual(sessao: SessaoPaciente | null, funcionalidade: FuncionalidadeApp): NivelAcesso {
  if (!sessao) return 'VISUALIZAR_LANCAR';
  const ativo = sessao.perfis.find((p) => p.pacienteId === sessao.pacienteId);
  if (!ativo || ativo.proprio || !ativo.permissoes) return 'VISUALIZAR_LANCAR';
  return ativo.permissoes[funcionalidade] ?? 'SEM_ACESSO';
}

/** O perfil ativo pode ao menos VISUALIZAR a funcionalidade. */
export function podeVer(sessao: SessaoPaciente | null, funcionalidade: FuncionalidadeApp): boolean {
  return nivelAtual(sessao, funcionalidade) !== 'SEM_ACESSO';
}

/**
 * true se o perfil ATIVO é o PRÓPRIO paciente (não um responsável agindo por outro).
 * Só o próprio paciente gerencia as pessoas autorizadas (o backend também recusa com 403).
 * Sessão antiga sem a lista de perfis → assume próprio; o backend é a autoridade.
 */
export function ehPerfilProprio(sessao: SessaoPaciente | null): boolean {
  if (!sessao) return false;
  const ativo = sessao.perfis.find((p) => p.pacienteId === sessao.pacienteId);
  return ativo ? ativo.proprio : true;
}

/** O perfil ativo pode fazer LANÇAMENTOS na funcionalidade (Visualizar e lançar). */
export function podeLancar(sessao: SessaoPaciente | null, funcionalidade: FuncionalidadeApp): boolean {
  return nivelAtual(sessao, funcionalidade) === 'VISUALIZAR_LANCAR';
}

type RotaAba =
  | '/(tabs)/agendamentos'
  | '/(tabs)/chat'
  | '/(tabs)/sau'
  | '/(tabs)/novidades'
  | '/(tabs)/prontuario'
  | '/(tabs)/nps';

/**
 * Aba inicial ao entrar no app / trocar de perfil: a primeira funcionalidade acessível
 * (Agenda por padrão para quem tem acesso). Se o perfil dependente não pode ver NENHUMA
 * aba (todas SEM_ACESSO), cai em "/perfil", que sempre mantém o botão "Selecionar perfil".
 * Evita aterrissar numa aba escondida (tela "Sem acesso").
 */
export function abaInicial(sessao: SessaoPaciente | null): RotaAba | '/perfil' {
  const ordem: [FuncionalidadeApp, RotaAba][] = [
    ['AGENDAMENTOS', '/(tabs)/agendamentos'],
    ['CHAT', '/(tabs)/chat'],
    ['SAU', '/(tabs)/sau'],
    ['REDE_SOCIAL', '/(tabs)/novidades'],
    ['PRONTUARIO', '/(tabs)/prontuario'],
    ['NPS', '/(tabs)/nps'],
  ];
  for (const [funcionalidade, rota] of ordem) {
    if (podeVer(sessao, funcionalidade)) return rota;
  }
  return '/perfil'; // nenhuma aba acessível: cai no perfil (sempre tem "Selecionar perfil")
}

export interface SessaoPaciente {
  /** Token do perfil ATIVO (carrega conta + perfil). */
  token: string;
  /** Perfil ativo (paciente por quem se está agindo). */
  pacienteId: number;
  nome: string;
  /** Perfis acessíveis pela conta (próprio + dependentes). */
  perfis: Perfil[];
  /** false logo após o OTP (precisa escolher na tela); true após escolher. */
  perfilSelecionado: boolean;
}

/** Cache em memória para leitura síncrona do token (cabeçalhos das requisições). */
let cache: SessaoPaciente | null = null;

/**
 * expo-secure-store é um módulo nativo (guarda o token cifrado). Existe no dev
 * build / app standalone, mas pode não estar disponível no Expo Go — e o import
 * dele avalia código nativo no topo do módulo, o que quebraria o app no arranque.
 * Por isso carregamos sob demanda e, se não houver, caímos no AsyncStorage.
 * Na web também usamos AsyncStorage (o app real roda no aparelho).
 */
type SecureStoreModulo = typeof import('expo-secure-store');
let secureStorePromise: Promise<SecureStoreModulo | null> | undefined;

function carregarSecureStore(): Promise<SecureStoreModulo | null> {
  if (!secureStorePromise) {
    secureStorePromise = (async () => {
      if (Platform.OS === 'web') return null;
      try {
        return await import('expo-secure-store');
      } catch {
        return null;
      }
    })();
  }
  return secureStorePromise;
}

async function lerBruto(): Promise<string | null> {
  const ss = await carregarSecureStore();
  return ss ? ss.getItemAsync(CHAVE) : AsyncStorage.getItem(CHAVE);
}
async function gravarBruto(valor: string): Promise<void> {
  const ss = await carregarSecureStore();
  if (ss) await ss.setItemAsync(CHAVE, valor);
  else await AsyncStorage.setItem(CHAVE, valor);
}
async function apagarBruto(): Promise<void> {
  const ss = await carregarSecureStore();
  if (ss) await ss.deleteItemAsync(CHAVE);
  else await AsyncStorage.removeItem(CHAVE);
}

/** Lê a sessão guardada no aparelho (chamada uma vez ao abrir o app). */
export async function carregarSessao(): Promise<SessaoPaciente | null> {
  if (cache) return cache;
  try {
    const bruto = await lerBruto();
    const obj = bruto ? JSON.parse(bruto) : null;
    if (!obj || !obj.token || obj.pacienteId == null) {
      cache = null;
    } else {
      cache = {
        token: obj.token,
        pacienteId: obj.pacienteId,
        nome: obj.nome ?? '',
        perfis: Array.isArray(obj.perfis) ? obj.perfis : [],
        // Sessão antiga (sem o campo): a pessoa já estava no app — não força a tela.
        perfilSelecionado: obj.perfilSelecionado ?? true,
      };
    }
  } catch {
    cache = null;
  }
  return cache;
}

async function persistir(): Promise<void> {
  if (cache) {
    try {
      await gravarBruto(JSON.stringify(cache));
    } catch {
      // silencioso: a sessão continua em memória mesmo se a gravação falhar
    }
  }
}

/**
 * Pede o código de ativação (OTP) por SMS. O telefone precisa ser de um paciente
 * OU de um responsável; senão → mensagem para procurar a unidade.
 */
export async function solicitarCodigo(telefone: string): Promise<void> {
  let resposta: Response;
  try {
    resposta = await fetch(`${API_URL}/paciente-auth/solicitar-codigo`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ telefone }),
    });
  } catch {
    throw new Error('Sem conexão. Verifique a internet e tente novamente.');
  }
  if (resposta.status === 404) {
    throw new Error('Telefone não encontrado no cadastro. Entre em contato com a sua unidade de saúde.');
  }
  if (!resposta.ok) {
    throw new Error('Não foi possível enviar o código agora. Tente novamente.');
  }
}

/**
 * Autentica o aparelho: envia telefone + código + id do aparelho. Guarda a sessão
 * com um perfil padrão e a lista de perfis; a tela "Selecionar Perfil" confirma a escolha.
 */
export async function ativar(telefone: string, codigo: string): Promise<SessaoPaciente> {
  const dispositivoId = await obterDispositivoId();
  let resposta: Response;
  try {
    resposta = await fetch(`${API_URL}/paciente-auth/ativar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ telefone, codigo, dispositivoId }),
    });
  } catch {
    throw new Error('Sem conexão. Verifique a internet e tente novamente.');
  }
  if (resposta.status === 401) {
    throw new Error('Telefone ou código inválido. Confira com a unidade de saúde.');
  }
  if (!resposta.ok) {
    throw new Error('Não foi possível entrar agora. Tente novamente.');
  }
  const dados = (await resposta.json()) as {
    token: string;
    pacienteId: number;
    nome: string;
    perfis?: Perfil[];
  };
  cache = {
    token: dados.token,
    pacienteId: dados.pacienteId,
    nome: dados.nome,
    perfis: dados.perfis ?? [],
    perfilSelecionado: false,
  };
  await persistir();
  return cache;
}

/** Perfis acessíveis pela conta (atualiza a sessão). Usa o token atual. */
export async function listarPerfis(): Promise<Perfil[]> {
  await carregarSessao();
  const cabecalhos = authHeaders();
  let resposta: Response;
  try {
    resposta = await fetch(`${API_URL}/paciente-auth/perfis`, { headers: cabecalhos });
  } catch {
    throw new Error('Sem conexão. Verifique a internet e tente novamente.');
  }
  if (resposta.status === 401 && cabecalhos.Authorization) {
    await sair();
    aoInvalidar?.();
    throw new Error('Sessão expirada. Entre novamente.');
  }
  if (!resposta.ok) {
    throw new Error('Não foi possível carregar os perfis.');
  }
  const perfis = (await resposta.json()) as Perfil[];
  if (cache) {
    cache = { ...cache, perfis };
    await persistir();
  }
  return perfis;
}

/** Escolhe o perfil ativo (reemite o token com o mesmo aparelho). Nunca refaz OTP. */
export async function trocarPerfil(pacienteId: number): Promise<SessaoPaciente> {
  await carregarSessao();
  const cabecalhos = authHeaders();
  let resposta: Response;
  try {
    resposta = await fetch(`${API_URL}/paciente-auth/trocar-perfil`, {
      method: 'POST',
      headers: { ...cabecalhos, 'Content-Type': 'application/json' },
      body: JSON.stringify({ pacienteId }),
    });
  } catch {
    throw new Error('Sem conexão. Verifique a internet e tente novamente.');
  }
  if (resposta.status === 401 && cabecalhos.Authorization) {
    await sair();
    aoInvalidar?.();
    throw new Error('Sessão expirada. Entre novamente.');
  }
  if (!resposta.ok) {
    throw new Error('Não foi possível selecionar o perfil. Tente novamente.');
  }
  const dados = (await resposta.json()) as { token: string; pacienteId: number; nome: string };
  cache = {
    token: dados.token,
    pacienteId: dados.pacienteId,
    nome: dados.nome,
    perfis: cache?.perfis ?? [],
    perfilSelecionado: true,
  };
  await persistir();
  return cache;
}

/** Callback do provider para trocar de perfil atualizando o estado do app (React). */
let solicitarTrocaPerfil: ((pacienteId: number) => Promise<void>) | null = null;
export function registrarTrocaPerfil(callback: ((pacienteId: number) => Promise<void>) | null): void {
  solicitarTrocaPerfil = callback;
}

/**
 * Troca para o perfil alvo se ele for diferente do ativo e acessível pela conta —
 * usado ao tocar numa notificação de outro perfil. Devolve true se trocou. No-op
 * (false) se já está nele, se o alvo não é acessível, ou se não há sessão.
 */
export async function trocarPerfilSeNecessario(pacienteId: number): Promise<boolean> {
  await carregarSessao();
  if (!cache || cache.pacienteId === pacienteId) return false;
  let acessivel = cache.perfis.some((p) => p.pacienteId === pacienteId);
  if (!acessivel) {
    // O perfil pode ter sido vinculado depois do login: atualiza a lista e reavalia.
    try {
      await listarPerfis();
    } catch {
      // sem rede: segue com o cache atual
    }
    acessivel = (cache?.perfis ?? []).some((p) => p.pacienteId === pacienteId);
  }
  if (!acessivel) return false;
  if (solicitarTrocaPerfil) await solicitarTrocaPerfil(pacienteId);
  else await trocarPerfil(pacienteId);
  return true;
}

/** Encerra a sessão (o usuário precisará de um novo código para voltar). */
export async function sair(): Promise<void> {
  // Desvincula o push deste aparelho ANTES de limpar a sessão (enquanto há token),
  // para não receber notificações privadas do perfil anterior num aparelho compartilhado.
  const cabecalhos = authHeaders();
  if (cabecalhos.Authorization) {
    try {
      await fetch(`${API_URL}/dispositivo/desvincular`, { method: 'POST', headers: cabecalhos });
    } catch {
      // falha de rede não deve impedir o logout local
    }
  }
  cache = null;
  try {
    await apagarBruto();
  } catch {
    // silencioso
  }
}

/** Cabeçalho Authorization para chamadas autenticadas do paciente. */
export function authHeaders(): Record<string, string> {
  return cache?.token ? { Authorization: `Bearer ${cache.token}` } : {};
}

/** Callback avisado quando o backend recusa o token (sessão inválida). */
let aoInvalidar: (() => void) | null = null;
export function registrarInvalidacao(callback: (() => void) | null): void {
  aoInvalidar = callback;
}

/**
 * fetch autenticado para os endpoints do paciente (/meu/**): anexa o Bearer e,
 * se o backend responder 401 (token expirado ou aparelho trocado), encerra a
 * sessão e avisa o app para voltar à tela de ativação.
 */
export async function fetchMeu(path: string, init: RequestInit = {}): Promise<Response> {
  // Garante que a sessão guardada já foi lida (evita header vazio no arranque frio,
  // ex.: chamada disparada por toque em notificação antes de carregar a sessão).
  await carregarSessao();
  const cabecalhos = authHeaders();
  const resposta = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: { ...(init.headers ?? {}), ...cabecalhos },
  });
  // Só encerra a sessão se REALMENTE enviamos um token e ele foi recusado (401).
  if (resposta.status === 401 && cabecalhos.Authorization) {
    await sair();
    aoInvalidar?.();
    throw new Error('Sessão expirada. Entre novamente.');
  }
  return resposta;
}
