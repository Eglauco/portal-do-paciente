import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AgendamentoEntrega, AgendamentoLog } from '../agendamentos/agendamento.model';
import { Agenda, AgendaFiltro, AgendaRequest, AgendaResumo, Horario, HorarioRequest, Pagina } from './agenda.model';

/** Agendas (slots) e Horários (marcações dos pacientes). */
@Injectable({ providedIn: 'root' })
export class AgendaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/agenda`;
  private readonly baseHorario = `${environment.apiUrl}/horario`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: AgendaFiltro,
    unidadeId: number | null = null,
    page = 0,
    size = AgendaService.TAMANHO_PADRAO,
  ): Observable<Pagina<AgendaResumo>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (unidadeId != null) params = params.set('unidadeId', unidadeId);
    if (filtro.data) params = params.set('data', filtro.data);
    if (filtro.profissionalNome?.trim()) params = params.set('profissionalNome', filtro.profissionalNome.trim());
    if (filtro.especialidadeNome?.trim()) params = params.set('especialidadeNome', filtro.especialidadeNome.trim());
    return this.http.get<Pagina<AgendaResumo>>(this.base, { params });
  }

  buscarPorId(id: number): Observable<Agenda> {
    return this.http.get<Agenda>(`${this.base}/${id}`);
  }

  criar(agenda: AgendaRequest): Observable<Agenda> {
    return this.http.post<Agenda>(this.base, agenda);
  }

  atualizar(id: number, agenda: AgendaRequest): Observable<Agenda> {
    return this.http.put<Agenda>(`${this.base}/${id}`, agenda);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  // --- Horários (marcações) ---

  criarHorario(req: HorarioRequest): Observable<Horario> {
    return this.http.post<Horario>(this.baseHorario, req);
  }

  atualizarHorario(id: number, req: HorarioRequest): Observable<Horario> {
    return this.http.put<Horario>(`${this.baseHorario}/${id}`, req);
  }

  excluirHorario(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseHorario}/${id}`);
  }

  logsHorario(id: number): Observable<AgendamentoLog[]> {
    return this.http.get<AgendamentoLog[]>(`${this.baseHorario}/${id}/logs`);
  }

  entregaHorario(id: number): Observable<AgendamentoEntrega[]> {
    return this.http.get<AgendamentoEntrega[]>(`${this.baseHorario}/${id}/entrega`);
  }
}
