import { Injectable } from '@angular/core';
import { TipoManifestacaoService } from './tipo-manifestacao.service';

/** Preserva o estado da pesquisa de tipos de manifestação ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class TipoManifestacaoBuscaStore {
  nome = '';
  ativo: boolean | null = null;
  page = 0;
  size = TipoManifestacaoService.TAMANHO_PADRAO;

  limpar(): void {
    this.nome = '';
    this.ativo = null;
    this.page = 0;
    this.size = TipoManifestacaoService.TAMANHO_PADRAO;
  }
}
