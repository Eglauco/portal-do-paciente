import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Comentario,
  Pagina,
  PaginaComentarios,
  Postagem,
  PostagemDetalhe,
  PostagemFiltro,
  PostagemRequest,
} from './postagem.model';

@Injectable({ providedIn: 'root' })
export class PostagemService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/postagem`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: PostagemFiltro = {},
    page = 0,
    size = PostagemService.TAMANHO_PADRAO,
  ): Observable<Pagina<Postagem>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.titulo) params = params.set('titulo', filtro.titulo);
    if (filtro.unidadeId) params = params.set('unidadeId', filtro.unidadeId);
    if (filtro.comentarios !== null && filtro.comentarios !== undefined) {
      params = params.set('comentarios', filtro.comentarios);
    }
    return this.http.get<Pagina<Postagem>>(this.base, { params });
  }

  /** Exporta as postagens dos filtros atuais em Excel ou PDF (arquivo binário). */
  exportar(formato: 'xlsx' | 'pdf', filtro: PostagemFiltro = {}): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.titulo) params = params.set('titulo', filtro.titulo);
    if (filtro.unidadeId) params = params.set('unidadeId', filtro.unidadeId);
    if (filtro.comentarios !== null && filtro.comentarios !== undefined) {
      params = params.set('comentarios', filtro.comentarios);
    }
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<PostagemDetalhe> {
    return this.http.get<PostagemDetalhe>(`${this.base}/${id}`);
  }

  criar(postagem: PostagemRequest): Observable<PostagemDetalhe> {
    return this.http.post<PostagemDetalhe>(this.base, postagem);
  }

  atualizar(id: number, postagem: PostagemRequest): Observable<PostagemDetalhe> {
    return this.http.put<PostagemDetalhe>(`${this.base}/${id}`, postagem);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  listarComentarios(id: number, page = 0, size = 20): Observable<PaginaComentarios> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PaginaComentarios>(`${this.base}/${id}/comentarios`, { params });
  }

  excluirComentario(comentarioId: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/comentario/${comentarioId}`);
  }

  responderComentario(comentarioId: number, autor: string, texto: string): Observable<Comentario> {
    return this.http.post<Comentario>(`${this.base}/comentario/${comentarioId}/responder`, { autor, texto });
  }

  /** Edita um comentário do próprio admin (permitido só até 15 min após criar). */
  editarComentario(comentarioId: number, texto: string): Observable<Comentario> {
    return this.http.put<Comentario>(`${this.base}/comentario/${comentarioId}`, { texto });
  }
}
