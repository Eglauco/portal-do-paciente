import { Injectable } from '@angular/core';
import { ProcedimentoService } from './procedimento.service';

/**
 * Mantém o último estado da pesquisa de procedimentos (filtros, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class ProcedimentoBuscaStore {
  codigo = '';
  nome = '';
  page = 0;
  size = ProcedimentoService.TAMANHO_PADRAO;

  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.page = 0;
    this.size = ProcedimentoService.TAMANHO_PADRAO;
  }
}
