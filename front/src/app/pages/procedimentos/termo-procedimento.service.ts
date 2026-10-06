import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ModeloZapSign,
  ModeloZapSignDetalhe,
  TermoProcedimento,
  TermoProcedimentoRequest,
  VariavelTermo,
} from './termo-procedimento.model';

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

  /** Modelos prontos no ZapSign, para selecionar no cadastro do termo. */
  modelosZapSign(): Observable<ModeloZapSign[]> {
    return this.http.get<ModeloZapSign[]>(`${this.base}/zapsign/modelos`);
  }

  /** Detalhe de um modelo do ZapSign (variáveis + marcação de conhecidas). */
  detalharModeloZapSign(token: string): Observable<ModeloZapSignDetalhe> {
    return this.http.get<ModeloZapSignDetalhe>(`${this.base}/zapsign/modelos/${token}`);
  }
}
