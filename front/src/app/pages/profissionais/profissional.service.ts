import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Pagina, ProfissionalSaude, ProfissionalSaudeFiltro } from './profissional.model';

@Injectable({ providedIn: 'root' })
export class ProfissionalSaudeService {
  private readonly http = inject(HttpClient);

  private readonly base = `${environment.apiUrl}/profissional`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(filtro: ProfissionalSaudeFiltro = {}, page = 0, size = ProfissionalSaudeService.TAMANHO_PADRAO): Observable<Pagina<ProfissionalSaude>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    return this.http.get<Pagina<ProfissionalSaude>>(this.base, { params });
  }

  /** Exporta os profissionais dos filtros atuais em Excel ou PDF (arquivo binário). */
  exportar(formato: 'xlsx' | 'pdf', filtro: ProfissionalSaudeFiltro = {}): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<ProfissionalSaude> {
    return this.http.get<ProfissionalSaude>(`${this.base}/${id}`);
  }

  criar(profissional: ProfissionalSaude): Observable<ProfissionalSaude> {
    return this.http.post<ProfissionalSaude>(this.base, profissional);
  }

  atualizar(id: number, profissional: ProfissionalSaude): Observable<ProfissionalSaude> {
    return this.http.put<ProfissionalSaude>(`${this.base}/${id}`, profissional);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
