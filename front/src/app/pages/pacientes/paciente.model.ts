/** Sexo do paciente (chave do enum no backend). */
export type Sexo = 'MASCULINO' | 'FEMININO' | 'OUTRO' | 'NAO_INFORMADO';

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

/** Responsável do paciente (cadastro paralelo: nome + telefone + permissões). */
export interface Responsavel {
  id?: number;
  nome: string;
  telefone?: string | null;
  permissoes?: PermissoesResponsavel;
}

/** Responsável no envio: id preenchido = existente; ausente = novo. */
export interface ResponsavelEntrada {
  id?: number | null;
  nome: string;
  telefone?: string | null;
  permissoes: PermissoesResponsavel;
}

export interface Paciente {
  id?: number;
  nome: string;
  telefone?: string | null;
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
  /** Foto (URL pré-assinada) para o avatar da lista; null se não tiver. */
  fotoUrl?: string | null;
}

/** Campos aceitos ao criar/editar (ativo/código são geridos pelo backend). */
export interface PacienteEntrada {
  nome: string;
  telefone?: string | null;
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
}

export interface PacienteFiltro {
  codigo?: string;
  nome?: string;
  cpf?: string;
  prontuario?: string;
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
