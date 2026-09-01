import { API_URL } from '@/constants/api';
import { Agendamento, MotivoFalta, StatusAgendamento, StatusBackend } from '@/constants/agendamentos';
import { fetchMeu } from '@/services/sessao';

interface Ref {
  id: number;
  nome: string;
}

interface AgendamentoBackend {
  id: number;
  dataHora: string;
  especialidade: Ref;
  profissionalSaude: Ref;
  procedimento: Ref;
  paciente: Ref;
  unidadeSaude: Ref;
  statusAgendamento: StatusBackend;
  statusDescricao: string;
  faltaJustificada: boolean;
  justificativaFalta: string | null;
  motivosFalta: Ref[];
  horasCancelamento: number | null;
}

interface MotivoFaltaBackend {
  id: number;
  motivo: string;
  ativo: boolean;
}

interface Pagina<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** Converte o status do back-end para o status visual usado no app. */
const MAPA_STATUS: Record<StatusBackend, StatusAgendamento> = {
  AGUARDANDO_CONFIRMACAO_PACIENTE: 'aguardando',
  PACIENTE_CONFIRMOU: 'confirmado',
  PRESENCA_PACIENTE: 'realizado',
  FALTA_PACIENTE: 'cancelado',
  CANCELADO_PELA_UNIDADE: 'cancelado',
  CANCELADO_PELO_PACIENTE: 'cancelado',
};

const MESES = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ'];
const SEMANA = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];

const doisDigitos = (n: number) => String(n).padStart(2, '0');

/** Mapeia a resposta do back-end para o modelo de exibição do app. */
function paraViewModel(b: AgendamentoBackend): Agendamento {
  const data = new Date(b.dataHora);
  const status = MAPA_STATUS[b.statusAgendamento] ?? 'aguardando';
  const grupo = status === 'realizado' || status === 'cancelado' ? 'concluidos' : 'proximos';

  return {
    id: String(b.id),
    dia: doisDigitos(data.getDate()),
    mes: MESES[data.getMonth()],
    semana: SEMANA[data.getDay()],
    especialidade: b.especialidade.nome,
    profissional: b.profissionalSaude.nome,
    hora: `${doisDigitos(data.getHours())}:${doisDigitos(data.getMinutes())}`,
    unidade: b.unidadeSaude.nome,
    status,
    statusLabel: b.statusDescricao,
    grupo,
    statusBackend: b.statusAgendamento,
    faltaJustificada: b.faltaJustificada,
    justificativaFalta: b.justificativaFalta,
    motivosFalta: (b.motivosFalta ?? []).map((m) => ({ id: m.id, motivo: m.nome })),
    dataHoraIso: b.dataHora,
    horasCancelamento: b.horasCancelamento ?? null,
  };
}

/** Prazo de cancelamento de um agendamento em relação a "agora" (ms). */
export interface InfoCancelamento {
  /** Ainda dá para cancelar (dentro do prazo). */
  podeCancelar: boolean;
  /** Milissegundos restantes até o limite (negativo se já passou). */
  restanteMs: number;
}

/** Calcula o prazo de cancelamento; null quando o procedimento não define prazo. */
export function infoCancelamento(a: Agendamento, agoraMs: number): InfoCancelamento | null {
  if (a.horasCancelamento == null || !a.dataHoraIso) return null;
  const limite = new Date(a.dataHoraIso).getTime() - a.horasCancelamento * 3_600_000;
  const restante = limite - agoraMs;
  return { podeCancelar: restante > 0, restanteMs: restante };
}

/** Formata um intervalo (ms) como "Xh Ymin" / "Ymin" / "menos de 1min". */
export function formatarRestante(ms: number): string {
  const totalMin = Math.max(0, Math.floor(ms / 60_000));
  if (totalMin === 0) return 'menos de 1min';
  const h = Math.floor(totalMin / 60);
  const m = totalMin % 60;
  return h > 0 ? `${h}h ${m}min` : `${m}min`;
}

async function comoJson<T>(resposta: Response): Promise<T> {
  if (!resposta.ok) {
    throw new Error(`Falha na requisição (${resposta.status})`);
  }
  return resposta.json() as Promise<T>;
}

/** Lista os agendamentos do paciente logado (mais recentes primeiro). */
export async function listarAgendamentos(): Promise<Agendamento[]> {
  const resposta = await fetchMeu('/meu/agendamentos?page=0&size=100');
  const pagina = await comoJson<Pagina<AgendamentoBackend>>(resposta);
  return pagina.content.map(paraViewModel);
}

/** Confirma o agendamento (paciente). */
export async function confirmarAgendamento(id: string): Promise<Agendamento> {
  const resposta = await fetchMeu(`/meu/agendamentos/${id}/confirmar`, { method: 'POST' });
  return paraViewModel(await comoJson<AgendamentoBackend>(resposta));
}

/** Cancela o agendamento (paciente). */
export async function cancelarAgendamento(id: string): Promise<Agendamento> {
  const resposta = await fetchMeu(`/meu/agendamentos/${id}/cancelar`, { method: 'POST' });
  return paraViewModel(await comoJson<AgendamentoBackend>(resposta));
}

/** Motivos de falta ativos, para o paciente selecionar ao justificar. */
export async function listarMotivosFalta(): Promise<MotivoFalta[]> {
  const resposta = await fetch(`${API_URL}/motivo-falta/ativos`);
  const lista = await comoJson<MotivoFaltaBackend[]>(resposta);
  return lista.map((m) => ({ id: m.id, motivo: m.motivo }));
}

/** Registra a justificativa da falta (motivos selecionados + texto livre). */
export async function justificarFalta(
  id: string,
  motivoIds: number[],
  justificativa: string,
): Promise<Agendamento> {
  const resposta = await fetchMeu(`/meu/agendamentos/${id}/justificar-falta`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ motivoIds, justificativa }),
  });
  return paraViewModel(await comoJson<AgendamentoBackend>(resposta));
}
