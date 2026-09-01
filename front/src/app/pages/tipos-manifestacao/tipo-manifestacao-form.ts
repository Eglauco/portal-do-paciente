import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { TipoManifestacaoService } from './tipo-manifestacao.service';

@Component({
  selector: 'app-tipo-manifestacao-form',
  imports: [ReactiveFormsModule],
  templateUrl: './tipo-manifestacao-form.html',
})
export class TipoManifestacaoForm implements PodeSair {
  private readonly service = inject(TipoManifestacaoService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly form = new FormGroup({
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(2)],
    }),
    descricao: new FormControl<string>('', { nonNullable: true }),
    ativo: new FormControl(true, { nonNullable: true }),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      const id = Number(idParam);
      this.editando.set(true);
      this.codigo.set(id);
      this.service.buscarPorId(id).subscribe({
        next: (tipo) =>
          this.form.patchValue({ nome: tipo.nome, descricao: tipo.descricao ?? '', ativo: tipo.ativo }),
        error: () => this.erroCarregar.set(true),
      });
    }
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: 'nome'): boolean {
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
    const dados = this.form.getRawValue();
    const requisicao = this.editando()
      ? this.service.atualizar(this.codigo()!, dados)
      : this.service.criar(dados);
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Tipo salvo');
        this.router.navigate(['/tipos-manifestacao']);
      },
      error: () => {
        this.salvando.set(false);
        this.toastr.error('Não foi possível salvar o tipo.');
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir o tipo de manifestação?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Tipo excluído');
        this.router.navigate(['/tipos-manifestacao']);
      },
      error: (e) => {
        this.excluindo.set(false);
        if (e?.status === 409) {
          this.toastr.error('Existem manifestações usando este tipo. Desative-o em vez de excluir.');
        } else {
          this.toastr.error('Não foi possível excluir o tipo.');
        }
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/tipos-manifestacao']);
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
