import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

import { API_URL } from '@/constants/api';
import { obterDispositivoId } from './identidade';

const CHAVE = 'pop.sessaoPaciente';

export interface SessaoPaciente {
  token: string;
  pacienteId: number;
  nome: string;
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
    cache = bruto ? (JSON.parse(bruto) as SessaoPaciente) : null;
  } catch {
    cache = null;
  }
  return cache;
}

/**
 * Ativa o app: envia telefone + código + id do aparelho e guarda o token.
 * Erros trazem uma mensagem amigável para exibir ao paciente.
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
  const dados = (await resposta.json()) as SessaoPaciente;
  cache = { token: dados.token, pacienteId: dados.pacienteId, nome: dados.nome };
  await gravarBruto(JSON.stringify(cache));
  return cache;
}

/** Encerra a sessão (o paciente precisará de um novo código para voltar). */
export async function sair(): Promise<void> {
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
