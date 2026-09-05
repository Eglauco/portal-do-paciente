import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina, Usuario, UsuarioFiltro, UsuarioRequest } from './usuario.model';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);

  // Base da API. Em produção viria de environment; aqui aponta para o backend local.
  readonly base = `${environment.apiUrl}/usuario`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(filtro: UsuarioFiltro = {}, page = 0, size = UsuarioService.TAMANHO_PADRAO): Observable<Pagina<Usuario>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    if (filtro.email?.trim()) params = params.set('email', filtro.email.trim());
    return this.http.get<Pagina<Usuario>>(this.base, { params });
  }

  /** Exporta os usuários dos filtros atuais em Excel ou PDF, só com as colunas escolhidas. */
  exportar(formato: 'xlsx' | 'pdf', filtro: UsuarioFiltro = {}, colunas: string[] = []): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    if (filtro.email?.trim()) params = params.set('email', filtro.email.trim());
    for (const c of colunas) params = params.append('colunas', c);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.base}/${id}`);
  }

  criar(usuario: UsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(this.base, usuario);
  }

  atualizar(id: number, usuario: UsuarioRequest): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.base}/${id}`, usuario);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
