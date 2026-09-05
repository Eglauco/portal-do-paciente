export type StatusNps = 'PENDENTE' | 'RESPONDIDO' | 'EXPIRADO';

export interface Ref {
  id: number;
  nome: string;
}

/** Item da listagem de NPS. */
export interface Nps {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  especialidade: Ref;
  dataHora: string;
  status: StatusNps;
  statusDescricao: string;
  media?: number | null;
  criadoEm: string;
}

/** Nota de uma categoria dentro de uma avaliação. */
export interface CategoriaNota {
  categoriaId: number;
  categoria: string;
  nota: number;
}

/** Detalhamento do NPS (inclui os dados do atendimento e as notas por categoria). */
export interface NpsDetalhe {
  id: number;
  paciente: Ref;
  /** Foto (pré-assinada) do paciente para o avatar; null se não tiver. */
  pacienteFotoUrl?: string | null;
  unidadeSaude: Ref;
  especialidade: Ref;
  profissionalSaude: Ref;
  procedimento: Ref;
  dataHora: string;
  status: StatusNps;
  statusDescricao: string;
  media?: number | null;
  notas: CategoriaNota[];
  observacao?: string | null;
  criadoEm: string;
  respondidoEm?: string | null;
}

export interface NpsFiltro {
  status?: StatusNps | null;
  pacienteId?: number | null;
  unidadeId?: number | null;
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

export const STATUS_OPTIONS: { value: StatusNps; label: string }[] = [
  { value: 'PENDENTE', label: 'Aguardando resposta' },
  { value: 'RESPONDIDO', label: 'Respondido' },
  { value: 'EXPIRADO', label: 'Expirado' },
];

export function statusLabel(valor: StatusNps): string {
  return STATUS_OPTIONS.find((o) => o.value === valor)?.label ?? valor;
}
