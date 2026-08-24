import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina, Unidade, UnidadeFiltro } from './unidade.model';

@Injectable({ providedIn: 'root' })
export class UnidadeService {
  private readonly http = inject(HttpClient);

  private readonly base = `${environment.apiUrl}/unidade`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(filtro: UnidadeFiltro = {}, page = 0, size = UnidadeService.TAMANHO_PADRAO): Observable<Pagina<Unidade>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    return this.http.get<Pagina<Unidade>>(this.base, { params });
  }

  buscarPorId(id: number): Observable<Unidade> {
    return this.http.get<Unidade>(`${this.base}/${id}`);
  }

  criar(unidade: Unidade): Observable<Unidade> {
    return this.http.post<Unidade>(this.base, unidade);
  }

  atualizar(id: number, unidade: Unidade): Observable<Unidade> {
    return this.http.put<Unidade>(`${this.base}/${id}`, unidade);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
