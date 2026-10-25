/** Item do FAQ da unidade (par pergunta + resposta) que alimenta a assistente virtual. */
export interface UnidadeFaqItem {
  id?: number;
  pergunta: string;
  resposta: string;
}

/** Dia da semana como o backend serializa (java.time.DayOfWeek). */
export type DiaSemana =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

/**
 * Horário de funcionamento de um dia. `fechado`: não abre. `vinteQuatroHoras`: aberta o dia inteiro.
 * Nos dois casos abertura/fechamento ficam nulos. Hora no formato "HH:mm".
 */
export interface UnidadeHorario {
  diaSemana: DiaSemana;
  fechado: boolean;
  vinteQuatroHoras: boolean;
  abertura?: string | null;
  fechamento?: string | null;
}

export interface Unidade {
  id?: number;
  nome: string;
  /** Código da unidade em um sistema externo (integração SIRESP/CROSS); único quando preenchido. */
  codigoIntegracao?: string | null;
  // Perfil (estilo perfil de empresa do WhatsApp). logoUrl/capaUrl = URL crua (p/ re-salvar);
  // logoUrlView/capaUrlView = URL assinada só p/ preview (não re-enviar).
  logoUrl?: string | null;
  logoUrlView?: string | null;
  capaUrl?: string | null;
  capaUrlView?: string | null;
  sobre?: string | null;
  telefone?: string | null;
  endereco?: string | null;
  /** FAQ que a assistente virtual usa no chat desta unidade (não vem no grid). */
  faq?: UnidadeFaqItem[];
  /** Horário de funcionamento por dia (não vem no grid). */
  horarios?: UnidadeHorario[];
}

export interface UnidadeFiltro {
  codigo?: string;
  nome?: string;
}

export interface Pagina<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}
