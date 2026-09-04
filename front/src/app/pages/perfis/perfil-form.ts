import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { Unidade } from '../unidades/unidade.model';
import { UnidadeService } from '../unidades/unidade.service';
import { TelaOpcao } from './perfil.model';
import { PerfilService } from './perfil.service';

@Component({
  selector: 'app-perfil-form',
  imports: [ReactiveFormsModule, NgSelectModule],
  templateUrl: './perfil-form.html',
  styleUrl: './perfil-form.css',
})
export class PerfilForm implements PodeSair {
  private readonly service = inject(PerfilService);
  private readonly unidadeService = inject(UnidadeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly unidades = signal<Unidade[]>([]);
  protected readonly telasCatalogo = signal<TelaOpcao[]>([]);
  /** Telas marcadas (chaves do enum). */
  protected readonly telasSel = signal<Set<string>>(new Set());
  /** Marca alteração das telas (usado no guard de saída, além do form.dirty). */
  private readonly telasAlteradas = signal(false);
  protected readonly submetido = signal(false);

  protected readonly form = new FormGroup({
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    unidadeIds: new FormControl<number[]>([], {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly duplicando = signal(false);
  protected readonly erroCarregar = signal(false);

  /** Erro visível das telas só depois de tentar salvar sem nenhuma marcada. */
  protected readonly telasInvalido = computed(() => this.submetido() && this.telasSel().size === 0);

  // Diálogo de confirmação
  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editando.set(true);
      this.codigo.set(Number(idParam));
    }
    // Só carrega no navegador (evita chamadas sem token no SSR/prerender).
    afterNextRender(() => this.carregar());
  }

  private carregar(): void {
    this.service.telas().subscribe({
      next: (t) => this.telasCatalogo.set(t),
      error: () => this.erroCarregar.set(true),
    });
    this.unidadeService.listar({}, 0, 100).subscribe({
      next: (p) => this.unidades.set(p.content),
      error: () => this.erroCarregar.set(true),
    });
    if (this.editando() && this.codigo() != null) {
      this.service.buscarPorId(this.codigo()!).subscribe({
        next: (perfil) => {
          this.form.patchValue({
            nome: perfil.nome,
            unidadeIds: perfil.unidades.map((u) => u.id),
          });
          this.telasSel.set(new Set(perfil.telas));
        },
        error: () => this.erroCarregar.set(true),
      });
    }
  }

  protected telaMarcada(chave: string): boolean {
    return this.telasSel().has(chave);
  }

  protected alternarTela(chave: string): void {
    const proximo = new Set(this.telasSel());
    if (proximo.has(chave)) proximo.delete(chave);
    else proximo.add(chave);
    this.telasSel.set(proximo);
    this.telasAlteradas.set(true);
  }

  protected marcarTodasTelas(): void {
    this.telasSel.set(new Set(this.telasCatalogo().map((t) => t.chave)));
    this.telasAlteradas.set(true);
  }

  protected limparTelas(): void {
    this.telasSel.set(new Set());
    this.telasAlteradas.set(true);
  }

  protected invalido(campo: 'nome' | 'unidadeIds'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.touched || control.dirty || this.submetido());
  }

  /** Chamado pelo guard de rota antes de sair. */
  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || (!this.form.dirty && !this.telasAlteradas())) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    this.submetido.set(true);
    if (this.form.invalid || this.telasSel().size === 0) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    const dados = {
      nome: this.form.controls.nome.value.trim(),
      telas: [...this.telasSel()],
      unidadeIds: this.form.controls.unidadeIds.value,
    };
    const requisicao = this.editando()
      ? this.service.atualizar(this.codigo()!, dados)
      : this.service.criar(dados);
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Perfil salvo');
        this.router.navigate(['/perfis']);
      },
      error: () => {
        this.salvando.set(false);
        this.toastr.error('Não foi possível salvar o perfil.');
      },
    });
  }

  /** Duplica este perfil (cria "Cópia de …") e volta para a lista. */
  protected duplicar(): void {
    if (!this.editando() || this.codigo() == null || this.duplicando()) return;
    this.duplicando.set(true);
    this.service.duplicar(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Perfil duplicado');
        this.router.navigate(['/perfis']);
      },
      error: () => {
        this.duplicando.set(false);
        this.toastr.error('Não foi possível duplicar o perfil.');
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir o perfil?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Perfil excluído');
        this.router.navigate(['/perfis']);
      },
      error: () => {
        this.excluindo.set(false);
        this.toastr.error('Não foi possível excluir o perfil.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/perfis']);
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
