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

  /**
   * Redimensiona a imagem para um quadrado (recorte central) e envia como JPEG — reduz o
   * peso e padroniza avatares. Se o processamento falhar (navegador sem canvas, imagem
   * inválida), envia o arquivo original. Nunca amplia imagens menores que {@code maxLado}.
   */
  async enviarImagem(
    arquivo: File,
    pasta?: string,
    opcoes: { maxLado?: number; qualidade?: number } = {},
  ): Promise<string> {
    const maxLado = opcoes.maxLado ?? 512;
    const qualidade = opcoes.qualidade ?? 0.8;
    let paraEnviar = arquivo;
    try {
      const blob = await this.redimensionarQuadrado(arquivo, maxLado, qualidade);
      if (blob) {
        paraEnviar = new File([blob], this.trocarExtensao(arquivo.name, 'jpg'), { type: 'image/jpeg' });
      }
    } catch {
      // Falha no processamento: envia o original (não bloqueia o usuário).
      paraEnviar = arquivo;
    }
    return this.enviar(paraEnviar, pasta);
  }

  /** Desenha um recorte quadrado central da imagem num canvas de lado fixo e devolve um JPEG. */
  private async redimensionarQuadrado(arquivo: File, lado: number, qualidade: number): Promise<Blob | null> {
    if (typeof document === 'undefined' || !arquivo.type.startsWith('image/')) return null;
    const fonte = await this.carregarImagem(arquivo);
    try {
      const largura = 'naturalWidth' in fonte ? fonte.naturalWidth : fonte.width;
      const altura = 'naturalHeight' in fonte ? fonte.naturalHeight : fonte.height;
      if (!largura || !altura) return null;
      const origemLado = Math.min(largura, altura);
      const destLado = Math.min(lado, origemLado); // não amplia imagens menores
      const sx = (largura - origemLado) / 2;
      const sy = (altura - origemLado) / 2;

      const canvas = document.createElement('canvas');
      canvas.width = destLado;
      canvas.height = destLado;
      const ctx = canvas.getContext('2d');
      if (!ctx) return null;
      ctx.fillStyle = '#ffffff'; // transparência (PNG) vira branco no JPEG
      ctx.fillRect(0, 0, destLado, destLado);
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(fonte, sx, sy, origemLado, origemLado, 0, 0, destLado, destLado);

      return await new Promise<Blob | null>((resolve) =>
        canvas.toBlob((b) => resolve(b), 'image/jpeg', qualidade),
      );
    } finally {
      if ('close' in fonte && typeof fonte.close === 'function') fonte.close();
    }
  }

  /** Decodifica o arquivo respeitando a orientação EXIF; cai no HTMLImageElement se preciso. */
  private async carregarImagem(arquivo: File): Promise<ImageBitmap | HTMLImageElement> {
    if (typeof createImageBitmap === 'function') {
      try {
        return await createImageBitmap(arquivo, { imageOrientation: 'from-image' });
      } catch {
        // navegador sem suporte à opção: usa o HTMLImageElement
      }
    }
    return await new Promise<HTMLImageElement>((resolve, reject) => {
      const img = new Image();
      const url = URL.createObjectURL(arquivo);
      img.onload = () => {
        URL.revokeObjectURL(url);
        resolve(img);
      };
      img.onerror = () => {
        URL.revokeObjectURL(url);
        reject(new Error('Imagem inválida'));
      };
      img.src = url;
    });
  }

  /** Troca a extensão do nome do arquivo (ex.: "foto.png" → "foto.jpg"). */
  private trocarExtensao(nome: string, ext: string): string {
    const base = (nome || '').replace(/\.[^./\\]+$/, '');
    return `${base || 'foto'}.${ext}`;
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
