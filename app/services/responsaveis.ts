import { fetchMeu } from './sessao';

/**
 * Nível de acesso de uma pessoa autorizada a UMA funcionalidade (o paciente escolhe pelo app).
 * SEM_ACESSO = não vê; VISUALIZAR = só visualiza; VISUALIZAR_LANCAR = visualiza e lança/age.
 */
export type NivelAcesso = 'SEM_ACESSO' | 'VISUALIZAR' | 'VISUALIZAR_LANCAR';

/** Funcionalidades que o paciente pode liberar (os valores casam com o enum do backend). */
export type Funcionalidade =
  | 'AGENDAMENTOS'
  | 'CHAT'
  | 'SAU'
  | 'REDE_SOCIAL'
  | 'MEU_PERFIL'
  | 'PRONTUARIO'
  | 'NPS';

/** Mapa funcionalidade → nível liberado. Ausência de uma chave = sem acesso. */
export type MapaPermissoes = Record<string, NivelAcesso>;

/** Item do catálogo de funcionalidades (a tela usa para montar a matriz na ordem certa). */
export interface CatalogoFuncionalidade {
  valor: Funcionalidade;
  rotulo: string;
  /** true quando NÃO existe "Ver e lançar" — só "Sem acesso" e "Só visualizar" (ex.: Prontuário). */
  semLancamento?: boolean;
}

/** Funcionalidades na ORDEM em que aparecem na matriz de permissões. */
export const FUNCIONALIDADES: CatalogoFuncionalidade[] = [
  { valor: 'AGENDAMENTOS', rotulo: 'Agendamentos' },
  { valor: 'CHAT', rotulo: 'Chat' },
  { valor: 'SAU', rotulo: 'SAU (Manifestações)' },
  { valor: 'REDE_SOCIAL', rotulo: 'Rede Social' },
  { valor: 'MEU_PERFIL', rotulo: 'Meu Perfil' },
  { valor: 'PRONTUARIO', rotulo: 'Prontuário', semLancamento: true },
  { valor: 'NPS', rotulo: 'NPS' },
];

/** Item do catálogo de níveis. */
export interface CatalogoNivel {
  valor: NivelAcesso;
  rotulo: string;
}

/** Níveis de acesso, do menor para o maior (a matriz mostra os botões nesta ordem). */
export const NIVEIS: CatalogoNivel[] = [
  { valor: 'SEM_ACESSO', rotulo: 'Sem acesso' },
  { valor: 'VISUALIZAR', rotulo: 'Só visualizar' },
  { valor: 'VISUALIZAR_LANCAR', rotulo: 'Ver e lançar' },
];

/** Mapa inicial de um responsável recém-adicionado: TODAS as funcionalidades em "Sem acesso". */
export function permissoesVazias(): MapaPermissoes {
  const mapa: MapaPermissoes = {};
  for (const f of FUNCIONALIDADES) mapa[f.valor] = 'SEM_ACESSO';
  return mapa;
}

/**
 * Pessoa autorizada que o PRÓPRIO paciente adicionou pelo app. O paciente controla, pelo
 * app, o que ela pode fazer em cada funcionalidade (mapa {@link MapaPermissoes}).
 */
export interface MeuResponsavel {
  id: number;
  nome: string;
  cpf: string;
  /** Data de nascimento no formato ISO "AAAA-MM-DD" (2ª trava de identidade no login). */
  dataNascimento: string;
  telefone: string;
  /** false = inativo (sem acesso, mas preservado — dá para reativar). */
  ativo: boolean;
  /** false quando já tem lançamentos no sistema: aí só cabe inativar/reativar, não excluir. */
  podeExcluir: boolean;
  /** O que a pessoa pode fazer por funcionalidade. Chave ausente = sem acesso. */
  permissoes: MapaPermissoes;
}

/** Pessoas autorizadas que o paciente adicionou (só as ativas, origem PACIENTE). */
export async function listarResponsaveis(): Promise<MeuResponsavel[]> {
  const resposta = await fetchMeu('/meu/responsaveis');
  if (!resposta.ok) {
    throw new Error(await mensagemErro(resposta, 'Não foi possível carregar as pessoas autorizadas.'));
  }
  return (await resposta.json()) as MeuResponsavel[];
}

/**
 * Adiciona uma pessoa (nome + CPF + data de nascimento + telefone + permissões). O paciente
 * escolhe o nível de cada funcionalidade em `permissoes`. `dataNascimento` no formato ISO
 * "AAAA-MM-DD". Enviar SEM_ACESSO é ok — o backend ignora.
 */
export async function adicionarResponsavel(
  nome: string,
  cpf: string,
  dataNascimento: string,
  telefone: string,
  permissoes: MapaPermissoes,
): Promise<MeuResponsavel> {
  const resposta = await fetchMeu('/meu/responsaveis', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nome, cpf: cpf.replace(/\D/g, ''), dataNascimento, telefone, permissoes }),
  });
  if (!resposta.ok) {
    throw new Error(await mensagemErro(resposta, 'Não foi possível adicionar agora. Tente novamente.'));
  }
  return (await resposta.json()) as MeuResponsavel;
}

/**
 * Edita uma pessoa já cadastrada: nome, data de nascimento, telefone e permissões. O CPF NÃO
 * é editável. `dataNascimento` no formato ISO "AAAA-MM-DD".
 */
export async function editarResponsavel(
  id: number,
  dados: { nome: string; dataNascimento: string; telefone: string; permissoes: MapaPermissoes },
): Promise<MeuResponsavel> {
  const resposta = await fetchMeu(`/meu/responsaveis/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(dados),
  });
  if (!resposta.ok) {
    throw new Error(await mensagemErro(resposta, 'Não foi possível salvar agora. Tente novamente.'));
  }
  return (await resposta.json()) as MeuResponsavel;
}

/**
 * Inativa (ativo=false) ou reativa (ativo=true) uma pessoa autorizada. Usado quando ela já
 * tem lançamentos e não pode ser excluída — preserva o histórico, mas tira/devolve o acesso.
 */
export async function definirSituacaoResponsavel(id: number, ativo: boolean): Promise<MeuResponsavel> {
  const resposta = await fetchMeu(`/meu/responsaveis/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ativo }),
  });
  if (!resposta.ok) {
    throw new Error(await mensagemErro(resposta, 'Não foi possível atualizar agora. Tente novamente.'));
  }
  return (await resposta.json()) as MeuResponsavel;
}

/** Erro de chamada com o status HTTP anexado (para a tela distinguir, ex.: 409). */
export type ErroApi = Error & { status?: number };

/** Exclui de vez uma pessoa autorizada (só quando NÃO tem lançamentos; senão o back bloqueia com 409). */
export async function removerResponsavel(id: number): Promise<void> {
  const resposta = await fetchMeu(`/meu/responsaveis/${id}`, { method: 'DELETE' });
  if (!resposta.ok) {
    const erro: ErroApi = new Error(await mensagemErro(resposta, 'Não foi possível remover agora. Tente novamente.'));
    erro.status = resposta.status;
    throw erro;
  }
}

/**
 * Mensagem de bloqueio vinda do backend (ApiExceptionHandler devolve {status, message}),
 * ex.: telefone inválido, próprio telefone, ou telefone já cadastrado. Se não houver, usa o padrão.
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
