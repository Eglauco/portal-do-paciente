import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
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

// SecureStore não existe na web; lá usamos AsyncStorage (o app real roda no aparelho).
async function lerBruto(): Promise<string | null> {
  return Platform.OS === 'web' ? AsyncStorage.getItem(CHAVE) : SecureStore.getItemAsync(CHAVE);
}
async function gravarBruto(valor: string): Promise<void> {
  if (Platform.OS === 'web') await AsyncStorage.setItem(CHAVE, valor);
  else await SecureStore.setItemAsync(CHAVE, valor);
}
async function apagarBruto(): Promise<void> {
  if (Platform.OS === 'web') await AsyncStorage.removeItem(CHAVE);
  else await SecureStore.deleteItemAsync(CHAVE);
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
