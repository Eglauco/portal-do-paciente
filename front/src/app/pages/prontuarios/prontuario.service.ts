import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Pagina,
  Prontuario,
  ProntuarioDetalhe,
  ProntuarioFiltro,
  ProntuarioRequest,
} from './prontuario.model';

@Injectable({ providedIn: 'root' })
export class ProntuarioService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/prontuario`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: ProntuarioFiltro = {},
    page = 0,
    size = ProntuarioService.TAMANHO_PADRAO,
  ): Observable<Pagina<Prontuario>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.numero) params = params.set('numero', filtro.numero);
    if (filtro.pacienteId) params = params.set('pacienteId', filtro.pacienteId);
    return this.http.get<Pagina<Prontuario>>(this.base, { params });
  }

  buscarPorId(id: number): Observable<ProntuarioDetalhe> {
    return this.http.get<ProntuarioDetalhe>(`${this.base}/${id}`);
  }

  criar(prontuario: ProntuarioRequest): Observable<ProntuarioDetalhe> {
    return this.http.post<ProntuarioDetalhe>(this.base, prontuario);
  }

  atualizar(id: number, prontuario: ProntuarioRequest): Observable<ProntuarioDetalhe> {
    return this.http.put<ProntuarioDetalhe>(`${this.base}/${id}`, prontuario);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
