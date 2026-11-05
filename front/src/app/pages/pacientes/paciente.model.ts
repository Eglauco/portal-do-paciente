/** Sexo do paciente (chave do enum no backend). */
export type Sexo = 'MASCULINO' | 'FEMININO' | 'OUTRO' | 'NAO_INFORMADO';

/**
 * Nome de EXIBIÇÃO do paciente: o nome social quando o paciente opta por usá-lo (e ele está
 * preenchido); senão, o nome civil. Usado nas telas que mostram a entidade completa (lista/cadastro).
 * Os demais payloads (Ref de chat/agendamento/NPS/…) já chegam com o nome de exibição pronto do backend.
 */
export function nomeExibicao(
  p: { nome: string; nomeSocial?: string | null; utilizaNomeSocial?: boolean } | null | undefined,
): string {
  if (!p) return '';
  return p.utilizaNomeSocial && p.nomeSocial?.trim() ? p.nomeSocial : p.nome;
}

/**
 * Converte a entidade Paciente completa (lista/cadastro) na forma esperada por `<app-nome-paciente>`:
 * nome de exibição + nome de registro (civil) + flag. Para Refs vindos do backend (chat, agenda, …)
 * os três campos já chegam prontos; este helper é para onde o front tem a entidade inteira.
 */
export function refExibicao(
  p: { nome: string; nomeSocial?: string | null; utilizaNomeSocial?: boolean },
): { nome: string; nomeRegistro: string; temNomeSocial: boolean } {
  return {
    nome: nomeExibicao(p),
    nomeRegistro: p.nome,
    temNomeSocial: !!(p.utilizaNomeSocial && p.nomeSocial?.trim()),
  };
}

/** Situação do cadastro (soft-delete). Não confundir com `ativo` (acesso ao app). */
export type SituacaoCadastro = 'ATIVO' | 'INATIVO';

/** Paciente enxuto para o typeahead de seleção (nome + CPF + prontuário para desambiguar). */
export interface PacienteSelecao {
  id: number;
  nome: string;
  cpf?: string | null;
  prontuario?: string | null;
}

/** Funcionalidade do app sujeita à permissão do responsável. */
export type FuncionalidadeApp =
  | 'AGENDAMENTOS'
  | 'CHAT'
  | 'SAU'
  | 'REDE_SOCIAL'
  | 'MEU_PERFIL'
  | 'PRONTUARIO'
  | 'NPS';

/** Nível de acesso do responsável a uma funcionalidade. */
export type NivelAcesso = 'SEM_ACESSO' | 'VISUALIZAR' | 'VISUALIZAR_LANCAR';

/** Permissões por funcionalidade (ausente = SEM_ACESSO). */
export type PermissoesResponsavel = Partial<Record<FuncionalidadeApp, NivelAcesso>>;

/**
 * Funcionalidades do app, na ordem de exibição, com rótulo. {@code semLancamento} =
 * a funcionalidade não tem "Visualizar e lançar" (o lançamento é feito pelo admin),
 * então só oferece Sem acesso / Só visualizar. É o caso do Prontuário.
 */
export const FUNCIONALIDADES_APP: { value: FuncionalidadeApp; label: string; semLancamento?: boolean }[] = [
  { value: 'AGENDAMENTOS', label: 'Agendamentos' },
  { value: 'CHAT', label: 'Chat' },
  { value: 'SAU', label: 'SAU (Manifestações)' },
  { value: 'REDE_SOCIAL', label: 'Rede Social' },
  { value: 'MEU_PERFIL', label: 'Meu Perfil' },
  { value: 'PRONTUARIO', label: 'Prontuário', semLancamento: true },
  { value: 'NPS', label: 'NPS' },
];

/** Níveis de acesso, na ordem de exibição, com rótulo. */
export const NIVEIS_ACESSO: { value: NivelAcesso; label: string }[] = [
  { value: 'SEM_ACESSO', label: 'Sem acesso' },
  { value: 'VISUALIZAR', label: 'Só visualizar' },
  { value: 'VISUALIZAR_LANCAR', label: 'Visualizar e lançar' },
];

/** Níveis para funcionalidades sem lançamento pelo paciente (ex.: Prontuário). */
export const NIVEIS_SEM_LANCAMENTO: { value: NivelAcesso; label: string }[] = NIVEIS_ACESSO.filter(
  (n) => n.value !== 'VISUALIZAR_LANCAR',
);

