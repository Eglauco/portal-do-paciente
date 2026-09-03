import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CategoriaNps, CategoriaNpsFiltro, Pagina } from './categoria-nps.model';

@Injectable({ providedIn: 'root' })
export class CategoriaNpsService {
  private readonly http = inject(HttpClient);

  private readonly base = `${environment.apiUrl}/categoria-nps`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: CategoriaNpsFiltro = {},
    page = 0,
    size = CategoriaNpsService.TAMANHO_PADRAO,
  ): Observable<Pagina<CategoriaNps>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    return this.http.get<Pagina<CategoriaNps>>(this.base, { params });
  }

  /** Exporta as categorias dos filtros atuais em Excel ou PDF (arquivo binário). */
  exportar(formato: 'xlsx' | 'pdf', filtro: CategoriaNpsFiltro = {}): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<CategoriaNps> {
    return this.http.get<CategoriaNps>(`${this.base}/${id}`);
  }

  criar(categoria: CategoriaNps): Observable<CategoriaNps> {
    return this.http.post<CategoriaNps>(this.base, categoria);
  }

  atualizar(id: number, categoria: CategoriaNps): Observable<CategoriaNps> {
    return this.http.put<CategoriaNps>(`${this.base}/${id}`, categoria);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
