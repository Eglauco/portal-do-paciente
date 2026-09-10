import { fetchMeu } from './sessao';

/**
 * Pessoa autorizada que o PRÓPRIO paciente adicionou pelo app. O acesso dela fica
 * TRAVADO no servidor em Agendamentos (ver e agendar) — o app não escolhe permissão.
 */
export interface MeuResponsavel {
  id: number;
  nome: string;
  telefone: string;
  /** false = inativo (sem acesso, mas preservado — dá para reativar). */
  ativo: boolean;
  /** false quando já tem lançamentos no sistema: aí só cabe inativar/reativar, não excluir. */
  podeExcluir: boolean;
}

/** Pessoas autorizadas que o paciente adicionou (só as ativas, origem PACIENTE). */
export async function listarResponsaveis(): Promise<MeuResponsavel[]> {
  const resposta = await fetchMeu('/meu/responsaveis');
  if (!resposta.ok) {
    throw new Error(await mensagemErro(resposta, 'Não foi possível carregar as pessoas autorizadas.'));
  }
  return (await resposta.json()) as MeuResponsavel[];
}

/** Adiciona uma pessoa (nome + telefone). O servidor libera só Agendamentos. */
export async function adicionarResponsavel(nome: string, telefone: string): Promise<MeuResponsavel> {
  const resposta = await fetchMeu('/meu/responsaveis', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nome, telefone }),
  });
  if (!resposta.ok) {
    throw new Error(await mensagemErro(resposta, 'Não foi possível adicionar agora. Tente novamente.'));
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
