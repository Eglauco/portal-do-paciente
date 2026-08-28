import AsyncStorage from '@react-native-async-storage/async-storage';

const CHAVE_ID = 'pop.dispositivoId';
const CHAVE_NOME = 'pop.nomePaciente';

let idCache: string | null = null;

function gerarId(): string {
  return `dev-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

/** Id anônimo e estável do aparelho (usado para curtidas). */
export async function obterDispositivoId(): Promise<string> {
  if (idCache) return idCache;
  try {
    let id = await AsyncStorage.getItem(CHAVE_ID);
    if (!id) {
      id = gerarId();
      await AsyncStorage.setItem(CHAVE_ID, id);
    }
    idCache = id;
    return id;
  } catch {
    if (!idCache) idCache = gerarId();
    return idCache;
  }
}

/** Nome do paciente para assinar comentários (lembrado no aparelho). */
export async function obterNome(): Promise<string> {
  try {
    return (await AsyncStorage.getItem(CHAVE_NOME)) ?? '';
  } catch {
    return '';
  }
}

export async function salvarNome(nome: string): Promise<void> {
  try {
    await AsyncStorage.setItem(CHAVE_NOME, nome.trim());
  } catch {
    // silencioso
  }
}
