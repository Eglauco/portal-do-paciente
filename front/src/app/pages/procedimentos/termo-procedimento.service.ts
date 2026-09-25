import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { TermoProcedimento, TermoProcedimentoRequest, VariavelTermo } from './termo-procedimento.model';

@Injectable({ providedIn: 'root' })
export class TermoProcedimentoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/procedimento`;

  listar(procedimentoId: number): Observable<TermoProcedimento[]> {
    return this.http.get<TermoProcedimento[]>(`${this.base}/${procedimentoId}/termos`);
  }

  criar(procedimentoId: number, req: TermoProcedimentoRequest): Observable<TermoProcedimento> {
    return this.http.post<TermoProcedimento>(`${this.base}/${procedimentoId}/termos`, req);
  }

  atualizar(id: number, req: TermoProcedimentoRequest): Observable<TermoProcedimento> {
    return this.http.put<TermoProcedimento>(`${this.base}/termos/${id}`, req);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/termos/${id}`);
  }

  /** Catálogo das variáveis dinâmicas (fonte única no backend) para copiar no Word. */
  variaveis(): Observable<VariavelTermo[]> {
    return this.http.get<VariavelTermo[]>(`${this.base}/termos/variaveis`);
  }
}
