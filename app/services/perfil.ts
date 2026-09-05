import * as FileSystem from 'expo-file-system/legacy';

import { fetchMeu } from '@/services/sessao';

export type SexoPaciente = 'MASCULINO' | 'FEMININO' | 'OUTRO' | 'NAO_INFORMADO';

/** Dados do paciente logado para a tela "Meu perfil" (somente leitura). */
export interface MeuPerfil {
  id: number;
  nome: string;
  telefone: string | null;
  telefonesAdicionais: string[];
  email: string | null;
  cpf: string | null;
  rg: string | null;
  cns: string | null;
  dataNascimento: string | null;
  sexo: SexoPaciente | null;
  nomeMae: string | null;
  nomePai: string | null;
  codigoIntegracao: string | null;
  prontuario: string | null;
  rua: string | null;
  numero: string | null;
  complemento: string | null;
  bairro: string | null;
  municipio: string | null;
  uf: string | null;
  cep: string | null;
  /** Link temporário (pré-assinado) da foto, ou null. */
  fotoUrl: string | null;
}

async function comoJson<T>(resposta: Response): Promise<T> {
  if (!resposta.ok) {
    throw new Error(`Falha na requisição (${resposta.status})`);
  }
  return resposta.json() as Promise<T>;
}

/** Carrega os dados do paciente logado (somente leitura). */
export async function carregarPerfil(): Promise<MeuPerfil> {
  return comoJson<MeuPerfil>(await fetchMeu('/meu/perfil'));
}

interface UploadUrl {
  uploadUrl: string;
  publicUrl: string;
}

/**
 * Troca a foto do perfil: pede uma URL pré-assinada, envia o arquivo DIRETO ao
 * S3 (PUT binário — não passa pelo backend) e persiste a URL. Devolve o perfil
 * já atualizado (com o novo link da foto).
 */
export async function trocarFoto(uri: string, nomeArquivo: string, contentType: string): Promise<MeuPerfil> {
  const { uploadUrl, publicUrl } = await comoJson<UploadUrl>(
    await fetchMeu('/meu/perfil/foto/upload-url', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nomeArquivo, contentType }),
    }),
  );

  // Upload binário direto ao S3. O Content-Type precisa casar com o que assinamos.
  const envio = await FileSystem.uploadAsync(uploadUrl, uri, {
    httpMethod: 'PUT',
    uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
    headers: { 'Content-Type': contentType },
  });
  if (envio.status < 200 || envio.status >= 300) {
    throw new Error(`Falha ao enviar a foto (${envio.status})`);
  }

  return comoJson<MeuPerfil>(
    await fetchMeu('/meu/perfil/foto', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: publicUrl }),
    }),
  );
}

/** Remove a foto do perfil (e apaga o objeto no S3). Devolve o perfil sem foto. */
export async function excluirFoto(): Promise<MeuPerfil> {
  return comoJson<MeuPerfil>(await fetchMeu('/meu/perfil/foto', { method: 'DELETE' }));
}
