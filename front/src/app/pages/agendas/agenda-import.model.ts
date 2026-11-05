/** Tipos do preview da importação de Agenda por Excel (Fase 1 — só preview; nada é gravado). */

/** Uma célula do preview: valor digitado, valor resolvido (nome casado/forma normalizada) e erro + mensagem. */
export interface CampoPreview {
  valor: string;
  resolvido?: string | null;
  erro: boolean;
  mensagem?: string | null;
}

/** Cabeçalho da agenda (o slot) — preenchido uma vez na planilha. Resolvido por id/código (nunca por nome). */
export interface AgendaPreview {
  data: CampoPreview;
  profissional: CampoPreview;
  especialidade: CampoPreview;
  configuracaoAgenda: CampoPreview;
  /** Unidade executante — vem do usuário logado (não da planilha). */
  unidade: CampoPreview;
  nome: CampoPreview;
}

/**
 * Uma marcação (um paciente) da planilha. `linha` é o número da linha no Excel para localizar/corrigir.
 * O paciente é localizado por id/prontuário/código; `paciente.resolvido` traz o nome encontrado.
 * CPF/Nome/Telefone/Data de nascimento são obrigatórios por linha; Nome social é opcional. Esses dados ficam
 * gravados junto ao horário (não alteram o cadastro do paciente).
 */
export interface HorarioPreview {
  linha: number;
  paciente: CampoPreview;
  cpf: CampoPreview;
  nome: CampoPreview;
  nomeSocial: CampoPreview;
  telefone: CampoPreview;
  dataNascimento: CampoPreview;
  horaInicio: CampoPreview;
}

/** Preview completo devolvido pelo backend. */
export interface AgendaImportPreview {
  arquivo: string;
  agenda: AgendaPreview;
  horarios: HorarioPreview[];
  totalHorarios: number;
  totalErros: number;
}

/** Resultado da confirmação (Fase 2): a agenda criada + quantos horários foram gravados. */
export interface AgendaImportResultado {
  agendaId: number;
  totalHorarios: number;
}
