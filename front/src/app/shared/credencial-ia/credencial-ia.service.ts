import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EscopoIaInput, FormIa, ResultadoTesteIa, StatusIa } from './credencial-ia.model';

/** CRUD (write-only) da credencial de IA (Claude) por unidade e do padrão do inquilino. */
@Injectable({ providedIn: 'root' })
export class CredencialIaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/configuracao-ia`;

  private url(escopo: EscopoIaInput, sufixo = ''): string {
    return escopo.tipo === 'unidade'
      ? `${this.base}/unidade/${escopo.unidadeId}${sufixo}`
      : `${this.base}/inquilino${sufixo}`;
  }

  status(escopo: EscopoIaInput): Observable<StatusIa> {
    return this.http.get<StatusIa>(this.url(escopo));
  }

  salvar(escopo: EscopoIaInput, form: FormIa): Observable<StatusIa> {
    return this.http.put<StatusIa>(this.url(escopo), form);
  }

  /** Testa a chave do FORMULÁRIO (em branco usa a salva) — valida via list de modelos (sem gastar tokens). */
  testar(escopo: EscopoIaInput, form: FormIa): Observable<ResultadoTesteIa> {
    return this.http.post<ResultadoTesteIa>(this.url(escopo, '/testar'), form);
  }

  /** Remove a credencial própria da unidade → volta a usar o padrão do inquilino. */
  remover(escopo: EscopoIaInput): Observable<StatusIa> {
    return this.http.delete<StatusIa>(this.url(escopo));
  }
}
