import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Chat, ChatDetalhe, ChatFiltro, ChatLog, Pagina } from './chat.model';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/chat`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(filtro: ChatFiltro = {}, page = 0, size = ChatService.TAMANHO_PADRAO): Observable<Pagina<Chat>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.pacienteId) params = params.set('pacienteId', filtro.pacienteId);
    if (filtro.unidadeId) params = params.set('unidadeId', filtro.unidadeId);
    if (filtro.responsavelId) params = params.set('responsavelId', filtro.responsavelId);
    if (filtro.status) params = params.set('status', filtro.status);
    if (filtro.naoResolvidas) params = params.set('naoResolvidas', true);
    return this.http.get<Pagina<Chat>>(this.base, { params });
  }

  /** Exporta as conversas dos filtros atuais em Excel ou PDF (arquivo binário). */
  exportar(formato: 'xlsx' | 'pdf', filtro: ChatFiltro = {}): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.pacienteId) params = params.set('pacienteId', filtro.pacienteId);
    if (filtro.unidadeId) params = params.set('unidadeId', filtro.unidadeId);
    if (filtro.responsavelId) params = params.set('responsavelId', filtro.responsavelId);
    if (filtro.status) params = params.set('status', filtro.status);
    if (filtro.naoResolvidas) params = params.set('naoResolvidas', true);
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  detalhe(id: number): Observable<ChatDetalhe> {
    return this.http.get<ChatDetalhe>(`${this.base}/${id}`);
  }

  /**
   * Abre a conversa do paciente na unidade (1 por paciente+unidade): reutiliza
   * a existente ou cria uma nova. Erro 422 = paciente não está usando o app.
   */
  abrir(pacienteId: number, unidadeId: number): Observable<ChatDetalhe> {
    return this.http.post<ChatDetalhe>(this.base, { pacienteId, unidadeId });
  }

  visualizar(id: number): Observable<ChatDetalhe> {
    return this.http.post<ChatDetalhe>(`${this.base}/${id}/visualizar`, {});
  }

  /** Assume (ou transfere para si) a conversa: o atendente passa a ser o responsável. */
  assumir(id: number): Observable<ChatDetalhe> {
    return this.http.post<ChatDetalhe>(`${this.base}/${id}/assumir`, {});
  }

  /** Transfere a conversa para outro atendente (o usuário indicado vira o responsável). */
  transferir(id: number, usuarioId: number): Observable<ChatDetalhe> {
    return this.http.post<ChatDetalhe>(`${this.base}/${id}/transferir`, { usuarioId });
  }

  /** Linha do tempo de auditoria da conversa (ações dos atendentes). */
  logs(id: number): Observable<ChatLog[]> {
    return this.http.get<ChatLog[]>(`${this.base}/${id}/logs`);
  }

  enviar(id: number, texto: string, clienteId?: string): Observable<ChatDetalhe> {
    return this.http.post<ChatDetalhe>(`${this.base}/${id}/mensagem`, { texto, clienteId });
  }

  resolver(id: number): Observable<ChatDetalhe> {
    return this.http.post<ChatDetalhe>(`${this.base}/${id}/resolver`, {});
  }

  reabrir(id: number): Observable<ChatDetalhe> {
    return this.http.post<ChatDetalhe>(`${this.base}/${id}/reabrir`, {});
  }
}
