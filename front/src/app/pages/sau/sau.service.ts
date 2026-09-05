import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Manifestacao, ManifestacaoDetalhe, ManifestacaoFiltro, Pagina } from './sau.model';

@Injectable({ providedIn: 'root' })
export class SauService {
  private readonly http = inject(HttpClient);
  readonly base = `${environment.apiUrl}/sau`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(filtro: ManifestacaoFiltro = {}, page = 0, size = SauService.TAMANHO_PADRAO): Observable<Pagina<Manifestacao>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.unidadeId) params = params.set('unidadeId', filtro.unidadeId);
    if (filtro.tipoId) params = params.set('tipoId', filtro.tipoId);
    if (filtro.status) params = params.set('status', filtro.status);
    return this.http.get<Pagina<Manifestacao>>(this.base, { params });
  }

  /** Exporta as manifestações dos filtros atuais em Excel ou PDF, só com as colunas escolhidas. */
  exportar(formato: 'xlsx' | 'pdf', filtro: ManifestacaoFiltro = {}, colunas: string[] = []): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.unidadeId) params = params.set('unidadeId', filtro.unidadeId);
    if (filtro.tipoId) params = params.set('tipoId', filtro.tipoId);
    if (filtro.status) params = params.set('status', filtro.status);
    for (const c of colunas) params = params.append('colunas', c);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  detalhe(id: number): Observable<ManifestacaoDetalhe> {
    return this.http.get<ManifestacaoDetalhe>(`${this.base}/${id}`);
  }

  /** SAU responde: registra o atendente logado e notifica o paciente. */
  responder(id: number, texto: string): Observable<ManifestacaoDetalhe> {
    return this.http.post<ManifestacaoDetalhe>(`${this.base}/${id}/mensagem`, { texto });
  }

  /** Marca a manifestação como fechada. */
  fechar(id: number): Observable<ManifestacaoDetalhe> {
    return this.http.post<ManifestacaoDetalhe>(`${this.base}/${id}/fechar`, {});
  }
}
