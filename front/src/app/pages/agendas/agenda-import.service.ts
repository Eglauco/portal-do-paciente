import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AgendaImportPreview } from './agenda-import.model';

/** Importação de Agenda por Excel — Fase 1 (baixar modelo + preview; sem gravar). */
@Injectable({ providedIn: 'root' })
export class AgendaImportService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/agenda-importacao`;

  /** Baixa a planilha de exemplo (.xlsx). */
  baixarModelo(): Observable<Blob> {
    return this.http.get(`${this.base}/modelo`, { responseType: 'blob' });
  }

  /** Sobe a planilha preenchida (multipart) e recebe o preview validado. */
  preview(arquivo: File): Observable<AgendaImportPreview> {
    const form = new FormData();
    form.append('arquivo', arquivo);
    return this.http.post<AgendaImportPreview>(`${this.base}/preview`, form);
  }
}
