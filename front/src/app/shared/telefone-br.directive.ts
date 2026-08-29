import { DestroyRef, Directive, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgControl } from '@angular/forms';

/** Formata um telefone brasileiro como "(00) 00000-0000" (aceita fixo com 10 dígitos). */
export function formatarTelefoneBr(valor: string | null | undefined): string {
  const d = (valor ?? '').replace(/\D/g, '').slice(0, 11);
  if (d.length === 0) return '';
  if (d.length <= 2) return `(${d}`;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

/**
 * Aplica a máscara de telefone a um controle de formulário reativo enquanto o
 * usuário digita e também quando o valor é definido pelo componente (ex.: ao
 * editar, o backend devolve só dígitos). O valor guardado no controle fica
 * mascarado; o backend normaliza para dígitos ao salvar.
 */
@Directive({ selector: '[appTelefoneBr]' })
export class TelefoneBrDirective implements OnInit {
  private readonly ngControl = inject(NgControl, { optional: true });
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    const control = this.ngControl?.control;
    if (!control) return;

    const inicial = formatarTelefoneBr(control.value);
    if (inicial !== control.value) {
      control.setValue(inicial, { emitEvent: false });
    }

    control.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((valor) => {
      const formatado = formatarTelefoneBr(valor);
      if (formatado !== valor) {
        control.setValue(formatado, { emitEvent: false });
      }
    });
  }
}
