import { Injectable } from '@angular/core';
import { PostagemService } from './postagem.service';

/** Mantém o estado da pesquisa de postagens ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class PostagemBuscaStore {
  titulo = '';
  unidadeId: number | null = null;
  comentarios: boolean | null = null;
  page = 0;
  size = PostagemService.TAMANHO_PADRAO;

  limpar(): void {
    this.titulo = '';
    this.unidadeId = null;
    this.comentarios = null;
    this.page = 0;
    this.size = PostagemService.TAMANHO_PADRAO;
  }
}
