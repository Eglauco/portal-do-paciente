import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Lembrete, LembreteRequest } from './lembrete.model';

@Injectable({ providedIn: 'root' })
export class LembreteService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/configuracao-agenda`;

  listar(configuracaoAgendaId: number): Observable<Lembrete[]> {
    return this.http.get<Lembrete[]>(`${this.base}/${configuracaoAgendaId}/lembretes`);
  }

  criar(configuracaoAgendaId: number, req: LembreteRequest): Observable<Lembrete> {
    return this.http.post<Lembrete>(`${this.base}/${configuracaoAgendaId}/lembretes`, req);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/lembretes/${id}`);
  }
}
