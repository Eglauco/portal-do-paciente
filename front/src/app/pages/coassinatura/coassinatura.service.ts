import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** Um atendimento com termos aguardando a coassinatura do profissional logado. */
export interface TermoProfissional {
  prontuarioId: number;
  paciente: string | null;
  especialidade: string | null;
  dataHora: string;
  /** Link da cerimônia do profissional (aberto embutido no iframe, ou em aba se for certificado/DocuSign). */
  signUrl: string | null;
  /** true se a assinatura é com certificado digital (ICP) — abre em aba cheia (A3/leitora). */
  certificado: boolean;
  /** true se a cerimônia deve abrir em ABA nova em vez de iframe (ex.: DocuSign, cuja URL é de uso único). */
  abrirEmAba: boolean;
  /** Nomes dos termos desta cerimônia. */
  termos: string[];
}

/** Termo (após conferir) — usado para saber se ainda está aguardando. */
export interface TermoStatus {
  id: number;
  nome: string;
  status: string;
  statusDescricao: string;
  criadoEm: string;
}

@Injectable({ providedIn: 'root' })
export class CoassinaturaService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/coassinatura`;

  /** Atendimentos com termos aguardando a minha (profissional) assinatura. */
  meusTermos(): Observable<TermoProfissional[]> {
    return this.http.get<TermoProfissional[]>(`${this.base}/meus-termos`);
  }

  /** Confere no provedor se o documento concluiu (após eu assinar) e marca ASSINADO. */
  conferir(prontuarioId: number): Observable<TermoStatus[]> {
    return this.http.post<TermoStatus[]>(`${this.base}/${prontuarioId}/conferir`, {});
  }
}