/** Responsável do paciente (cadastro paralelo: nome + cpf + telefone + permissões). */
export interface Responsavel {
  id?: number;
  nome: string;
  /** CPF (só dígitos, opcional na resposta). */
  cpf?: string | null;
  telefone?: string | null;
  /** Data de nascimento (ISO, opcional). Usada p/ validar a idade mínima ao comentar na rede social. */
  dataNascimento?: string | null;
  permissoes?: PermissoesResponsavel;
  /** Ativo (soft-delete/acesso). Inativo perde acesso ao perfil no app. */
  ativo?: boolean;
  /** Tem lançamentos no sistema? Quando true, não pode ser removido (só inativado). */
  temLancamentos?: boolean;
}

/** Responsável no envio: id preenchido = existente; ausente = novo. */
export interface ResponsavelEntrada {
  id?: number | null;
  nome: string;
  /** CPF (só dígitos); obrigatório no cadastro. */
  cpf: string;
  telefone?: string | null;
  dataNascimento?: string | null;
  permissoes: PermissoesResponsavel;
  ativo: boolean;
}

export interface Paciente {
  id?: number;
  /** Nome civil (registro). Sempre preservado. */
  nome: string;
  /** Nome social (opcional): nome de identificação/exibição do paciente. */
  nomeSocial?: string | null;
  /** Quando true (e há nome social), o atendimento exibe o nome social no lugar do civil. */
  utilizaNomeSocial?: boolean;
  codigoIntegracao?: string | null;
  prontuario?: string | null;
  sexo?: Sexo | null;
  /** Data ISO (yyyy-MM-dd). */
  dataNascimento?: string | null;
  rg?: string | null;
  cpf?: string | null;
  nomeMae?: string | null;
  nomePai?: string | null;
  rua?: string | null;
  numero?: string | null;
  bairro?: string | null;
  municipio?: string | null;
  uf?: string | null;
  cep?: string | null;
  complemento?: string | null;
  email?: string | null;
  cns?: string | null;
  telefonesAdicionais?: string[];
  responsaveis?: Responsavel[];
  /** Liberado para acessar o app. */
  ativo?: boolean;
  /** Situação do cadastro (soft-delete): ATIVO ou INATIVO. */
  situacao?: SituacaoCadastro;
  /** Unidades de saúde que o paciente pode acessar (feed/chat/SAU). */
  unidades?: { id: number; nome: string }[];
  /** Foto (URL pré-assinada) para o avatar da lista; null se não tiver. */
  fotoUrl?: string | null;
}

/** Campos aceitos ao criar/editar (ativo/código são geridos pelo backend). */
export interface PacienteEntrada {
  nome: string;
  /** Nome social (opcional). */
  nomeSocial?: string | null;
  /** Usa o nome social no atendimento. Exige nomeSocial preenchido. */
  utilizaNomeSocial?: boolean;
  codigoIntegracao?: string | null;
  prontuario?: string | null;
  sexo?: Sexo | null;
  /** Data ISO (yyyy-MM-dd) ou null. */
  dataNascimento?: string | null;
  rg?: string | null;
  cpf?: string | null;
  nomeMae?: string | null;
  nomePai?: string | null;
  rua?: string | null;
  numero?: string | null;
  bairro?: string | null;
  municipio?: string | null;
  uf?: string | null;
  cep?: string | null;
  complemento?: string | null;
  email?: string | null;
  cns?: string | null;
  telefonesAdicionais: string[];
  responsaveis: ResponsavelEntrada[];
  /** Ids das unidades de saúde que o paciente pode acessar. */
  unidadeIds: number[];
}

export interface PacienteFiltro {
  codigo?: string;
  nome?: string;
  cpf?: string;
  prontuario?: string;
  /** ATIVO (padrão no backend), INATIVO ou TODOS. */
  situacao?: 'ATIVO' | 'INATIVO' | 'TODOS';
}

/** Tipo de evento na auditoria do cadastro. */
export type TipoEventoPaciente = 'CRIACAO' | 'ALTERACAO' | 'INATIVACAO' | 'REATIVACAO';

/** Quem realizou o evento de auditoria. */
export type AutorLogPaciente = 'UNIDADE' | 'PACIENTE' | 'RESPONSAVEL' | 'SISTEMA' | 'IMPORTACAO_SIRESP';

/** Alteração de um campo (antes/depois) dentro de um evento de auditoria. */
export interface PacienteLogAlteracao {
  campo: string;
  campoDescricao: string;
  valorAntes?: string | null;
  valorDepois?: string | null;
}

/** Item da linha do tempo de auditoria do cadastro. */
export interface PacienteLog {
  id: number;
  tipo: TipoEventoPaciente;
  tipoDescricao: string;
  autor: AutorLogPaciente;
  /** Nome de quem fez a ação; null = sem ator identificado (exibir "Sistema"). */
  autorNome?: string | null;
  criadoEm: string;
  alteracoes: PacienteLogAlteracao[];
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
