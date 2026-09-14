/** Referência leve de conselho (para o vínculo). */
export interface ConselhoRef {
  id: number;
  sigla: string;
  nome: string;
}

/** Referência leve (id + nome) de especialidade/unidade nos vínculos. */
export interface RefNome {
  id: number;
  nome: string;
}

export interface ProfissionalSaude {
  id?: number;
  nome: string;
  conselho?: ConselhoRef | null;
  numeroConselho?: string | null;
  sexo?: string | null;
  dataNascimento?: string | null;
  rg?: string | null;
  cpf?: string | null;
  cns?: string | null;
  telefone?: string | null;
  telefonesAdicionais?: string[];
  rua?: string | null;
  numero?: string | null;
  bairro?: string | null;
  municipio?: string | null;
  uf?: string | null;
  cep?: string | null;
  complemento?: string | null;
  email?: string | null;
  fotoUrl?: string | null;
  codigoIntegracao?: string | null;
  ativo?: boolean;
  inativadoEm?: string | null;
  especialidades?: RefNome[];
  unidades?: RefNome[];
}

/** Corpo enviado ao criar/editar (mapeia para o ProfissionalSaudeRequest do backend). */
export interface ProfissionalSaudeEntrada {
  nome: string;
  conselhoId?: number | null;
  numeroConselho?: string | null;
  sexo?: string | null;
  dataNascimento?: string | null;
  rg?: string | null;
  cpf?: string | null;
  cns?: string | null;
  telefone?: string | null;
  telefonesAdicionais?: string[];
  rua?: string | null;
  numero?: string | null;
  bairro?: string | null;
  municipio?: string | null;
  uf?: string | null;
  cep?: string | null;
  complemento?: string | null;
  email?: string | null;
  fotoUrl?: string | null;
  codigoIntegracao?: string | null;
  especialidadeIds?: number[];
  unidadeIds?: number[];
}

export interface ProfissionalSaudeFiltro {
  codigo?: string;
  nome?: string;
  /** ATIVO (padrão) | INATIVO | TODOS. */
  situacao?: 'ATIVO' | 'INATIVO' | 'TODOS';
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
