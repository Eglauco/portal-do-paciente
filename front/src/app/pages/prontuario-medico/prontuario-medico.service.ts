import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { HistoricoMedico } from './prontuario-medico.model';

/**
 * Tela "Prontuário Médico" (back-office): histórico do paciente em linha do tempo. O resumo do
 * histórico por IA é gerado AUTOMATICAMENTE no backend ao analisar documentos (sem chamada manual).
 */
@Injectable({ providedIn: 'root' })
export class ProntuarioMedicoService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/prontuario/medico`;

  /** Cabeçalho do paciente + resumo do histórico + linha do tempo dos atendimentos. */
  historico(pacienteId: number): Observable<HistoricoMedico> {
    return this.http.get<HistoricoMedico>(`${this.base}/${pacienteId}`);
  }
}
