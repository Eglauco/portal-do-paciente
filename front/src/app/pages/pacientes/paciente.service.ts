import { environment } from '../../../environments/environment';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CodigoAtivacao, Pagina, Paciente, PacienteEntrada, PacienteFiltro } from './paciente.model';

@Injectable({ providedIn: 'root' })
export class PacienteService {
  private readonly http = inject(HttpClient);

  private readonly base = `${environment.apiUrl}/paciente`;

  /** Opções de registros por página (o backend limita a 100). */
  static readonly TAMANHOS = [10, 25, 50, 100];

  /** Quantidade padrão exibida ao abrir a tela. */
  static readonly TAMANHO_PADRAO = 10;

  listar(filtro: PacienteFiltro = {}, page = 0, size = PacienteService.TAMANHO_PADRAO): Observable<Pagina<Paciente>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    if (filtro.cpf?.trim()) params = params.set('cpf', filtro.cpf.trim());
    if (filtro.prontuario?.trim()) params = params.set('prontuario', filtro.prontuario.trim());
    return this.http.get<Pagina<Paciente>>(this.base, { params });
  }

  /** Exporta os pacientes dos filtros atuais em Excel ou PDF (arquivo binário). */
  exportar(formato: 'xlsx' | 'pdf', filtro: PacienteFiltro = {}): Observable<Blob> {
    let params = new HttpParams().set('formato', formato);
    if (filtro.codigo?.trim()) params = params.set('codigo', filtro.codigo.trim());
    if (filtro.nome?.trim()) params = params.set('nome', filtro.nome.trim());
    if (filtro.cpf?.trim()) params = params.set('cpf', filtro.cpf.trim());
    if (filtro.prontuario?.trim()) params = params.set('prontuario', filtro.prontuario.trim());
    return this.http.get(`${this.base}/exportar`, { params, responseType: 'blob' });
  }

  buscarPorId(id: number): Observable<Paciente> {
    return this.http.get<Paciente>(`${this.base}/${id}`);
  }

  criar(paciente: PacienteEntrada): Observable<Paciente> {
    return this.http.post<Paciente>(this.base, paciente);
  }

  atualizar(id: number, paciente: PacienteEntrada): Observable<Paciente> {
    return this.http.put<Paciente>(`${this.base}/${id}`, paciente);
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  /** Libera o paciente e gera um código de ativação (mostrado uma vez). */
  gerarCodigo(id: number): Observable<CodigoAtivacao> {
    return this.http.post<CodigoAtivacao>(`${this.base}/${id}/gerar-codigo`, {});
  }

  /** Revoga o acesso do paciente ao app. */
  revogarAcesso(id: number): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/revogar-acesso`, {});
  }
}
