import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface Appointment {
  time: string;
  patient: string;
  professional: string;
  type: string;
  status: 'confirmado' | 'aguardando' | 'cancelado';
}

interface Activity {
  kind: 'paciente' | 'agenda' | 'chat' | 'nps';
  text: string;
  time: string;
}

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  templateUrl: './home.html',
})
export class Home {
  protected readonly greeting = this.buildGreeting();
  protected readonly today = this.buildDate();

  protected readonly appointments: Appointment[] = [
    { time: '08:30', patient: 'Maria Oliveira', professional: 'Dra. Helena Costa', type: 'Consulta clínica', status: 'confirmado' },
    { time: '09:15', patient: 'João Almeida', professional: 'Dr. Rafael Lima', type: 'Retorno', status: 'confirmado' },
    { time: '10:00', patient: 'Ana Beatriz Souza', professional: 'Dra. Helena Costa', type: 'Exame — coleta', status: 'aguardando' },
    { time: '11:30', patient: 'Carlos Mendes', professional: 'Dr. Paulo Nunes', type: 'Consulta clínica', status: 'confirmado' },
    { time: '14:00', patient: 'Fernanda Dias', professional: 'Dra. Helena Costa', type: 'Retorno', status: 'cancelado' },
  ];

  protected readonly activity: Activity[] = [
    { kind: 'paciente', text: 'Novo paciente cadastrado — Lucas Ferreira', time: 'há 8 min' },
    { kind: 'agenda', text: 'Agendamento remarcado para 26/08 — Ana Beatriz', time: 'há 22 min' },
    { kind: 'chat', text: '2 pacientes aguardando no chat ao vivo', time: 'há 35 min' },
    { kind: 'nps', text: 'Nova avaliação NPS recebida — nota 9', time: 'há 1 h' },
    { kind: 'paciente', text: 'Prontuário atualizado — Carlos Mendes', time: 'há 2 h' },
  ];

  private buildGreeting(): string {
    const h = new Date().getHours();
    if (h < 12) return 'Bom dia';
    if (h < 18) return 'Boa tarde';
    return 'Boa noite';
  }

  private buildDate(): string {
    const formatted = new Intl.DateTimeFormat('pt-BR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    }).format(new Date());
    return formatted.charAt(0).toUpperCase() + formatted.slice(1);
  }
}
