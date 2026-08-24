import { Injectable } from '@angular/core';
import { AgendamentoService } from './agendamento.service';
import { StatusAgendamento } from './agendamento.model';

/** Mantém o estado da pesquisa de agendamentos ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class AgendamentoBuscaStore {
  status: StatusAgendamento | null = null;
  page = 0;
  size = AgendamentoService.TAMANHO_PADRAO;

  limpar(): void {
    this.status = null;
    this.page = 0;
    this.size = AgendamentoService.TAMANHO_PADRAO;
  }
}
