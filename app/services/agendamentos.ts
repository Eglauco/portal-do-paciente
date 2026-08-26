import { API_URL } from '@/constants/api';
import { Agendamento, StatusAgendamento } from '@/constants/agendamentos';

/** Status do agendamento no back-end (enum Java). */
type StatusBackend =
  | 'AGUARDANDO_CONFIRMACAO_PACIENTE'
  | 'PACIENTE_CONFIRMOU'
  | 'CANCELADO_PELA_UNIDADE'
  | 'CANCELADO_PELO_PACIENTE'
  | 'FALTA_PACIENTE'
  | 'PRESENCA_PACIENTE';

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
  };
}

async function comoJson<T>(resposta: Response): Promise<T> {
  if (!resposta.ok) {
    throw new Error(`Falha na requisição (${resposta.status})`);
  }
  return resposta.json() as Promise<T>;
}

/** Lista todos os agendamentos disponíveis (mais recentes primeiro). */
export async function listarAgendamentos(): Promise<Agendamento[]> {
  const resposta = await fetch(`${API_URL}/agendamento?page=0&size=100`);
  const pagina = await comoJson<Pagina<AgendamentoBackend>>(resposta);
  return pagina.content.map(paraViewModel);
}

/** Confirma o agendamento (paciente). */
export async function confirmarAgendamento(id: string): Promise<Agendamento> {
  const resposta = await fetch(`${API_URL}/agendamento/${id}/confirmar`, { method: 'POST' });
  return paraViewModel(await comoJson<AgendamentoBackend>(resposta));
}

/** Cancela o agendamento (paciente). */
export async function cancelarAgendamento(id: string): Promise<Agendamento> {
  const resposta = await fetch(`${API_URL}/agendamento/${id}/cancelar`, { method: 'POST' });
  return paraViewModel(await comoJson<AgendamentoBackend>(resposta));
}
