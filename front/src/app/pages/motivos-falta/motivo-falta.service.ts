import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { MotivoFalta, MotivoFaltaFiltro, Pagina } from './motivo-falta.model';

@Injectable({ providedIn: 'root' })
export class MotivoFaltaService {
  private readonly http = inject(HttpClient);

  private readonly base = `${environment.apiUrl}/motivo-falta`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: MotivoFaltaFiltro = {},
    page = 0,
    size = MotivoFaltaService.TAMANHO_PADRAO,
  ): Observable<Pagina<MotivoFalta>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.motivo?.trim()) params = params.set('motivo', filtro.motivo.trim());
    return this.http.get<Pagina<MotivoFalta>>(this.base, { params });
  }

  buscarPorId(id: number): Observable<MotivoFalta> {
    return this.http.get<MotivoFalta>(`${this.base}/${id}`);
  }

  criar(motivo: MotivoFalta): Observable<MotivoFalta> {
    return this.http.post<MotivoFalta>(this.base, motivo);
  }

  atualizar(id: number, motivo: MotivoFalta): Observable<MotivoFalta> {
    return this.http.put<MotivoFalta>(`${this.base}/${id}`, motivo);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
