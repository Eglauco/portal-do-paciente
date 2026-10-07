import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Ordenacao, ordenacoesParaParametros } from '../../shared/ordenacao/ordenacao.model';
import { Pagina, Exame, ExameFiltro } from './exame.model';

@Injectable({ providedIn: 'root' })
export class ExameService {
  private readonly http = inject(HttpClient);

  readonly base = `${environment.apiUrl}/exame`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: ExameFiltro = {},
    page = 0,
    size = ExameService.TAMANHO_PADRAO,
    ordenacoes: readonly Ordenacao[] = [],
  ): Observable<Pagina<Exame>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    for (const o of ordenacoesParaParametros(ordenacoes)) params = params.append('ordenar', o);
    return this.http.get<Pagina<Exame>>(this.base, { params });
  }

  /** Exporta os exames dos filtros/ordenação atuais em Excel ou PDF, só com as colunas escolhidas. */
  exportar(
    formato: 'xlsx' | 'pdf',
    filtro: ExameFiltro = {},
    colunas: string[] = [],
    ordenacoes: readonly Ordenacao[] = [],
  ): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    for (const c of colunas) params = params.append('colunas', c);
    for (const o of ordenacoesParaParametros(ordenacoes)) params = params.append('ordenar', o);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<Exame> {
    return this.http.get<Exame>(`${this.base}/${id}`);
  }

  criar(exame: Exame): Observable<Exame> {
    return this.http.post<Exame>(this.base, exame);
  }

  atualizar(id: number, exame: Exame): Observable<Exame> {
    return this.http.put<Exame>(`${this.base}/${id}`, exame);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
