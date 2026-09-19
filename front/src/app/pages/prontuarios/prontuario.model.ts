export interface Ref {
  id: number;
  nome: string;
}

/** Situação da análise por IA de um documento. */
export type StatusAnaliseDocumento =
  | 'NAO_ANALISADO'
  | 'SEM_ALTERACOES'
  | 'AGUARDANDO_VALIDACAO'
  | 'VALIDADO'
  | 'NAO_ANALISAVEL';

/** Situação consolidada do alerta do prontuário (pior status entre os documentos). */
export type StatusAlertaProntuario = 'SEM_ALTERACOES' | 'AGUARDANDO_VALIDACAO' | 'VALIDADO';

/** Documento com os dados da análise por IA (retorno do detalhe do prontuário). */
export interface DocumentoAdmin {
  id: number;
  nome: string;
  url?: string | null;
  tipoId?: number | null;
  tipoNome?: string | null;
  resumoClinico?: string | null;
  statusAnalise: StatusAnaliseDocumento;
  statusAnaliseDescricao: string;
  validadoPorNome?: string | null;
  validadoEm?: string | null;
  analisadoEm?: string | null;
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
  statusAlerta: StatusAlertaProntuario;
  statusAlertaDescricao: string;
}

/** Detalhe do prontuário (com a lista de documentos e a análise). */
export interface ProntuarioDetalhe {
  id: number;
  numeroAtendimento: string;
  agendamentoId: number;
  paciente: Ref;
  especialidade: Ref;
  profissionalSaude: Ref;
  unidadeSaude: Ref;
  dataHora: string;
  statusAlerta: StatusAlertaProntuario;
  statusAlertaDescricao: string;
  documentos: DocumentoAdmin[];
}

/** Formato de envio (criação/edição). */
export interface ProntuarioRequest {
  agendamentoId: number;
  numeroAtendimento: string;
  documentos: { nome: string; url?: string | null; tipoId?: number | null }[];
}

export interface ProntuarioFiltro {
  numero?: string | null;
  pacienteId?: number | null;
  unidadeId?: number | null;
  especialidade?: string | null;
  status?: StatusAlertaProntuario | null;
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
