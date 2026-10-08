import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ModeloZapSign,
  ModeloZapSignDetalhe,
  TermoConfiguracaoAgenda,
  TermoConfiguracaoAgendaRequest,
  VariavelTermo,
} from './termo-configuracao-agenda.model';

@Injectable({ providedIn: 'root' })
export class TermoConfiguracaoAgendaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/configuracao-agenda`;

  listar(configuracaoAgendaId: number): Observable<TermoConfiguracaoAgenda[]> {
    return this.http.get<TermoConfiguracaoAgenda[]>(`${this.base}/${configuracaoAgendaId}/termos`);
  }

  criar(configuracaoAgendaId: number, req: TermoConfiguracaoAgendaRequest): Observable<TermoConfiguracaoAgenda> {
    return this.http.post<TermoConfiguracaoAgenda>(`${this.base}/${configuracaoAgendaId}/termos`, req);
  }

  atualizar(id: number, req: TermoConfiguracaoAgendaRequest): Observable<TermoConfiguracaoAgenda> {
    return this.http.put<TermoConfiguracaoAgenda>(`${this.base}/termos/${id}`, req);
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
