export interface Ref {
  id: number;
  nome: string;
}

export interface Documento {
  id: number;
  nome: string;
  url?: string | null;
}

/** Item da listagem de prontuários. */
export interface Prontuario {
  id: number;
  numeroAtendimento: string;
  agendamentoId: number;
  paciente: Ref;
  especialidade: Ref;
  unidadeSaude: Ref;
  dataHora: string;
  documentos: number;
}

/** Detalhe do prontuário (com a lista de documentos). */
export interface ProntuarioDetalhe {
  id: number;
  numeroAtendimento: string;
  agendamentoId: number;
  paciente: Ref;
  especialidade: Ref;
  profissionalSaude: Ref;
  unidadeSaude: Ref;
  dataHora: string;
  documentos: Documento[];
}

/** Formato de envio (criação/edição). */
export interface ProntuarioRequest {
  agendamentoId: number;
  numeroAtendimento: string;
  documentos: { nome: string; url?: string | null }[];
}

export interface ProntuarioFiltro {
  numero?: string | null;
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
