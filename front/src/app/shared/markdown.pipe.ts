import { Pipe, PipeTransform } from '@angular/core';
import { marked } from 'marked';

/**
 * Converte markdown (gerado pela IA) em HTML. Use com `[innerHTML]`, que passa pela sanitização
 * nativa do Angular (remove scripts/handlers, mantém títulos/negrito/listas/tabelas) — sem bypass.
 */
@Pipe({ name: 'markdown' })
export class MarkdownPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    if (!value || !value.trim()) {
      return '';
    }
    return marked.parse(value, { gfm: true, breaks: true }) as string;
  }
}
