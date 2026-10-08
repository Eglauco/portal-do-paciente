import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AgendaImportPreview, AgendaImportResultado } from './agenda-import.model';

/** Importação de Agenda por Excel — Fase 1 (baixar modelo + preview; sem gravar). */
@Injectable({ providedIn: 'root' })
export class AgendaImportService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/agenda-importacao`;

  /** Baixa a planilha de exemplo (.xlsx). */
  baixarModelo(): Observable<Blob> {
    return this.http.get(`${this.base}/modelo`, { responseType: 'blob' });
  }

  /**
   * Sobe a planilha preenchida (multipart) e recebe o preview validado. `unidadeId` é a unidade do usuário
   * logado — vira a unidade executante da agenda (não vai na planilha).
   */
  preview(arquivo: File, unidadeId: number | null): Observable<AgendaImportPreview> {
    const form = new FormData();
    form.append('arquivo', arquivo);
    let params = new HttpParams();
    if (unidadeId != null) params = params.set('unidadeId', unidadeId);
    return this.http.post<AgendaImportPreview>(`${this.base}/preview`, form, { params });
  }

  /** Confirma a importação (Fase 2): grava a agenda + horários e notifica os pacientes. Reenvia o arquivo. */
  confirmar(arquivo: File, unidadeId: number | null): Observable<AgendaImportResultado> {
    const form = new FormData();
    form.append('arquivo', arquivo);
    let params = new HttpParams();
    if (unidadeId != null) params = params.set('unidadeId', unidadeId);
    return this.http.post<AgendaImportResultado>(`${this.base}/confirmar`, form, { params });
  }
}
