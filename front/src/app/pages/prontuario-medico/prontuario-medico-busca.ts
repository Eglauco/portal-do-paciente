import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, finalize, map, of, switchMap } from 'rxjs';
import { Paciente, PacienteFiltro } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { idadeRotulo, iniciais, sexoRotulo } from './prontuario-medico.util';

/**
 * Tela 1 do Prontuário Médico: busca acolhedora do paciente por nome ou CPF, com resultados
 * em grade de cards. O card inteiro leva à linha do tempo do paciente. Nenhuma busca no
 * carregamento (SSR-safe): só busca quando o médico digita.
 */
@Component({
  selector: 'app-prontuario-medico-busca',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './prontuario-medico-busca.html',
})
export class ProntuarioMedicoBusca {
  private readonly pacienteService = inject(PacienteService);

  protected readonly busca = new FormControl('', { nonNullable: true });

  protected readonly pacientes = signal<Paciente[]>([]);
  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);
  /** Termo efetivamente pesquisado (>= 2 chars) — controla os estados vazios. */
  protected readonly termoAtivo = signal('');

  /** Antes de qualquer busca válida: mostra o estado inicial acolhedor. */
  protected readonly semTermo = computed(() => this.termoAtivo().length < 2);

  /** Placeholders para o skeleton de carregamento. */
  protected readonly skeletons = Array.from({ length: 8 });

  protected readonly iniciais = iniciais;
  protected readonly idadeRotulo = idadeRotulo;
  protected readonly sexoRotulo = sexoRotulo;

  constructor() {
    this.busca.valueChanges
      .pipe(
        map((t) => (t ?? '').trim()),
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((termo) => {
          if (termo.length < 2) {
            this.carregando.set(false);
            this.erro.set(false);
            this.termoAtivo.set(termo);
            this.pacientes.set([]);
            return of<Paciente[] | null>([]);
          }
          this.carregando.set(true);
          this.erro.set(false);
          this.termoAtivo.set(termo);
          return this.pacienteService.listar(this.montarFiltro(termo), 0, 24).pipe(
            map((p) => p.content),
            catchError(() => {
              this.erro.set(true);
              return of<Paciente[] | null>(null);
            }),
            finalize(() => this.carregando.set(false)),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((lista) => {
        if (lista) this.pacientes.set(lista);
      });
  }

  /** Se o termo parece um CPF (só dígitos/pontuação), busca por CPF; senão, por nome. */
  private montarFiltro(termo: string): PacienteFiltro {
    const soDigitos = termo.replace(/\D/g, '');
    const temLetra = /[a-zA-ZÀ-ÿ]/.test(termo);
    if (!temLetra && soDigitos.length >= 2) return { situacao: 'ATIVO', cpf: soDigitos };
    return { situacao: 'ATIVO', nome: termo };
  }
}
