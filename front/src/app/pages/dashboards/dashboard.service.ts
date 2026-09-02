import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth.service';
import {
  AgendamentoDashboard,
  ChatDashboard,
  GeralDashboard,
  NpsDashboard,
  SauDashboard,
} from './dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = `${environment.apiUrl}/dashboard`;

  /** Parâmetros comuns: janela (dias) + unidade ativa do gestor. */
  private params(dias: number): HttpParams {
    let params = new HttpParams().set('dias', dias);
    const unidadeId = this.auth.unidadeId();
    if (unidadeId != null) params = params.set('unidadeId', unidadeId);
    return params;
  }

  geral(dias: number): Observable<GeralDashboard> {
    return this.http.get<GeralDashboard>(`${this.base}/geral`, { params: this.params(dias) });
  }

  agendamentos(dias: number): Observable<AgendamentoDashboard> {
    return this.http.get<AgendamentoDashboard>(`${this.base}/agendamentos`, { params: this.params(dias) });
  }

  chats(dias: number): Observable<ChatDashboard> {
    return this.http.get<ChatDashboard>(`${this.base}/chats`, { params: this.params(dias) });
  }

  sau(dias: number): Observable<SauDashboard> {
    return this.http.get<SauDashboard>(`${this.base}/sau`, { params: this.params(dias) });
  }

  nps(dias: number): Observable<NpsDashboard> {
    return this.http.get<NpsDashboard>(`${this.base}/nps`, { params: this.params(dias) });
  }
}
