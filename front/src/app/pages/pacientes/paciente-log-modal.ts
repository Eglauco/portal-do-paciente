import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { PacienteLog } from './paciente.model';
import { PacienteService } from './paciente.service';

/**
 * Modal com a linha do tempo de auditoria (LGPD) do cadastro do paciente: cada evento
 * (criação, alteração, inativação, reativação) com quem fez, quando e — nas alterações —
 * os campos que mudaram (antes → depois). Carrega os logs sozinho ao abrir.
 */
@Component({
  selector: 'app-paciente-log-modal',
  imports: [DatePipe],
  templateUrl: './paciente-log-modal.html',
  styleUrl: './paciente-log-modal.css',
})
export class PacienteLogModal implements OnInit {
  private readonly service = inject(PacienteService);

  readonly pacienteId = input.required<number>();
  readonly pacienteNome = input<string>('');
  readonly fechar = output<void>();

  protected readonly logs = signal<PacienteLog[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal(false);

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.service.logs(this.pacienteId()).subscribe({
      next: (logs) => {
        this.logs.set(logs);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  /** Ícone (emoji) por tipo de evento — leitura rápida na linha do tempo. */
  protected icone(tipo: PacienteLog['tipo']): string {
    switch (tipo) {
      case 'CRIACAO':
        return '✚';
      case 'ALTERACAO':
        return '✎';
      case 'INATIVACAO':
        return '⦸';
      case 'REATIVACAO':
        return '↺';
      default:
        return '•';
    }
  }
}
