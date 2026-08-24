export type StatusAgendamento =
  | 'confirmado'
  | 'aguardando'
  | 'realizado'
  | 'cancelado'
  | 'reagendado';

export interface Agendamento {
  id: string;
  dia: string;
  mes: string;
  semana: string;
  especialidade: string;
  profissional: string;
  hora: string;
  unidade: string;
  status: StatusAgendamento;
  grupo: 'proximos' | 'concluidos';
}

export const AGENDAMENTOS: Agendamento[] = [
  { id: '1', dia: '26', mes: 'AGO', semana: 'Ter', especialidade: 'Cardiologia', profissional: 'Dr. Rafael Lima', hora: '14:30', unidade: 'Unidade de Saúde 01', status: 'aguardando', grupo: 'proximos' },
  { id: '2', dia: '30', mes: 'AGO', semana: 'Sáb', especialidade: 'Retorno — Dermatologia', profissional: 'Dra. Helena Costa', hora: '09:00', unidade: 'Unidade de Saúde 03', status: 'aguardando', grupo: 'proximos' },
  { id: '3', dia: '28', mes: 'AGO', semana: 'Qui', especialidade: 'Exame — Coleta de sangue', profissional: 'Laboratório central', hora: '08:00', unidade: 'Unidade de Saúde 01', status: 'confirmado', grupo: 'proximos' },
  { id: '4', dia: '02', mes: 'SET', semana: 'Ter', especialidade: 'Ortopedia', profissional: 'Dra. Mariana Duarte', hora: '10:15', unidade: 'Unidade de Saúde 02', status: 'reagendado', grupo: 'proximos' },
  { id: '5', dia: '18', mes: 'AGO', semana: 'Seg', especialidade: 'Clínico Geral', profissional: 'Dr. Paulo Nunes', hora: '09:30', unidade: 'Unidade de Saúde 01', status: 'realizado', grupo: 'concluidos' },
  { id: '6', dia: '05', mes: 'AGO', semana: 'Ter', especialidade: 'Oftalmologia', profissional: 'Dr. Carlos Mendes', hora: '11:00', unidade: 'Unidade de Saúde 01', status: 'cancelado', grupo: 'concluidos' },
];
