import { Injectable } from '@angular/core';
import { CategoriaNpsService } from './categoria-nps.service';

/**
 * Mantém o último estado da pesquisa de categorias de NPS (filtros, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class CategoriaNpsBuscaStore {
  codigo = '';
  nome = '';
  page = 0;
  size = CategoriaNpsService.TAMANHO_PADRAO;

  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.page = 0;
    this.size = CategoriaNpsService.TAMANHO_PADRAO;
  }
}
