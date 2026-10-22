import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { ModeloIaService } from '../../shared/modelo-ia/modelo-ia.service';

/**
 * Cadastro de um Modelo de IA: provedor + id do modelo + nome + preço por MTok (entrada/saída) + ativo.
 * O id do modelo é único; o backend recusa duplicado (409) e devolve a mensagem para exibir.
 * O provedor amarra o modelo a uma plataforma (Anthropic/Claude e, no futuro, OpenAI/Gemini/Azure) e
 * filtra os modelos oferecidos no cadastro da credencial de IA.
 */
@Component({
  selector: 'app-modelo-ia-form',
  imports: [ReactiveFormsModule],
  templateUrl: './modelo-ia-form.html',
})
export class ModeloIaForm implements PodeSair {
  private readonly service = inject(ModeloIaService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  /** Provedores conhecidos (para o select). Vem do backend; cai em ANTHROPIC se falhar. */
  protected readonly provedores = signal<string[]>(['ANTHROPIC']);

  protected readonly form = new FormGroup({
    provedor: new FormControl('ANTHROPIC', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    modeloId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2)],
    }),
    precoEntradaUsdMtok: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0)],
    }),
    precoSaidaUsdMtok: new FormControl<number | null>(null, {
      validators: [Validators.required, Validators.min(0)],
    }),
    ativo: new FormControl(true, { nonNullable: true }),
  });

  protected readonly editando = signal(false);
  protected readonly id = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;

  constructor() {
    this.service.provedores().subscribe({
      next: (p) => this.provedores.set(p?.length ? p : ['ANTHROPIC']),
      error: () => this.provedores.set(['ANTHROPIC']),
    });
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.editando.set(true);
      this.id.set(id);
      this.service.buscarPorId(id).subscribe({
        next: (m) =>
          this.form.patchValue({
            provedor: m.provedor || 'ANTHROPIC',
            modeloId: m.modeloId,
            nome: m.nome,
            precoEntradaUsdMtok: m.precoEntradaUsdMtok,
            precoSaidaUsdMtok: m.precoSaidaUsdMtok,
            ativo: m.ativo,
          }),
        error: () => this.erroCarregar.set(true),
      });
    }
  }

  /** Nome amigável do provedor para o select. */
  protected rotuloProvedor(id: string): string {
    switch (id) {
      case 'ANTHROPIC': return 'Anthropic · Claude';
      case 'OPENAI': return 'OpenAI · GPT';
      case 'GOOGLE_GEMINI': return 'Google · Gemini';
      case 'AZURE_OPENAI': return 'Azure OpenAI';
      default: return id;
    }
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: 'modeloId' | 'nome' | 'precoEntradaUsdMtok' | 'precoSaidaUsdMtok'): boolean {
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
    const v = this.form.getRawValue();
    const dados = {
      provedor: v.provedor,
      modeloId: v.modeloId.trim(),
      nome: v.nome.trim(),
      precoEntradaUsdMtok: v.precoEntradaUsdMtok!,
      precoSaidaUsdMtok: v.precoSaidaUsdMtok!,
      ativo: v.ativo,
    };
    const requisicao = this.editando()
      ? this.service.atualizar(this.id()!, dados)
      : this.service.criar(dados);
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Modelo salvo');
        this.router.navigate(['/modelos-ia']);
      },
      error: (e) => {
        this.salvando.set(false);
        this.toastr.error(e?.error?.message ?? e?.error?.detail ?? 'Não foi possível salvar o modelo.');
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.id() == null) return;
    const confirmado = await this.confirmar('Deseja excluir o modelo?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.id()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Modelo excluído');
        this.router.navigate(['/modelos-ia']);
      },
      error: (e) => {
        this.excluindo.set(false);
        this.toastr.error(e?.error?.message ?? e?.error?.detail ?? 'Não foi possível excluir o modelo.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/modelos-ia']);
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
