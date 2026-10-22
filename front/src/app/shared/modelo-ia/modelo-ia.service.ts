import { HttpClient, HttpParams } from '@angular/common/http';
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

  /** Só os modelos ativos — alimentam os selects da credencial. {@code provedor} filtra pelo provedor. */
  ativos(provedor?: string): Observable<ModeloIa[]> {
    const params = provedor ? new HttpParams().set('provedor', provedor) : undefined;
    return this.http.get<ModeloIa[]>(`${this.base}/ativos`, { params });
  }

  /** Todos os provedores de IA conhecidos (para o select de provedor no cadastro de modelo). */
  provedores(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/provedores`);
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
