import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { Lembrete } from './lembrete.model';
import { LembreteService } from './lembrete.service';
import { ProcedimentoService } from './procedimento.service';

@Component({
  selector: 'app-procedimento-form',
  imports: [ReactiveFormsModule],
  templateUrl: './procedimento-form.html',
})
export class ProcedimentoForm implements PodeSair {
  private readonly service = inject(ProcedimentoService);
  private readonly lembreteService = inject(LembreteService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly form = new FormGroup({
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    preparo: new FormControl('', { nonNullable: true }),
    horasCancelamento: new FormControl<number | null>(24, {
      validators: [Validators.required, Validators.min(0)],
    }),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;

  // Lembretes (só ao editar um procedimento existente).
  protected readonly lembretes = signal<Lembrete[]>([]);
  protected readonly carregandoLembretes = signal(false);
  protected readonly salvandoLembrete = signal(false);
  protected readonly lembreteForm = new FormGroup({
    texto: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(300)] }),
    horasAntecedencia: new FormControl<number | null>(24, {
      validators: [Validators.required, Validators.min(1), Validators.max(8760)],
    }),
  });

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.editando.set(true);
      this.codigo.set(id);
      this.service.buscarPorId(id).subscribe({
        next: (procedimento) =>
          this.form.patchValue({
            nome: procedimento.nome,
            preparo: procedimento.preparo ?? '',
            horasCancelamento: procedimento.horasCancelamento ?? 24,
          }),
        error: () => this.erroCarregar.set(true),
      });
      this.carregarLembretes(id);
    }
  }

  private carregarLembretes(procedimentoId: number): void {
    this.carregandoLembretes.set(true);
    this.lembreteService.listar(procedimentoId).subscribe({
      next: (lembretes) => {
        this.lembretes.set(lembretes);
        this.carregandoLembretes.set(false);
      },
      error: () => this.carregandoLembretes.set(false),
    });
  }

  protected adicionarLembrete(): void {
    const id = this.codigo();
    if (id == null || this.lembreteForm.invalid || this.salvandoLembrete()) {
      this.lembreteForm.markAllAsTouched();
      return;
    }
    this.salvandoLembrete.set(true);
    const req = {
      texto: this.lembreteForm.controls.texto.value.trim(),
      horasAntecedencia: this.lembreteForm.controls.horasAntecedencia.value!,
    };
    this.lembreteService.criar(id, req).subscribe({
      next: (lembrete) => {
        this.lembretes.update((atual) => [lembrete, ...atual]);
        this.lembreteForm.reset({ texto: '', horasAntecedencia: 24 });
        this.salvandoLembrete.set(false);
        this.toastr.success('Lembrete adicionado');
      },
      error: () => {
        this.salvandoLembrete.set(false);
        this.toastr.error('Não foi possível adicionar o lembrete.');
      },
    });
  }

  protected async removerLembrete(lembrete: Lembrete): Promise<void> {
    const confirmado = await this.confirmar('Deseja excluir este lembrete?');
    if (!confirmado) return;
    this.lembreteService.excluir(lembrete.id).subscribe({
      next: () => {
        this.lembretes.update((atual) => atual.filter((l) => l.id !== lembrete.id));
        this.toastr.success('Lembrete excluído');
      },
      error: () => this.toastr.error('Não foi possível excluir o lembrete.'),
    });
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: 'nome' | 'horasCancelamento'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    const dados = {
      nome: this.form.controls.nome.value,
      preparo: this.form.controls.preparo.value.trim() || undefined,
      horasCancelamento: this.form.controls.horasCancelamento.value ?? 0,
    };
    const requisicao = this.editando()
      ? this.service.atualizar(this.codigo()!, dados)
      : this.service.criar(dados);
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Procedimento salvo');
        this.router.navigate(['/procedimentos']);
      },
      error: () => {
        this.salvando.set(false);
        this.toastr.error('Não foi possível salvar o procedimento.');
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir o procedimento?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Procedimento excluído');
        this.router.navigate(['/procedimentos']);
      },
      error: () => {
        this.excluindo.set(false);
        this.toastr.error('Não foi possível excluir o procedimento.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/procedimentos']);
  }

  private confirmar(mensagem: string): Promise<boolean> {
    this.confirmacao.set(mensagem);
    return new Promise<boolean>((resolve) => {
      this.resolverConfirmacao = resolve;
    });
  }

  protected responderConfirmacao(resposta: boolean): void {
    this.confirmacao.set(null);
    this.resolverConfirmacao?.(resposta);
    this.resolverConfirmacao = null;
  }
}
