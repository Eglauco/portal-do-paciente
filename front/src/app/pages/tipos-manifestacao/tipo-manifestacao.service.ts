import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina, TipoManifestacao, TipoManifestacaoFiltro } from './tipo-manifestacao.model';

@Injectable({ providedIn: 'root' })
export class TipoManifestacaoService {
  private readonly http = inject(HttpClient);
  readonly base = `${environment.apiUrl}/tipo-manifestacao`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: TipoManifestacaoFiltro = {},
    page = 0,
    size = TipoManifestacaoService.TAMANHO_PADRAO,
  ): Observable<Pagina<TipoManifestacao>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    if (filtro.ativo != null) params = params.set('ativo', filtro.ativo);
    return this.http.get<Pagina<TipoManifestacao>>(this.base, { params });
  }

  /** Exporta os tipos dos filtros atuais em Excel ou PDF, só com as colunas escolhidas. */
  exportar(
    formato: 'xlsx' | 'pdf',
    filtro: TipoManifestacaoFiltro = {},
    colunas: string[] = [],
  ): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    if (filtro.ativo != null) params = params.set('ativo', filtro.ativo);
    for (const c of colunas) params = params.append('colunas', c);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<TipoManifestacao> {
    return this.http.get<TipoManifestacao>(`${this.base}/${id}`);
  }

  criar(tipo: TipoManifestacao): Observable<TipoManifestacao> {
    return this.http.post<TipoManifestacao>(this.base, tipo);
  }

  atualizar(id: number, tipo: TipoManifestacao): Observable<TipoManifestacao> {
    return this.http.put<TipoManifestacao>(`${this.base}/${id}`, tipo);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
