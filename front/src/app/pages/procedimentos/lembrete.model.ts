/** Lembrete de um procedimento (texto + antecedência em horas). */
export interface Lembrete {
  id: number;
  texto: string;
  horasAntecedencia: number;
}

export interface LembreteRequest {
  texto: string;
  horasAntecedencia: number;
}
