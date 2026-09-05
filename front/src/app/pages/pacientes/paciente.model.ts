/** Sexo do paciente (chave do enum no backend). */
export type Sexo = 'MASCULINO' | 'FEMININO' | 'OUTRO' | 'NAO_INFORMADO';

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
