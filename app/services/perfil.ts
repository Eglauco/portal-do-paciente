import * as FileSystem from 'expo-file-system/legacy';

import { fetchMeu } from '@/services/sessao';

export type SexoPaciente = 'MASCULINO' | 'FEMININO' | 'OUTRO' | 'NAO_INFORMADO';

/** Dados do paciente logado para a tela "Meu perfil" (somente leitura). */
export interface MeuPerfil {
  id: number;
  nome: string;
  /** Telefones do cadastro (o backend não expõe mais um "telefone" único). */
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

/** Só os dígitos de um valor (telefones, CNS, CEP vão para o backend sem máscara). */
const soDigitos = (v: string | null | undefined): string => (v ?? '').replace(/\D/g, '');

/**
 * Extrai a mensagem de bloqueio vinda do backend (ApiExceptionHandler devolve {status, message}),
 * ex.: 422 (lista de telefones vazia) ou 400/409 (CNS inválido/duplicado). Sem corpo útil, usa o padrão.
 */
async function mensagemErro(resposta: Response, padrao: string): Promise<string> {
  try {
    const corpo = (await resposta.json()) as { message?: string };
    if (corpo && typeof corpo.message === 'string' && corpo.message.trim()) {
      return corpo.message;
    }
  } catch {
    // corpo vazio/não-JSON: cai no padrão
  }
  return padrao;
}

async function comoJson<T>(resposta: Response): Promise<T> {
  if (!resposta.ok) {
    throw new Error(await mensagemErro(resposta, `Falha na requisição (${resposta.status})`));
  }
  return resposta.json() as Promise<T>;
}

/** Carrega os dados do paciente logado (somente leitura). */
export async function carregarPerfil(): Promise<MeuPerfil> {
  return comoJson<MeuPerfil>(await fetchMeu('/meu/perfil'));
}

/**
 * Campos que o paciente pode editar na tela "Editar dados". O CPF NÃO entra (é a identidade
 * de login). Código de integração, prontuário e unidades também ficam de fora (administrativos).
 * `dataNascimento` em ISO "AAAA-MM-DD" ou null; telefones/cns/cep vão só com dígitos.
 */
export interface PerfilEditavel {
  nome: string;
  /** Telefones do paciente (o backend exige ao menos 1). Só dígitos. */
  telefonesAdicionais: string[];
  sexo: SexoPaciente | null;
  dataNascimento: string | null;
  rg: string | null;
  cns: string | null;
  nomeMae: string | null;
  nomePai: string | null;
  email: string | null;
  rua: string | null;
  numero: string | null;
  complemento: string | null;
  bairro: string | null;
  municipio: string | null;
  uf: string | null;
  cep: string | null;
}

/**
 * Salva os dados pessoais do paciente logado. Normaliza telefones/CNS/CEP para só dígitos
 * (o backend também normaliza) e devolve o MESMO shape do GET (perfil já atualizado).
 * Erros do backend (422 telefone, 400/409 CNS) sobem com a mensagem para a tela exibir.
 */
export async function atualizarPerfil(dados: PerfilEditavel): Promise<MeuPerfil> {
  const corpo: PerfilEditavel = {
    ...dados,
    telefonesAdicionais: dados.telefonesAdicionais.map(soDigitos).filter((tel) => tel.length > 0),
    cns: dados.cns ? soDigitos(dados.cns) : null,
    cep: dados.cep ? soDigitos(dados.cep) : null,
  };
  return comoJson<MeuPerfil>(
    await fetchMeu('/meu/perfil', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo),
    }),
  );
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
