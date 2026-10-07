import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Pagina,
  SirespConfig,
  SirespConfigSalvar,
  SirespDetalhe,
  SirespEnvioResultado,
  SirespFiltro,
  SirespImportResultado,
  SirespResumo,
} from './siresp.model';

/** Registros importados do SIRESP (só consulta + importação de XML). */
@Injectable({ providedIn: 'root' })
export class SirespService {
  private readonly http = inject(HttpClient);
  readonly base = `${environment.apiUrl}/siresp`;

  static readonly TAMANHOS = [10, 25, 50, 100];
  static readonly TAMANHO_PADRAO = 25;

  listar(
    filtro: SirespFiltro = {},
    unidadeId: number | null,
    page = 0,
    size = SirespService.TAMANHO_PADRAO,
  ): Observable<Pagina<SirespResumo>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (unidadeId != null) params = params.set('unidadeId', unidadeId);
    if (filtro.busca?.trim()) params = params.set('busca', filtro.busca.trim());
    if (filtro.status) params = params.set('status', filtro.status);
    if (filtro.statusEnvio) params = params.set('statusEnvio', filtro.statusEnvio);
    if (filtro.tipoMovimento) params = params.set('tipoMovimento', filtro.tipoMovimento);
    return this.http.get<Pagina<SirespResumo>>(this.base, { params });
  }

  detalhe(id: number): Observable<SirespDetalhe> {
    return this.http.get<SirespDetalhe>(`${this.base}/${id}`);
  }

  /**
   * Reprocessa o PROCESSAMENTO INTERNO: recalcula o diagnóstico e cria o agendamento quando tudo está resolvido.
   * Não envia nada ao Sistema de Gestão. Devolve o registro atualizado.
   */
  reprocessar(id: number): Observable<SirespDetalhe> {
    return this.http.post<SirespDetalhe>(`${this.base}/${id}/reprocessar`, {});
  }

  /** Reenvia o XML deste registro ao Sistema de Gestão (Post XML) e grava o resultado no log/status de envio. */
  enviar(id: number): Observable<SirespEnvioResultado> {
    return this.http.post<SirespEnvioResultado>(`${this.base}/${id}/enviar`, {});
  }

  /** Importa um arquivo XML do SIRESP (multipart). A unidade é resolvida no servidor pelo token. */
  importar(arquivo: File): Observable<SirespImportResultado> {
    const form = new FormData();
    form.append('arquivo', arquivo);
    return this.http.post<SirespImportResultado>(`${this.base}/importar`, form);
  }

  /** Config de atualização do paciente (liga/desliga + ação por campo). */
  lerConfig(): Observable<SirespConfig> {
    return this.http.get<SirespConfig>(`${this.base}/config`);
  }

  salvarConfig(req: SirespConfigSalvar): Observable<SirespConfig> {
    return this.http.put<SirespConfig>(`${this.base}/config`, req);
  }
}
