export interface Pagina<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

/** Status derivado de um registro do SIRESP: já agendado ou pendente de revisão. */
export type SirespStatus = 'AGENDADO' | 'REVISAO';

/** Status do envio do XML ao Sistema de Gestão (independente do agendamento). */
export type SirespStatusEnvio = 'NAO_ENVIADO' | 'ENVIADO' | 'FALHA';

/** Tipo do registro importado do SIRESP: consulta ou exame (mesma tabela). */
export type SirespTipoRegistro = 'CONSULTA' | 'EXAME';

/** Movimentação da mensagem do SIRESP (TIPO_CONSULTA/TIPO_EXAME). */
export type SirespTipoMovimento = 'AGENDAMENTO' | 'CANCELAMENTO' | 'TRANSFERENCIA';

/** Linha da LISTA do SIRESP (resumo). */
export interface SirespResumo {
  id: number;
  dataAgenda?: string | null;
  horIni?: string | null;
  horFim?: string | null;
  nomePaciente?: string | null;
  cpf?: string | null;
  nomeEspecialidade?: string | null;
  nomeProfissional?: string | null;
  nomeUnidadeSolicitante?: string | null;
  arquivo?: string | null;
  /** ISO date-time. */
  importadoEm?: string | null;
  importadoPorNome?: string | null;
  status: SirespStatus;
  statusDescricao: string;
  /** Status do envio ao Sistema de Gestão. */
  statusEnvio: SirespStatusEnvio;
  statusEnvioDescricao: string;
  /** Tipo do registro (consulta ou exame). */
  tipoRegistro: SirespTipoRegistro;
  tipoRegistroDescricao: string;
  /** Movimentação (agendamento/cancelamento/transferência). */
  tipoMovimento: SirespTipoMovimento;
  tipoMovimentoDescricao: string;
}

/** Detalhe (todos os campos do XML + metadados da importação) — somente leitura. */
export interface SirespDetalhe {
  id: number;
  unidadeSaudeId?: number | null;
  arquivo?: string | null;
  importadoEm?: string | null;
  importadoPorUsuarioId?: number | null;
  importadoPorNome?: string | null;
  /** URL (S3) do arquivo XML original deste import — usada no botão "Baixar XML original". */
  arquivoUrl?: string | null;
  /** Id do agendamento gerado a partir deste registro (null = ainda não gerado). */
  agendamentoId?: number | null;
  /** Tipo do registro (consulta ou exame). */
  tipoRegistro?: SirespTipoRegistro;
  /** Movimentação (agendamento/cancelamento/transferência). */
  tipoMovimento?: SirespTipoMovimento;
  /** true quando o sistema preencheu campos de exibição (paciente/data/etc.) a partir do horário — não vieram do XML. */
  dadosResolvidos?: boolean;
  /** Log do PROCESSAMENTO INTERNO (encontrou/não encontrou cada entidade pelo código de integração + agendamento). */
  logIntegracao?: string | null;
  /** Log do ENVIO ao Sistema de Gestão (resultado do último Post XML). */
  logEnvio?: string | null;
  /** Status do envio ao Sistema de Gestão. */
  statusEnvio?: SirespStatusEnvio;
  /** Data/hora da última tentativa de envio (ISO). */
  enviadoEm?: string | null;

