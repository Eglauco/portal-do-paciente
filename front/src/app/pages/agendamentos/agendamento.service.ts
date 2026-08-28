import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Agendamento, AgendamentoRequest, Pagina, StatusAgendamento } from './agendamento.model';

@Injectable({ providedIn: 'root' })
export class AgendamentoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/agendamento`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(
    status: StatusAgendamento | null,
    unidadeId: number | null = null,
    page = 0,
    size = AgendamentoService.TAMANHO_PADRAO,
  ): Observable<Pagina<Agendamento>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) params = params.set('status', status);
    if (unidadeId != null) params = params.set('unidadeId', unidadeId);
    return this.http.get<Pagina<Agendamento>>(this.base, { params });
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
}
