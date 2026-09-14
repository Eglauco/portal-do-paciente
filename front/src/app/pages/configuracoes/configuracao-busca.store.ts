import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { ConfiguracaoService } from './configuracao.service';

/** Preserva o estado da pesquisa de configurações ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class ConfiguracaoBuscaStore {
  busca = '';
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = ConfiguracaoService.TAMANHO_PADRAO;

  limpar(): void {
    this.busca = '';
    this.page = 0;
    this.size = ConfiguracaoService.TAMANHO_PADRAO;
  }
}
