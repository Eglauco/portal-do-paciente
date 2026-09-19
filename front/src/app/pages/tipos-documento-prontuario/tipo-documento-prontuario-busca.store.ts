import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { TipoDocumentoProntuarioService } from './tipo-documento-prontuario.service';

/** Preserva o estado da pesquisa de tipos de documento ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class TipoDocumentoProntuarioBuscaStore {
  nome = '';
  ativo: boolean | null = null;
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = TipoDocumentoProntuarioService.TAMANHO_PADRAO;

  limpar(): void {
    this.nome = '';
    this.ativo = null;
    this.page = 0;
    this.size = TipoDocumentoProntuarioService.TAMANHO_PADRAO;
  }
}
