import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Ordenacao, ordenacoesParaParametros } from '../../shared/ordenacao/ordenacao.model';
import {
  Pagina,
  TipoDocumentoProntuario,
  TipoDocumentoProntuarioAtivo,
  TipoDocumentoProntuarioFiltro,
} from './tipo-documento-prontuario.model';

@Injectable({ providedIn: 'root' })
export class TipoDocumentoProntuarioService {
  private readonly http = inject(HttpClient);
  readonly base = `${environment.apiUrl}/tipo-documento-prontuario`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: TipoDocumentoProntuarioFiltro = {},
    page = 0,
    size = TipoDocumentoProntuarioService.TAMANHO_PADRAO,
    ordenacoes: readonly Ordenacao[] = [],
  ): Observable<Pagina<TipoDocumentoProntuario>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    if (filtro.ativo != null) params = params.set('ativo', filtro.ativo);
    for (const o of ordenacoesParaParametros(ordenacoes)) params = params.append('ordenar', o);
    return this.http.get<Pagina<TipoDocumentoProntuario>>(this.base, { params });
  }

  /** Tipos ativos (para o seletor no upload de documentos do prontuário). */
  listarAtivos(): Observable<TipoDocumentoProntuarioAtivo[]> {
    return this.http.get<TipoDocumentoProntuarioAtivo[]>(`${this.base}/ativos`);
  }

  buscarPorId(id: number): Observable<TipoDocumentoProntuario> {
    return this.http.get<TipoDocumentoProntuario>(`${this.base}/${id}`);
  }

  criar(tipo: TipoDocumentoProntuario): Observable<TipoDocumentoProntuario> {
    return this.http.post<TipoDocumentoProntuario>(this.base, tipo);
  }

  atualizar(id: number, tipo: TipoDocumentoProntuario): Observable<TipoDocumentoProntuario> {
    return this.http.put<TipoDocumentoProntuario>(`${this.base}/${id}`, tipo);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
