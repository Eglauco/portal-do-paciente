import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina, Procedimento, ProcedimentoFiltro } from './procedimento.model';

@Injectable({ providedIn: 'root' })
export class ProcedimentoService {
  private readonly http = inject(HttpClient);

  readonly base = `${environment.apiUrl}/procedimento`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(filtro: ProcedimentoFiltro = {}, page = 0, size = ProcedimentoService.TAMANHO_PADRAO): Observable<Pagina<Procedimento>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    return this.http.get<Pagina<Procedimento>>(this.base, { params });
  }

  /** Exporta os procedimentos dos filtros atuais em Excel ou PDF, só com as colunas escolhidas. */
  exportar(formato: 'xlsx' | 'pdf', filtro: ProcedimentoFiltro = {}, colunas: string[] = []): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    for (const c of colunas) params = params.append('colunas', c);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<Procedimento> {
    return this.http.get<Procedimento>(`${this.base}/${id}`);
  }

  criar(procedimento: Procedimento): Observable<Procedimento> {
    return this.http.post<Procedimento>(this.base, procedimento);
  }

  atualizar(id: number, procedimento: Procedimento): Observable<Procedimento> {
    return this.http.put<Procedimento>(`${this.base}/${id}`, procedimento);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