  tipoConsulta?: string | null;
  codUnidadeExecutante?: string | null;
  idAgeConsultaHor?: string | null;
  idAgeConsulta?: string | null;
  ageConsultaNome?: string | null;
  idEspecialidade?: string | null;
  nomeEspecialidade?: string | null;
  codDia?: string | null;
  dataAgenda?: string | null;
  horIni?: string | null;
  horFim?: string | null;
  tipo?: string | null;
  idMotivo?: string | null;
  idProfissional?: string | null;
  docProfissional?: string | null;
  origem?: string | null;
  nomeProfissional?: string | null;
  idProtocolo?: string | null;
  subcateg?: string | null;
  nomeProtocolo?: string | null;
  codUnidadeSolicitante?: string | null;
  nomeUnidadeSolicitante?: string | null;
  cnesUnidadeSolicitante?: string | null;
  nomeUsuarioSolicitante?: string | null;
  dtUltimaAtualiz?: string | null;
  codPaciente?: string | null;
  nomePaciente?: string | null;
  sexo?: string | null;
  dtNascimento?: string | null;
  rg?: string | null;
  cpf?: string | null;
  nomeMae?: string | null;
  nomePai?: string | null;
  endereco?: string | null;
  enderecoNumero?: string | null;
  bairro?: string | null;
  municipio?: string | null;
  uf?: string | null;
  cep?: string | null;
  telResDdd?: string | null;
  telRes?: string | null;
  telCelularDdd?: string | null;
  telCelular?: string | null;
  telComDdd?: string | null;
  telCom?: string | null;
  telComRamal?: string | null;
  email?: string | null;
  contatoNome?: string | null;
  contatoTelDdd?: string | null;
  contatoTel?: string | null;
  numCns?: string | null;
  numProntuario?: string | null;

  // Horário de origem da transferência (consulta/exame).
  idAgeConsultaHorOrigem?: string | null;
  idAgeExameHorOrigem?: string | null;

  // Campos específicos do EXAME (nulos quando o registro é consulta).
  tipoExame?: string | null;
  idAgeExameHor?: string | null;
  idAgeExame?: string | null;
  ageExameNome?: string | null;
  idAssociacao?: string | null;
  nomeAssociacao?: string | null;
  idExame?: string | null;
  codExame?: string | null;
  nomeExame?: string | null;
  tipoTabela?: string | null;
}

/** Resultado de uma importação de XML (inclui o desfecho do envio automático ao Sistema de Gestão, quando ligado). */
export interface SirespImportResultado {
  importados: number;
  arquivo?: string | null;
  pacientesAtualizados: number;
  pacientesCriados: number;
  /** true se houve tentativa de envio automático ao Sistema de Gestão após o import. */
  enviado: boolean;
  /** true se o Sistema de Gestão processou o XML (só relevante quando enviado = true). */
  envioSucesso: boolean;
  envioMensagem?: string | null;
}

export interface SirespFiltro {
  busca?: string | null;
  status?: SirespStatus | null;
  statusEnvio?: SirespStatusEnvio | null;
  tipoMovimento?: SirespTipoMovimento | null;
}

/** Política de atualização de um campo do paciente a partir do XML. */
export type AcaoAtualizacao = 'SEMPRE' | 'SE_VAZIO' | 'NUNCA';

export interface SirespConfigCampo {
  campo: string;
  rotulo: string;
  acao: AcaoAtualizacao;
}

/** Config exibida no modal: atualizar + criar + URL de envio + enviar-ao-importar + campos. */
export interface SirespConfig {
  habilitado: boolean;
  criar: boolean;
  /** URL do Sistema de Gestão que recebe o XML via HTTP POST (vazia = envio desabilitado). */
  postUrl?: string | null;
  /** Enviar o XML ao Sistema de Gestão automaticamente ao importar (só dispara se houver URL). */
  enviarAoImportar: boolean;
  campos: SirespConfigCampo[];
}

/** Payload de salvamento (campo → ação). */
export interface SirespConfigSalvar {
  habilitado: boolean;
  criar: boolean;
  postUrl: string | null;
  enviarAoImportar: boolean;
  campos: Record<string, AcaoAtualizacao>;
}

/** Resultado do botão "Enviar" (reenvia o XML ao Sistema de Gestão via Post XML). */
export interface SirespEnvioResultado {
  /** true se houve tentativa de envio ao Sistema de Gestão (false quando não há URL configurada). */
  enviado: boolean;
  /** true se o Sistema de Gestão processou o XML (só relevante quando enviado = true). */
  sucesso: boolean;
  mensagem: string;
  /** Registro atualizado (log de envio + status de envio). */
  registro: SirespDetalhe;
}
