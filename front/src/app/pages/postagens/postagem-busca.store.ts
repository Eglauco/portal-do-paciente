import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { PostagemService } from './postagem.service';

/** Mantém o estado da pesquisa de postagens ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class PostagemBuscaStore {
  titulo = '';
  unidadeId: number | null = null;
  comentarios: boolean | null = null;
  novoComentario: boolean | null = null;
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = PostagemService.TAMANHO_PADRAO;

  /** Limpa apenas os filtros (a ordenação tem o próprio "Limpar ordenação"). */
  limpar(): void {
    this.titulo = '';
    this.unidadeId = null;
    this.comentarios = null;
    this.novoComentario = null;
    this.page = 0;
    this.size = PostagemService.TAMANHO_PADRAO;
  }
}
