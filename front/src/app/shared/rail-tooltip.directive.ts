import { Directive, DestroyRef, ElementRef, inject, input } from '@angular/core';

/**
 * Tooltip do menu recolhido: ao passar o mouse (ou focar) num item só-ícone, mostra o
 * nome da funcionalidade num balão à direita. Usa posição `fixed` (via JS) para escapar
 * do recorte horizontal da sidebar rolável. Só age quando `appRailTip` é true (recolhido).
 *
 * O nome acessível do item continua vindo do texto (`.nav__label`, mantido como sr-only),
 * então o balão é apenas reforço visual (aria-hidden) para quem enxerga.
 */
@Directive({
  selector: '[appRailTip]',
  host: {
    '(mouseenter)': 'mostrar()',
    '(focus)': 'mostrar()',
    '(mouseleave)': 'esconder()',
    '(blur)': 'esconder()',
    '(keydown.escape)': 'esconder()',
  },
})
export class RailTooltip {
  /** Ativo apenas com o menu recolhido. */
  readonly ativo = input.required<boolean>({ alias: 'appRailTip' });

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private balao: HTMLElement | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.esconder());
  }

  protected mostrar(): void {
    if (!this.ativo() || this.balao || typeof document === 'undefined') return;
    const el = this.host.nativeElement;
    const texto = (el.querySelector('.nav__label')?.textContent ?? el.textContent ?? '').trim();
    if (!texto) return;

    const rect = el.getBoundingClientRect();
    const balao = document.createElement('div');
    balao.className = 'rail-tip';
    balao.setAttribute('role', 'tooltip');
    balao.setAttribute('aria-hidden', 'true');
    balao.textContent = texto;
    balao.style.top = `${rect.top + rect.height / 2}px`;
    balao.style.left = `${rect.right + 10}px`;
    document.body.appendChild(balao);
    this.balao = balao;
  }

  protected esconder(): void {
    this.balao?.remove();
    this.balao = null;
  }
}
