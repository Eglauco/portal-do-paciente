import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Status (mascarado) de um provedor de assinatura — nunca traz o valor dos segredos, só se estão preenchidos. */
export interface ProvedorStatus {
  id: string; // ZAPSIGN | AUTENTIQUE | CLICKSIGN
  nome: string;
  ambiente: string; // SANDBOX | PRODUCAO — qual das URLs abaixo está ativa
  urlSandbox: string;
  urlProducao: string;
  tokenPreenchido: boolean;
  webhookPreenchido: boolean;
  disponivel: boolean;
}

export interface ProvedoresResponse {
  /** Provedor ativo (PROVEDOR_ASSINATURA) — pode ser um dos geridos ou DOCUSIGN. */
  ativo: string;
  provedores: ProvedorStatus[];
}

/** token/webhookSecret em branco MANTÊM o valor atual (não precisa redigitar o segredo); URLs em branco idem. */
export interface CredenciaisRequest {
  token?: string | null;
  webhookSecret?: string | null;
  ambiente: string;
  urlSandbox?: string | null;
  urlProducao?: string | null;
}

export interface ResultadoTeste {
  ok: boolean;
  mensagem: string;
}

@Injectable({ providedIn: 'root' })
export class ProvedoresAssinaturaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/provedores-assinatura`;

  listar(): Observable<ProvedoresResponse> {
    return this.http.get<ProvedoresResponse>(this.base);
  }

  salvar(id: string, req: CredenciaisRequest): Observable<ProvedoresResponse> {
    return this.http.put<ProvedoresResponse>(`${this.base}/${id}`, req);
  }

  testar(id: string): Observable<ResultadoTeste> {
    return this.http.post<ResultadoTeste>(`${this.base}/${id}/testar`, {});
  }

  definirAtivo(provedor: string): Observable<ProvedoresResponse> {
    return this.http.put<ProvedoresResponse>(`${this.base}/ativo`, { provedor });
  }
}
