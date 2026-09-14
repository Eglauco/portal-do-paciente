import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { TipoManifestacaoService } from './tipo-manifestacao.service';

/** Preserva o estado da pesquisa de tipos de manifestação ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class TipoManifestacaoBuscaStore {
  nome = '';
  ativo: boolean | null = null;
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = TipoManifestacaoService.TAMANHO_PADRAO;

  limpar(): void {
    this.nome = '';
    this.ativo = null;
    this.page = 0;
    this.size = TipoManifestacaoService.TAMANHO_PADRAO;
  }
}
