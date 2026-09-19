import { DocumentoAdmin, ProntuarioDetalhe } from '../prontuarios/prontuario.model';

/**
 * Cabeçalho clínico do paciente na tela "Prontuário Médico" (retorno do backend).
 * Campos leves para montar o topo fixo da linha do tempo.
 */
export interface PacienteCabecalho {
  id: number;
  nome: string;
  cpf?: string | null;
  /** Data ISO (yyyy-MM-dd). */
  dataNascimento?: string | null;
  /** Chave do enum de sexo (MASCULINO, FEMININO, OUTRO, NAO_INFORMADO) ou null. */
  sexo?: string | null;
  /** Foto pré-assinada; null se não tiver. */
  fotoUrl?: string | null;
  prontuario?: string | null;
  telefones: string[];
  unidades: string[];
  /** Documentos aguardando validação (destaque de alerta). */
  alertasPendentes: number;
  totalAtendimentos: number;
}

/** Histórico completo do paciente para a tela do médico. */
export interface HistoricoMedico {
  paciente: PacienteCabecalho;
  /** Resumo geral do histórico gerado por IA (ou null se nunca gerado). */
  resumoHistoricoIa: string | null;
  /** Quando o resumo foi gerado (ISO) ou null. */
  resumoHistoricoGeradoEm: string | null;
  /** Análise de documentos por IA ligada nas Configurações. */
  iaHabilitada: boolean;
  /** Resumo do histórico por IA ligado nas Configurações (gerado automaticamente ao analisar docs). */
  resumoIaHabilitado: boolean;
  /** Atendimentos (prontuários) do mais recente ao mais antigo. */
  prontuarios: ProntuarioDetalhe[];
}

/** Tipo de arquivo detectado para o visor embutido. */
export type TipoArquivo = 'pdf' | 'imagem' | 'outro';

/** Documento aberto no visor, com o atendimento de origem para o contexto. */
export interface DocumentoAberto {
  documento: DocumentoAdmin;
  prontuario: ProntuarioDetalhe;
}
