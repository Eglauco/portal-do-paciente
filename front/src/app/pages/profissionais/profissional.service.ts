import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Ordenacao, ordenacoesParaParametros } from '../../shared/ordenacao/ordenacao.model';
import {
  Pagina,
  ProfissionalSaude,
  ProfissionalSaudeEntrada,
  ProfissionalSaudeFiltro,
} from './profissional.model';

@Injectable({ providedIn: 'root' })
export class ProfissionalSaudeService {
  private readonly http = inject(HttpClient);

  readonly base = `${environment.apiUrl}/profissional`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: ProfissionalSaudeFiltro = {},
    page = 0,
    size = ProfissionalSaudeService.TAMANHO_PADRAO,
    ordenacoes: readonly Ordenacao[] = [],
  ): Observable<Pagina<ProfissionalSaude>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    if (filtro.situacao) params = params.set('situacao', filtro.situacao);
    for (const o of ordenacoesParaParametros(ordenacoes)) params = params.append('ordenar', o);
    return this.http.get<Pagina<ProfissionalSaude>>(this.base, { params });
  }

  /** Exporta os profissionais dos filtros/ordenação atuais em Excel ou PDF, só com as colunas escolhidas. */
  exportar(
    formato: 'xlsx' | 'pdf',
    filtro: ProfissionalSaudeFiltro = {},
    colunas: string[] = [],
    ordenacoes: readonly Ordenacao[] = [],
  ): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    if (filtro.situacao) params = params.set('situacao', filtro.situacao);
    for (const c of colunas) params = params.append('colunas', c);
    for (const o of ordenacoesParaParametros(ordenacoes)) params = params.append('ordenar', o);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<ProfissionalSaude> {
    return this.http.get<ProfissionalSaude>(`${this.base}/${id}`);
  }

  criar(profissional: ProfissionalSaudeEntrada): Observable<ProfissionalSaude> {
    return this.http.post<ProfissionalSaude>(this.base, profissional);
  }

  atualizar(id: number, profissional: ProfissionalSaudeEntrada): Observable<ProfissionalSaude> {
    return this.http.put<ProfissionalSaude>(`${this.base}/${id}`, profissional);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /** Inativa o profissional (some das seleções; grava data-hora). */
  inativar(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/inativar`, {});
  }

  /** Reativa o profissional (volta a aparecer nas seleções). */
  reativar(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/reativar`, {});
  }
}
