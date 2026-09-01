import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';

interface UploadUrlResponse {
  uploadUrl: string;
  publicUrl: string;
}

@Injectable({ providedIn: 'root' })
export class StorageService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/storage`;

  /**
   * Envia o arquivo direto para o S3 (sem passar pelo backend):
   * 1) pede ao backend uma URL pré-assinada (PUT);
   * 2) faz o PUT do arquivo direto no S3;
   * 3) retorna a URL pública para salvar no documento.
   *
   * {@code pasta}: subpasta no bucket (ex.: "rede-social"). Omitida → "prontuarios".
   */
  async enviar(arquivo: File, pasta?: string): Promise<string> {
    const contentType = arquivo.type || 'application/octet-stream';
    const { uploadUrl, publicUrl } = await firstValueFrom(
      this.http.post<UploadUrlResponse>(`${this.base}/upload-url`, {
        nomeArquivo: arquivo.name,
        contentType,
        pasta,
      }),
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

  /** Gera uma URL temporária (assinada) para visualizar/baixar o arquivo. */
  async urlDownload(url: string): Promise<string> {
    const params = new HttpParams().set('url', url);
    const r = await firstValueFrom(this.http.get<{ url: string }>(`${this.base}/download-url`, { params }));
    return r.url;
  }

  /** Exclui o arquivo no S3 a partir da sua URL. */
  async excluir(url: string): Promise<void> {
    const params = new HttpParams().set('url', url);
    await firstValueFrom(this.http.delete<void>(this.base, { params }));
  }
}
