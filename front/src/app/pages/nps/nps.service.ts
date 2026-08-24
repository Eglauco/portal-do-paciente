import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Nps, NpsDetalhe, NpsFiltro, Pagina } from './nps.model';

@Injectable({ providedIn: 'root' })
export class NpsService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/nps`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(filtro: NpsFiltro = {}, page = 0, size = NpsService.TAMANHO_PADRAO): Observable<Pagina<Nps>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.status) params = params.set('status', filtro.status);
    if (filtro.pacienteId) params = params.set('pacienteId', filtro.pacienteId);
    if (filtro.unidadeId) params = params.set('unidadeId', filtro.unidadeId);
    return this.http.get<Pagina<Nps>>(this.base, { params });
  }

  detalhe(id: number): Observable<NpsDetalhe> {
    return this.http.get<NpsDetalhe>(`${this.base}/${id}`);
  }

  responder(id: number, nota: number, observacao?: string | null): Observable<NpsDetalhe> {
    return this.http.post<NpsDetalhe>(`${this.base}/${id}/responder`, { nota, observacao });
  }

  expirar(id: number): Observable<NpsDetalhe> {
    return this.http.post<NpsDetalhe>(`${this.base}/${id}/expirar`, {});
  }
}
