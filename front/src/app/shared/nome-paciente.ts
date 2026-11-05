import { Component, input } from '@angular/core';

/** Forma mínima aceita pelo componente: nome de exibição + (opcional) nome de registro e flag de uso. */
export interface NomePacienteRef {
  /** Nome exibido (nome social quando o paciente usa; senão, o civil). */
  nome: string;
  /** Nome civil (de registro); revelado ao passar o mouse no marcador. */
  nomeRegistro?: string | null;
  /** O paciente usa nome social? (mostra o marcador). */
  temNomeSocial?: boolean;
}

/**
 * Mostra o nome do paciente (o nome social, quando ele usa) e, nesse caso, um marcador: ao passar o
 * mouse revela o NOME DE REGISTRO (civil) — informação que o administrador precisa. Usa o tooltip
 * NATIVO (atributo `title`), que o navegador desenha fora de qualquer container com overflow (ex.: o
 * painel do ng-select) — por isso nunca é cortado. O leitor de tela recebe a informação por um texto
 * visualmente oculto. Direito ao nome social: Decreto nº 8.727/2016; Portaria MS nº 1.820/2009.
 */
@Component({
  selector: 'app-nome-paciente',
  template: `
    @if (paciente(); as pac) {
      <span class="np">
        <span class="np__nome">{{ pac.nome }}</span>
        @if (pac.temNomeSocial && pac.nomeRegistro) {
          <span class="np__badge" [title]="'Nome de registro: ' + pac.nomeRegistro">
            <svg class="np__ico" viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="3" y="5" width="18" height="14" rx="2.5" />
              <circle cx="8.5" cy="11" r="1.8" />
              <path d="M13 10h4M13 14h4M5.6 15.4c.4-1.3 2.4-1.3 2.8 0" />
            </svg>
            <span class="np__sr">(usa nome social — nome de registro: {{ pac.nomeRegistro }})</span>
          </span>
        }
      </span>
    }
  `,
  styles: `
    .np { display: inline-flex; align-items: center; gap: 6px; max-width: 100%; }
    .np__nome { min-width: 0; overflow: hidden; text-overflow: ellipsis; }
    .np__badge {
      display: inline-flex; align-items: center; justify-content: center;
      width: 18px; height: 18px; flex: none; cursor: help;
      color: var(--muted, #64748b); line-height: 0;
    }
    .np__badge:hover { color: var(--brand, #0f766e); }
    .np__ico { width: 15px; height: 15px; }
    .np__sr {
      position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px;
      overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0;
    }
  `,
})
export class NomePaciente {
  /** Paciente a exibir (nome + nome de registro + flag). */
  readonly paciente = input<NomePacienteRef | null | undefined>();
}
