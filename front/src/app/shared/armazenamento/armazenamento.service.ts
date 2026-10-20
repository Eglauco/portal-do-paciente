import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EscopoS3Input, FormS3, ResultadoTesteS3, StatusS3 } from './armazenamento.model';

/** CRUD (write-only) da config de S3 por unidade e do S3 padrão do inquilino. */
@Injectable({ providedIn: 'root' })
export class ArmazenamentoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/configuracao-s3`;

  private url(escopo: EscopoS3Input, sufixo = ''): string {
    return escopo.tipo === 'unidade'
      ? `${this.base}/unidade/${escopo.unidadeId}${sufixo}`
      : `${this.base}/inquilino${sufixo}`;
  }

  status(escopo: EscopoS3Input): Observable<StatusS3> {
    return this.http.get<StatusS3>(this.url(escopo));
  }

  salvar(escopo: EscopoS3Input, form: FormS3): Observable<StatusS3> {
    return this.http.put<StatusS3>(this.url(escopo), form);
  }

  /** Testa os valores do FORMULÁRIO (secret em branco usa a salva) — sem precisar salvar antes. */
  testar(escopo: EscopoS3Input, form: FormS3): Observable<ResultadoTesteS3> {
    return this.http.post<ResultadoTesteS3>(this.url(escopo, '/testar'), form);
  }
}
