import { Component, model } from '@angular/core';
import { PERIODOS } from '../dashboard.models';

/** Seletor de período (janela em dias). Two-way: [(dias)]. */
@Component({
  selector: 'dash-periodo',
  template: `
    <div class="periodo" role="group" aria-label="Período">
      @for (p of periodos; track p.dias) {
        <button
          type="button"
          class="periodo__btn"
          [class.is-active]="dias() === p.dias"
          [attr.aria-pressed]="dias() === p.dias"
          (click)="dias.set(p.dias)"
        >
          {{ p.label }}
        </button>
      }
    </div>
  `,
})
export class DashPeriodo {
  protected readonly periodos = PERIODOS;
  readonly dias = model(30);
}
