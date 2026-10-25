import { Pipe, PipeTransform, inject } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Destaca as ocorrências de `termo` no texto, envolvendo cada uma num `<mark class="wa__marca">`.
 * Escapa TODO o texto primeiro (XSS-safe) e só então injeta o `<mark>`; por isso o
 * `bypassSecurityTrustHtml` é seguro. Pipe PURO (padrão): memoiza por (texto, termo), então não
 * re-sanitiza/re-pinta a cada ciclo de detecção — só quando o texto ou o termo mudam.
 */
@Pipe({ name: 'destacarBusca' })
export class DestacarBuscaPipe implements PipeTransform {
  private readonly sanitizer = inject(DomSanitizer);

  transform(texto: string | null | undefined, termo: string | null | undefined): SafeHtml {
    const t = texto ?? '';
    const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const termoNorm = (termo ?? '').trim().toLowerCase();
    if (!termoNorm) return this.sanitizer.bypassSecurityTrustHtml(esc(t));
    const alvo = t.toLowerCase();
    let pos = alvo.indexOf(termoNorm);
    if (pos < 0) return this.sanitizer.bypassSecurityTrustHtml(esc(t));
    let i = 0;
    let out = '';
    while (pos >= 0) {
      out += esc(t.slice(i, pos)) + '<mark class="wa__marca">' + esc(t.slice(pos, pos + termoNorm.length)) + '</mark>';
      i = pos + termoNorm.length;
      pos = alvo.indexOf(termoNorm, i);
    }
    out += esc(t.slice(i));
    return this.sanitizer.bypassSecurityTrustHtml(out);
  }
}
