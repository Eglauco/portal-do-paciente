import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Marca } from '../../core/marca.service';
import { PaletaTema } from '../../core/tema.service';
import { CriarInquilino, Inquilino } from './inquilino.model';
import { ConfigPlataforma, SalvarConfigPlataforma } from './plataforma-config.model';

interface UploadUrlResponse {
  uploadUrl: string;
  publicUrl: string;
}

/**
 * Console de super-admin (acessado "por fora" do sistema dos inquilinos). Não usa o token do admin:
 * cada chamada leva o segredo fixo no header {@code X-SuperAdmin-Secret} (o mesmo do backend). O
 * segredo fica só na memória do componente — nunca persistido.
 */
@Injectable({ providedIn: 'root' })
export class SuperadminService {
  private readonly http = inject(HttpClient);

  readonly base = `${environment.apiUrl}/superadmin/inquilinos`;
  private readonly basePlataforma = `${environment.apiUrl}/superadmin/plataforma`;

  listar(segredo: string): Observable<Inquilino[]> {
    return this.http.get<Inquilino[]>(this.base, { headers: this.headers(segredo) });
  }

  criar(segredo: string, inquilino: CriarInquilino): Observable<Inquilino> {
    return this.http.post<Inquilino>(this.base, inquilino, { headers: this.headers(segredo) });
  }

  // ---------- Identidade da plataforma ----------

  /** Marca SEMPRE da plataforma (pública, ignora inquilino) — logo/identidade do portão do super-admin. */
  marcaPlataforma(): Observable<Marca> {
    return this.http.get<Marca>(`${environment.apiUrl}/marca/plataforma`);
  }

  lerConfigPlataforma(segredo: string): Observable<ConfigPlataforma> {
    return this.http.get<ConfigPlataforma>(`${this.basePlataforma}/config`, { headers: this.headers(segredo) });
  }

  salvarConfigPlataforma(segredo: string, dados: SalvarConfigPlataforma): Observable<ConfigPlataforma> {
    return this.http.put<ConfigPlataforma>(`${this.basePlataforma}/config`, dados, { headers: this.headers(segredo) });
  }

  /** Prévia da paleta derivada de uma cor candidata (equivalente ao /tema/preview do admin). */
  temaPreview(segredo: string, cor: string): Observable<PaletaTema> {
    return this.http.get<PaletaTema>(`${this.basePlataforma}/tema-preview`, {
      headers: this.headers(segredo),
      params: new HttpParams().set('cor', cor),
    });
  }

  /**
   * Sobe a imagem direto ao S3 (sem passar pelo backend), sob o segredo do super-admin:
   * 1) pede a URL pré-assinada (PUT) a {@code /superadmin/plataforma/upload-url};
   * 2) faz o PUT do arquivo no S3; 3) devolve a URL pública (canônica) p/ salvar.
   */
  async enviarImagemPlataforma(segredo: string, arquivo: File): Promise<string> {
    const contentType = arquivo.type || 'application/octet-stream';
    const { uploadUrl, publicUrl } = await firstValueFrom(
      this.http.post<UploadUrlResponse>(
        `${this.basePlataforma}/upload-url`,
        { nomeArquivo: arquivo.name, contentType },
        { headers: this.headers(segredo) },
      ),
    );
    const resposta = await fetch(uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': contentType },
      body: arquivo,
    });
    if (!resposta.ok) {
      throw new Error(`Falha no upload para o S3 (${resposta.status})`);
    }
    return publicUrl;
  }

  private headers(segredo: string): HttpHeaders {
    return new HttpHeaders({ 'X-SuperAdmin-Secret': segredo });
  }
}
