export type StatusManifestacao = 'AGUARDANDO_SAU' | 'AGUARDANDO_PACIENTE' | 'FECHADA';
export type AutorManifestacao = 'PACIENTE' | 'SAU';

export interface Ref {
  id: number;
  nome: string;
}

/** Item da listagem (CRUD do SAU). */
export interface Manifestacao {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  tipo: Ref;
  status: StatusManifestacao;
  statusDescricao: string;
  ultimaMensagem?: string | null;
  ultimaMensagemDe?: AutorManifestacao | null;
  /** Nota do atendimento (1-5) quando o paciente já avaliou. */
  avaliacaoNota?: number | null;
  atualizadoEm: string;
  criadoEm: string;
}

export interface MensagemSau {
  id: number;
  autor: AutorManifestacao;
  autorNome: string;
  texto: string;
  criadoEm: string;
}

export interface ManifestacaoDetalhe {
  id: number;
  paciente: Ref;
  unidadeSaude: Ref;
  tipo: Ref;
  status: StatusManifestacao;
  statusDescricao: string;
  /** Avaliação do atendimento pelo paciente (null enquanto não avaliada). */
  avaliacaoNota?: number | null;
  avaliacaoComentario?: string | null;
  avaliadoEm?: string | null;
  mensagens: MensagemSau[];
}

export interface ManifestacaoFiltro {
  unidadeId?: number | null;
  tipoId?: number | null;
  status?: StatusManifestacao | null;
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

export const STATUS_OPTIONS: { value: StatusManifestacao; label: string }[] = [
  { value: 'AGUARDANDO_SAU', label: 'Aguardando SAU responder' },
  { value: 'AGUARDANDO_PACIENTE', label: 'Aguardando paciente responder' },
  { value: 'FECHADA', label: 'Mensagem fechada' },
];

export function statusLabel(valor: StatusManifestacao): string {
  return STATUS_OPTIONS.find((o) => o.value === valor)?.label ?? valor;
}
