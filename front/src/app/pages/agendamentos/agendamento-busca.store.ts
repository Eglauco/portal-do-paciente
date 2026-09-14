import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { AgendamentoService } from './agendamento.service';
import { EstadoEntrega, StatusAgendamento } from './agendamento.model';

/** Mantém o estado da pesquisa de agendamentos ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class AgendamentoBuscaStore {
  status: StatusAgendamento | null = null;
  nome = '';
  especialidadeNome = '';
  profissionalNome = '';
  entregaResumo: EstadoEntrega | null = null;
  data = '';
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = AgendamentoService.TAMANHO_PADRAO;

  limpar(): void {
    this.status = null;
    this.nome = '';
    this.especialidadeNome = '';
    this.profissionalNome = '';
    this.entregaResumo = null;
    this.data = '';
    this.page = 0;
    this.size = AgendamentoService.TAMANHO_PADRAO;
  }
}
