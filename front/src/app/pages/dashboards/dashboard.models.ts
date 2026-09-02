/** Espelham os DTOs de `com.example.pop.dashboard` no backend. */

export interface SerieDiaria {
  data: string; // ISO yyyy-MM-dd
  valor: number;
}

export interface Fatia {
  chave: string;
  rotulo: string;
  valor: number;
}

export interface ItemContagem {
  rotulo: string;
  valor: number;
}

export interface MediaCategoria {
  categoria: string;
  media: number;
  respostas: number;
}

export interface GeralDashboard {
  dias: number;
  agendamentosPeriodo: number;
  agendamentosAnterior: number;
  presencas: number;
  faltas: number;
  taxaComparecimento: number;
  chatsAbertos: number;
  chatsPeriodo: number;
  sauAbertas: number;
  sauPeriodo: number;
  npsRespondidos: number;
  npsMedia: number | null;
  pacientesUsandoApp: number;
  pacientesAtivos: number;
  pacientesTotal: number;
  agendamentosPorDia: SerieDiaria[];
  agendamentosPorStatus: Fatia[];
}

export interface AgendamentoDashboard {
  dias: number;
  total: number;
  anterior: number;
  aguardando: number;
  confirmados: number;
  presencas: number;
  faltas: number;
  canceladosUnidade: number;
  canceladosPaciente: number;
  taxaComparecimento: number;
  taxaConfirmacao: number;
  taxaCancelamento: number;
  proximos7Dias: number;
  porDia: SerieDiaria[];
  porStatus: Fatia[];
  topProcedimentos: ItemContagem[];
  topProfissionais: ItemContagem[];
  porEspecialidade: ItemContagem[];
  motivosFalta: ItemContagem[];
}

export interface ChatDashboard {
  dias: number;
  conversasPeriodo: number;
  anterior: number;
  naoLidas: number;
  aguardando: number;
  emAtendimento: number;
  resolvidas: number;
  abertas: number;
  semResponsavel: number;
  mensagensPeriodo: number;
  mensagensPaciente: number;
  mensagensUnidade: number;
  taxaResolucao: number;
  conversasPorDia: SerieDiaria[];
  porStatus: Fatia[];
  cargaPorAtendente: ItemContagem[];
}

export interface SauDashboard {
  dias: number;
  total: number;
  anterior: number;
  aguardandoSau: number;
  aguardandoPaciente: number;
  fechadas: number;
  abertas: number;
  taxaResolucao: number;
  mensagensPaciente: number;
  mensagensSau: number;
  porDia: SerieDiaria[];
  porStatus: Fatia[];
  porTipo: ItemContagem[];
  cargaPorAtendente: ItemContagem[];
}

export interface NpsDashboard {
  dias: number;
  gerados: number;
  anterior: number;
  respondidos: number;
  pendentes: number;
  expirados: number;
  taxaResposta: number;
  mediaGeral: number | null;
  satisfeitos: number;
  neutros: number;
  insatisfeitos: number;
  avaliacoesPorDia: SerieDiaria[];
  porStatus: Fatia[];
  mediaPorCategoria: MediaCategoria[];
  distribuicaoNotas: ItemContagem[];
}

/** Opções do seletor de período (dias). */
export const PERIODOS: { label: string; dias: number }[] = [
  { label: '7 dias', dias: 7 },
  { label: '30 dias', dias: 30 },
  { label: '90 dias', dias: 90 },
];
