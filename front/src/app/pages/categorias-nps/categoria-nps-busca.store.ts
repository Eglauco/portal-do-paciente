import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { CategoriaNpsService } from './categoria-nps.service';

/**
 * Mantém o último estado da pesquisa de categorias de NPS (filtros, ordenação, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class CategoriaNpsBuscaStore {
  codigo = '';
  nome = '';
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = CategoriaNpsService.TAMANHO_PADRAO;

  /** Limpa apenas os filtros (a ordenação tem o próprio "Limpar ordenação"). */
  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.page = 0;
    this.size = CategoriaNpsService.TAMANHO_PADRAO;
  }
}
