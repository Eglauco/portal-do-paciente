import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Ordenacao, ordenacoesParaParametros } from '../../shared/ordenacao/ordenacao.model';
import {
  Pagina,
  Prontuario,
  ProntuarioDetalhe,
  ProntuarioFiltro,
  ProntuarioRequest,
} from './prontuario.model';

@Injectable({ providedIn: 'root' })
export class ProntuarioService {
  private readonly http = inject(HttpClient);
  readonly base = `${environment.apiUrl}/prontuario`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: ProntuarioFiltro = {},
    page = 0,
    size = ProntuarioService.TAMANHO_PADRAO,
    ordenacoes: readonly Ordenacao[] = [],
  ): Observable<Pagina<Prontuario>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.numero) params = params.set('numero', filtro.numero);
    if (filtro.pacienteId) params = params.set('pacienteId', filtro.pacienteId);
    if (filtro.unidadeId != null) params = params.set('unidadeId', filtro.unidadeId);
    if (filtro.especialidade?.trim()) params = params.set('especialidade', filtro.especialidade.trim());
    if (filtro.status) params = params.set('status', filtro.status);
    for (const o of ordenacoesParaParametros(ordenacoes)) params = params.append('ordenar', o);
    return this.http.get<Pagina<Prontuario>>(this.base, { params });
  }

  /** Exporta os prontuários dos filtros/ordenação atuais em Excel ou PDF, só com as colunas escolhidas. */
  exportar(
    formato: 'xlsx' | 'pdf',
    filtro: ProntuarioFiltro = {},
    colunas: string[] = [],
    ordenacoes: readonly Ordenacao[] = [],
  ): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.numero) params = params.set('numero', filtro.numero);
    if (filtro.pacienteId) params = params.set('pacienteId', filtro.pacienteId);
    if (filtro.unidadeId != null) params = params.set('unidadeId', filtro.unidadeId);
    if (filtro.especialidade?.trim()) params = params.set('especialidade', filtro.especialidade.trim());
    if (filtro.status) params = params.set('status', filtro.status);
    for (const c of colunas) params = params.append('colunas', c);
    for (const o of ordenacoesParaParametros(ordenacoes)) params = params.append('ordenar', o);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<ProntuarioDetalhe> {
    return this.http.get<ProntuarioDetalhe>(`${this.base}/${id}`);
  }

  criar(prontuario: ProntuarioRequest): Observable<ProntuarioDetalhe> {
    return this.http.post<ProntuarioDetalhe>(this.base, prontuario);
  }

  atualizar(id: number, prontuario: ProntuarioRequest): Observable<ProntuarioDetalhe> {
    return this.http.put<ProntuarioDetalhe>(`${this.base}/${id}`, prontuario);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /** Confirma o alerta de um documento (aguardando validação). Devolve o prontuário atualizado. */
  validarDocumento(documentoId: number): Observable<ProntuarioDetalhe> {
    return this.http.post<ProntuarioDetalhe>(`${this.base}/documento/${documentoId}/validar`, {});
  }

  /** Reprocessa a análise por IA de um documento (assíncrono, sem corpo de resposta). */
  reanalisarDocumento(documentoId: number): Observable<void> {
    return this.http.post<void>(`${this.base}/documento/${documentoId}/reanalisar`, {});
  }
}
