import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ModeloIa } from './modelo-ia.model';

/** CRUD do cadastro de Modelos de IA (Claude) + preços. {@code ativos} alimenta os selects da credencial. */
@Injectable({ providedIn: 'root' })
export class ModeloIaService {
  private readonly http = inject(HttpClient);

  readonly base = `${environment.apiUrl}/modelo-ia`;

  /** Todos os modelos (cadastro), em ordem de nome. */
  listar(): Observable<ModeloIa[]> {
    return this.http.get<ModeloIa[]>(this.base);
  }

  /** Só os modelos ativos — usados para popular os selects de modelo no cadastro da credencial de IA. */
  ativos(): Observable<ModeloIa[]> {
    return this.http.get<ModeloIa[]>(`${this.base}/ativos`);
  }

  buscarPorId(id: number): Observable<ModeloIa> {
    return this.http.get<ModeloIa>(`${this.base}/${id}`);
  }

  criar(modelo: ModeloIa): Observable<ModeloIa> {
    return this.http.post<ModeloIa>(this.base, modelo);
  }

  atualizar(id: number, modelo: ModeloIa): Observable<ModeloIa> {
    return this.http.put<ModeloIa>(`${this.base}/${id}`, modelo);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
