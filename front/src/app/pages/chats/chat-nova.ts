import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { Subject, catchError, debounceTime, distinctUntilChanged, finalize, map, of, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { Paciente } from '../pacientes/paciente.model';
import { PacienteService } from '../pacientes/paciente.service';
import { ChatService } from './chat.service';

/**
 * Inicia uma conversa a partir do back-office. A unidade é sempre a ativa do
 * operador; escolhe-se apenas o paciente. Regras (aplicadas no backend):
 * - já existe conversa deste paciente na unidade → abre a existente (não duplica);
 * - paciente sem sessão no app (não está usando o celular) → 422, mostra aviso;
 * - caso contrário → cria a conversa e navega para ela.
 */
@Component({
  selector: 'app-chat-nova',
  imports: [ReactiveFormsModule, NgSelectModule],
  templateUrl: './chat-nova.html',
})
export class ChatNova {
  private readonly service = inject(ChatService);
  private readonly pacienteService = inject(PacienteService);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  protected readonly unidadeNome = this.auth.unidadeNome;
  protected readonly pacientes = signal<Paciente[]>([]);
  protected readonly carregandoPacientes = signal(false);
  protected readonly iniciando = signal(false);
  /** Mensagem clara na tela quando não é possível iniciar (ex.: paciente sem app). */
  protected readonly erro = signal<string | null>(null);

  /** Termo digitado no seletor — busca os pacientes no servidor. */
  protected readonly buscaPaciente$ = new Subject<string>();

  protected readonly form = new FormGroup({
    pacienteId: new FormControl<number | null>(null, { validators: [Validators.required] }),
  });

  constructor() {
    // Busca no servidor: a base de pacientes é global e pode passar de 100.
    // Filtrar no cliente sobre os "100 primeiros" esconderia justamente os
    // recém-cadastrados/ativados (ids maiores) — os que mais se quer contatar.
    this.buscaPaciente$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((termo) => {
          const t = (termo ?? '').trim();
          if (t.length < 2) {
            this.carregandoPacientes.set(false);
            return of<Paciente[]>([]);
          }
          this.carregandoPacientes.set(true);
          return this.pacienteService.listar({ nome: t }, 0, 20).pipe(
            map((p) => p.content),
            catchError(() => of<Paciente[]>([])),
            finalize(() => this.carregandoPacientes.set(false)),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((lista) => this.pacientes.set(lista));
  }

  protected invalido(): boolean {
    const c = this.form.controls.pacienteId;
    return c.invalid && (c.touched || c.dirty);
  }

  protected iniciar(event: Event): void {
    event.preventDefault();
    this.erro.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const unidadeId = this.auth.unidadeId();
    if (unidadeId == null) {
      this.erro.set('Selecione uma unidade ativa no topo antes de iniciar uma conversa.');
      return;
    }
    const pacienteId = this.form.controls.pacienteId.value!;
    this.iniciando.set(true);
    this.service.abrir(pacienteId, unidadeId).subscribe({
      next: (chat) => this.router.navigate(['/chats', chat.id]),
      error: (e: HttpErrorResponse) => {
        this.iniciando.set(false);
        if (e.status === 422) {
          this.erro.set(
            'Este paciente não está utilizando o aplicativo no celular. ' +
              'Não é possível iniciar uma conversa até que ele instale e ative o app.',
          );
        } else if (e.status === 404) {
          this.erro.set('Paciente ou unidade não encontrado. Atualize a página e tente novamente.');
        } else {
          this.erro.set('Não foi possível iniciar a conversa. Tente novamente.');
        }
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/chats']);
  }
}
