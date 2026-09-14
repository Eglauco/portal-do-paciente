import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { ProcedimentoService } from './procedimento.service';

/**
 * Mantém o último estado da pesquisa de procedimentos (filtros, ordenação, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class ProcedimentoBuscaStore {
  codigo = '';
  nome = '';
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = ProcedimentoService.TAMANHO_PADRAO;

  /** Limpa apenas os filtros (a ordenação tem o próprio "Limpar ordenação"). */
  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.page = 0;
    this.size = ProcedimentoService.TAMANHO_PADRAO;
  }
}
