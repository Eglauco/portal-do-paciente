export interface Paciente {
  id?: number;
  nome: string;
  telefone?: string | null;
  /** Liberado para acessar o app. */
  ativo?: boolean;
}

/** Campos aceitos ao criar/editar (ativo/código são geridos pelo backend). */
export type PacienteEntrada = Pick<Paciente, 'nome' | 'telefone'>;

/** Código de ativação gerado para o paciente (mostrado uma única vez). */
export interface CodigoAtivacao {
  codigo: string;
  expiraEm: string;
}

export interface PacienteFiltro {
  codigo?: string;
  nome?: string;
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
