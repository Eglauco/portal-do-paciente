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
  /** Diagnóstico da integração (encontrou/não encontrou cada entidade pelo código de integração). */
  logIntegracao?: string | null;

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
}

/** Resultado de uma importação de XML (inclui o desfecho do envio automático ao cliente, quando ligado). */
export interface SirespImportResultado {
  importados: number;
  arquivo?: string | null;
  pacientesAtualizados: number;
  pacientesCriados: number;
  /** true se houve tentativa de envio automático ao cliente após o import. */
  enviado: boolean;
  /** true se o cliente processou o XML (só relevante quando enviado = true). */
  envioSucesso: boolean;
  envioMensagem?: string | null;
}

export interface SirespFiltro {
  busca?: string | null;
  status?: SirespStatus | null;
}

/** Política de atualização de um campo do paciente a partir do XML. */
export type AcaoAtualizacao = 'SEMPRE' | 'SE_VAZIO' | 'NUNCA';

export interface SirespConfigCampo {
  campo: string;
  rotulo: string;
  acao: AcaoAtualizacao;
}

/** Opção do dropdown de procedimento padrão. */
export interface SirespProcedimentoOpcao {
  id: number;
  nome: string;
}

/** Config exibida no modal: atualizar + criar + URL de envio + enviar-ao-importar + procedimento padrão + campos. */
export interface SirespConfig {
  habilitado: boolean;
  criar: boolean;
  /** URL do cliente que recebe o XML via HTTP POST (vazia = envio desabilitado). */
  postUrl?: string | null;
  /** Enviar o XML ao cliente automaticamente ao importar (só dispara se houver URL). */
  enviarAoImportar: boolean;
  /** Id do procedimento padrão usado nos agendamentos do SIRESP (null = não configurado). */
  procedimentoPadraoId?: number | null;
  /** Procedimentos disponíveis para escolher o padrão. */
  procedimentos: SirespProcedimentoOpcao[];
  campos: SirespConfigCampo[];
}

/** Payload de salvamento (campo → ação). */
export interface SirespConfigSalvar {
  habilitado: boolean;
  criar: boolean;
  postUrl: string | null;
  enviarAoImportar: boolean;
  procedimentoPadraoId: number | null;
  campos: Record<string, AcaoAtualizacao>;
}

/** Resultado do botão "Reprocessar e enviar" (reprocessa o log e, se houver URL, reenvia o XML ao cliente). */
export interface SirespEnvioResultado {
  /** true se houve tentativa de envio ao cliente (false quando só reprocessou, por falta de URL). */
  enviado: boolean;
  /** true se o cliente processou o XML (só relevante quando enviado = true). */
  sucesso: boolean;
  mensagem: string;
  /** Registro atualizado (log recalculado e/ou resultado do envio anexado). */
  registro: SirespDetalhe;
}
