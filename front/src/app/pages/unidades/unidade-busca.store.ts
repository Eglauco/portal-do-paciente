import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { UnidadeService } from './unidade.service';

/**
 * Mantém o último estado da pesquisa de unidades (filtros, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class UnidadeBuscaStore {
  codigo = '';
  nome = '';
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = UnidadeService.TAMANHO_PADRAO;

  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.page = 0;
    this.size = UnidadeService.TAMANHO_PADRAO;
  }
}
