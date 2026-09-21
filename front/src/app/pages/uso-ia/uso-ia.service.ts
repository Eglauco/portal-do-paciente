import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina, UsoIa, UsoIaFiltro, UsoIaTotais } from './uso-ia.model';

/** Ledger de uso de IA (só consulta + exportação). */
@Injectable({ providedIn: 'root' })
export class UsoIaService {
  private readonly http = inject(HttpClient);
  readonly base = `${environment.apiUrl}/uso-ia`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 25;

  listar(filtro: UsoIaFiltro = {}, page = 0, size = UsoIaService.TAMANHO_PADRAO): Observable<Pagina<UsoIa>> {
    let params = new HttpParams().set('page', page).set('size', size);
    params = this.aplicarFiltro(params, filtro);
    return this.http.get<Pagina<UsoIa>>(this.base, { params });
  }

  totais(filtro: UsoIaFiltro = {}): Observable<UsoIaTotais> {
    const params = this.aplicarFiltro(new HttpParams(), filtro);
    return this.http.get<UsoIaTotais>(`${this.base}/totais`, { params });
  }

  exportar(formato: 'xlsx' | 'pdf', filtro: UsoIaFiltro = {}): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    params = this.aplicarFiltro(params, filtro);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  private aplicarFiltro(params: HttpParams, filtro: UsoIaFiltro): HttpParams {
    if (filtro.tipo) params = params.set('tipo', filtro.tipo);
    if (filtro.de) params = params.set('de', filtro.de);
    if (filtro.ate) params = params.set('ate', filtro.ate);
    if (filtro.busca?.trim()) params = params.set('busca', filtro.busca.trim());
    return params;
  }
}
