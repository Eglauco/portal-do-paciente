import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina, Especialidade, EspecialidadeFiltro } from './especialidade.model';

@Injectable({ providedIn: 'root' })
export class EspecialidadeService {
  private readonly http = inject(HttpClient);

  readonly base = `${environment.apiUrl}/especialidade`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(filtro: EspecialidadeFiltro = {}, page = 0, size = EspecialidadeService.TAMANHO_PADRAO): Observable<Pagina<Especialidade>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    return this.http.get<Pagina<Especialidade>>(this.base, { params });
  }

  /** Exporta as especialidades dos filtros atuais em Excel ou PDF, só com as colunas escolhidas. */
  exportar(formato: 'xlsx' | 'pdf', filtro: EspecialidadeFiltro = {}, colunas: string[] = []): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    for (const c of colunas) params = params.append('colunas', c);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<Especialidade> {
    return this.http.get<Especialidade>(`${this.base}/${id}`);
  }

  criar(especialidade: Especialidade): Observable<Especialidade> {
    return this.http.post<Especialidade>(this.base, especialidade);
  }

  atualizar(id: number, especialidade: Especialidade): Observable<Especialidade> {
    return this.http.put<Especialidade>(`${this.base}/${id}`, especialidade);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
