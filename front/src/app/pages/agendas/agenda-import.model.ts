/** Tipos do preview da importação de Agenda por Excel (Fase 1 — só preview; nada é gravado). */

/** Uma célula do preview: valor digitado, valor resolvido (nome casado/forma normalizada) e erro + mensagem. */
export interface CampoPreview {
  valor: string;
  resolvido?: string | null;
  erro: boolean;
  mensagem?: string | null;
}

/** Cabeçalho da agenda (o slot) — preenchido uma vez na planilha. */
export interface AgendaPreview {
  data: CampoPreview;
  profissional: CampoPreview;
  especialidade: CampoPreview;
  /** Rótulo visível de "Procedimento" nesta tela. */
  configuracaoAgenda: CampoPreview;
  unidade: CampoPreview;
  nome: CampoPreview;
}

/** Uma marcação (um paciente) da planilha. `linha` é o número da linha no Excel para localizar/corrigir. */
export interface HorarioPreview {
  linha: number;
  paciente: CampoPreview;
  cpf: CampoPreview;
  horaInicio: CampoPreview;
  horaFim: CampoPreview;
  status: CampoPreview;
}

/** Preview completo devolvido pelo backend. */
export interface AgendaImportPreview {
  arquivo: string;
  agenda: AgendaPreview;
  horarios: HorarioPreview[];
  totalHorarios: number;
  totalErros: number;
}
