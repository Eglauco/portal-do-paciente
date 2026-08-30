import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Chat, ChatDetalhe, ChatFiltro, Pagina } from './chat.model';

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
    if (filtro.status) params = params.set('status', filtro.status);
    if (filtro.naoResolvidas) params = params.set('naoResolvidas', true);
    return this.http.get<Pagina<Chat>>(this.base, { params });
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
