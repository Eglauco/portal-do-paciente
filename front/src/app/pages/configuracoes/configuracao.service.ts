import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Configuracao, ConfiguracaoFiltro, ConfiguracaoValor, Pagina } from './configuracao.model';

@Injectable({ providedIn: 'root' })
export class ConfiguracaoService {
  private readonly http = inject(HttpClient);
  readonly base = `${environment.apiUrl}/configuracao`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 10;

  listar(
    filtro: ConfiguracaoFiltro = {},
    page = 0,
    size = ConfiguracaoService.TAMANHO_PADRAO,
  ): Observable<Pagina<Configuracao>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtro.busca?.trim()) params = params.set('busca', filtro.busca.trim());
    if (filtro.tipo) params = params.set('tipo', filtro.tipo);
    return this.http.get<Pagina<Configuracao>>(this.base, { params });
  }

  buscarPorId(id: number): Observable<Configuracao> {
    return this.http.get<Configuracao>(`${this.base}/${id}`);
  }

  /** Atualiza SÓ o valor da configuração (o servidor aplica o campo do tipo). */
  atualizar(id: number, valor: ConfiguracaoValor): Observable<Configuracao> {
    return this.http.put<Configuracao>(`${this.base}/${id}`, valor);
  }
}
