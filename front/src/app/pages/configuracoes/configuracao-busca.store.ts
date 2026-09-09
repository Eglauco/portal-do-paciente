import { Injectable } from '@angular/core';
import { ConfiguracaoService } from './configuracao.service';

/** Preserva o estado da pesquisa de configurações ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class ConfiguracaoBuscaStore {
  busca = '';
  page = 0;
  size = ConfiguracaoService.TAMANHO_PADRAO;

  limpar(): void {
    this.busca = '';
    this.page = 0;
    this.size = ConfiguracaoService.TAMANHO_PADRAO;
  }
}
