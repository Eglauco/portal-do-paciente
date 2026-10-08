import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Ordenacao, ordenacoesParaParametros } from '../../shared/ordenacao/ordenacao.model';
import { Pagina, ConfiguracaoAgenda, ConfiguracaoAgendaFiltro } from './configuracao-agenda.model';

@Injectable({ providedIn: 'root' })
export class ConfiguracaoAgendaService {
  private readonly http = inject(HttpClient);

  readonly base = `${environment.apiUrl}/configuracao-agenda`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: ConfiguracaoAgendaFiltro = {},
    page = 0,
    size = ConfiguracaoAgendaService.TAMANHO_PADRAO,
    ordenacoes: readonly Ordenacao[] = [],
  ): Observable<Pagina<ConfiguracaoAgenda>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    for (const o of ordenacoesParaParametros(ordenacoes)) params = params.append('ordenar', o);
    return this.http.get<Pagina<ConfiguracaoAgenda>>(this.base, { params });
  }

  /** Exporta os configuracaoAgendas dos filtros/ordenação atuais em Excel ou PDF, só com as colunas escolhidas. */
  exportar(
    formato: 'xlsx' | 'pdf',
    filtro: ConfiguracaoAgendaFiltro = {},
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

  buscarPorId(id: number): Observable<ConfiguracaoAgenda> {
    return this.http.get<ConfiguracaoAgenda>(`${this.base}/${id}`);
  }

  criar(configuracaoAgenda: ConfiguracaoAgenda): Observable<ConfiguracaoAgenda> {
    return this.http.post<ConfiguracaoAgenda>(this.base, configuracaoAgenda);
  }

  atualizar(id: number, configuracaoAgenda: ConfiguracaoAgenda): Observable<ConfiguracaoAgenda> {
    return this.http.put<ConfiguracaoAgenda>(`${this.base}/${id}`, configuracaoAgenda);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
