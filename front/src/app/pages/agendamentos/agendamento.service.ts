import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Ordenacao, ordenacoesParaParametros } from '../../shared/ordenacao/ordenacao.model';
import {
  Agendamento,
  AgendamentoEntrega,
  AgendamentoFiltro,
  AgendamentoLog,
  AgendamentoRequest,
  Pagina,
} from './agendamento.model';

@Injectable({ providedIn: 'root' })
export class AgendamentoService {
  private readonly http = inject(HttpClient);
  readonly base = `${environment.apiUrl}/agendamento`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: AgendamentoFiltro,
    unidadeId: number | null = null,
    page = 0,
    size = AgendamentoService.TAMANHO_PADRAO,
    ordenacoes: readonly Ordenacao[] = [],
  ): Observable<Pagina<Agendamento>> {
    let params = new HttpParams().set('page', page).set('size', size);
    params = this.aplicarFiltro(params, filtro, unidadeId);
    for (const o of ordenacoesParaParametros(ordenacoes)) params = params.append('ordenar', o);
    return this.http.get<Pagina<Agendamento>>(this.base, { params });
  }

  /** Exporta os agendamentos dos filtros atuais em Excel ou PDF, só com as colunas escolhidas. */
  exportar(
    formato: 'xlsx' | 'pdf',
    filtro: AgendamentoFiltro,
    unidadeId: number | null = null,
    colunas: string[] = [],
    ordenacoes: readonly Ordenacao[] = [],
  ): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    params = this.aplicarFiltro(params, filtro, unidadeId);
    for (const c of colunas) params = params.append('colunas', c);
    for (const o of ordenacoesParaParametros(ordenacoes)) params = params.append('ordenar', o);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  /** Anexa os filtros preenchidos (e a unidade ativa) aos parâmetros da requisição. */
  private aplicarFiltro(params: HttpParams, filtro: AgendamentoFiltro, unidadeId: number | null): HttpParams {
    if (filtro.status) params = params.set('status', filtro.status);
    if (filtro.nome && filtro.nome.trim()) params = params.set('nome', filtro.nome.trim());
    if (filtro.especialidadeNome && filtro.especialidadeNome.trim())
      params = params.set('especialidadeNome', filtro.especialidadeNome.trim());
    if (filtro.profissionalNome && filtro.profissionalNome.trim())
      params = params.set('profissionalNome', filtro.profissionalNome.trim());
    if (filtro.entregaResumo) params = params.set('entregaResumo', filtro.entregaResumo);
    if (filtro.data) params = params.set('data', filtro.data);
    if (unidadeId != null) params = params.set('unidadeId', unidadeId);
    return params;
  }

  buscarPorId(id: number): Observable<Agendamento> {
    return this.http.get<Agendamento>(`${this.base}/${id}`);
  }

  criar(agendamento: AgendamentoRequest): Observable<Agendamento> {
    return this.http.post<Agendamento>(this.base, agendamento);
  }

  atualizar(id: number, agendamento: AgendamentoRequest): Observable<Agendamento> {
    return this.http.put<Agendamento>(`${this.base}/${id}`, agendamento);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /** Linha do tempo das trocas de status do agendamento (quem fez cada mudança). */
  logs(id: number): Observable<AgendamentoLog[]> {
    return this.http.get<AgendamentoLog[]>(`${this.base}/${id}/logs`);
  }

  /** Destinatários da notificação e o estado de entrega de cada um (paciente + responsáveis). */
  entrega(id: number): Observable<AgendamentoEntrega[]> {
    return this.http.get<AgendamentoEntrega[]>(`${this.base}/${id}/entrega`);
  }
}
