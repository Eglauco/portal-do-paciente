import { Component, input } from '@angular/core';

/**
 * Mostra como a imagem da postagem ficará no feed, na proporção 4:5.
 * A imagem já é recortada em 4:5 no upload; aqui apenas exibimos no quadro.
 */
@Component({
  selector: 'app-imagem-45',
  template: `
    <div class="img45">
      @if (src()) {
        <img class="img45__img" [src]="src()" alt="Pré-visualização da postagem em 4:5" />
      } @else {
        <div class="img45__vazio">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <rect x="3" y="3" width="18" height="18" rx="2" /><circle cx="9" cy="9" r="2" /><path d="m21 15-5-5L5 21" />
          </svg>
          <span>Prévia 4:5</span>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .img45 {
        aspect-ratio: 4 / 5;
        width: 100%;
        max-width: 260px;
        border-radius: 0.9rem;
        overflow: hidden;
        border: 1px solid var(--line);
        background: var(--bg);
      }
      .img45__img {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
      }
      .img45__vazio {
        width: 100%;
        height: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
        color: var(--muted);
        font-size: 0.85rem;
      }
      .img45__vazio svg {
        width: 2.2rem;
        height: 2.2rem;
        stroke: currentColor;
      }
    `,
  ],
})
export class Imagem45 {
  readonly src = input<string | null>(null);
}
