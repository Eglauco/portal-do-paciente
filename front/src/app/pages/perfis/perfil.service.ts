import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Pagina, Perfil, PerfilFiltro, PerfilRequest, TelaOpcao } from './perfil.model';

@Injectable({ providedIn: 'root' })
export class PerfilService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/perfil`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  /** Lista os perfis de forma paginada, com filtros por código e nome. */
  listar(filtro: PerfilFiltro = {}, page = 0, size = PerfilService.TAMANHO_PADRAO): Observable<Pagina<Perfil>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    return this.http.get<Pagina<Perfil>>(this.base, { params });
  }

  /** Catálogo de telas disponíveis (chave + rótulo) para montar o formulário. */
  telas(): Observable<TelaOpcao[]> {
    return this.http.get<TelaOpcao[]>(`${this.base}/telas`);
  }

  buscarPorId(id: number): Observable<Perfil> {
    return this.http.get<Perfil>(`${this.base}/${id}`);
  }

  criar(perfil: PerfilRequest): Observable<Perfil> {
    return this.http.post<Perfil>(this.base, perfil);
  }

  atualizar(id: number, perfil: PerfilRequest): Observable<Perfil> {
    return this.http.put<Perfil>(`${this.base}/${id}`, perfil);
  }

  duplicar(id: number): Observable<Perfil> {
    return this.http.post<Perfil>(`${this.base}/${id}/duplicar`, {});
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
