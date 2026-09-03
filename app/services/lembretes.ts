import { fetchMeu } from '@/services/sessao';

/** Pop-up de lembrete pendente (vem do backend ao abrir o app). */
export interface LembretePopup {
  id: number; // id da notificação (usado para reconhecer)
  titulo: string;
  mensagem: string;
  agendamentoId: number | null;
  podeCancelar: boolean;
  dataHora: string | null;
  especialidade: string | null;
}

async function comoJson<T>(resposta: Response): Promise<T> {
  if (!resposta.ok) {
    throw new Error(`Falha na requisição (${resposta.status})`);
  }
  return resposta.json() as Promise<T>;
}

/** Lembretes a mostrar como pop-up (não reconhecidos ainda). */
export async function listarPopupsPendentes(): Promise<LembretePopup[]> {
  const resposta = await fetchMeu('/meu/lembretes/popups');
  return comoJson<LembretePopup[]>(resposta);
}

/** Reconhece o pop-up (não reaparece mais). */
export async function reconhecerLembrete(notificacaoId: number): Promise<void> {
  const resposta = await fetchMeu(`/meu/lembretes/${notificacaoId}/reconhecer`, { method: 'POST' });
  if (!resposta.ok) {
    throw new Error(`Falha ao reconhecer lembrete (${resposta.status})`);
  }
}
