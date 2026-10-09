import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { CriarInquilino, Inquilino } from './inquilino.model';
import { SuperadminService } from './superadmin.service';

/**
 * Console de super-admin: cadastro e pesquisa de inquilinos da plataforma. Fica FORA do sistema dos
 * inquilinos (sem login de admin, sem unidade) — a porta é um segredo fixo digitado aqui e enviado
 * no header de cada chamada. Enquanto o segredo não é validado (carregando a lista), só o portão
 * aparece; a lista e o cadastro ficam ocultos. Marca GENÉRICA (sem white-label do inquilino).
 */
@Component({
  selector: 'app-superadmin',
  imports: [ReactiveFormsModule],
  templateUrl: './superadmin.html',
  styleUrl: './superadmin.css',
})
export class Superadmin {
  private readonly service = inject(SuperadminService);
  private readonly toastr = inject(ToastrService);

  /** Segredo do super-admin (só na memória; enviado no header de cada chamada). */
  protected readonly segredo = signal('');
  /** Entrou no console (a lista carregou com o segredo correto). */
  protected readonly autenticado = signal(false);
  protected readonly carregando = signal(false);
  protected readonly erroAcesso = signal<string | null>(null);

  protected readonly inquilinos = signal<Inquilino[]>([]);
  /** Filtro cliente por nome ou schema (a lista de inquilinos é pequena). */
  protected readonly busca = signal('');
  protected readonly inquilinosFiltrados = computed(() => {
    const termo = this.busca().trim().toLowerCase();
    const lista = this.inquilinos();
    if (!termo) return lista;
    return lista.filter(
      (i) => i.nome.toLowerCase().includes(termo) || i.schemaName.toLowerCase().includes(termo),
    );
  });

  protected readonly mostrarForm = signal(false);
  protected readonly criando = signal(false);

  protected readonly form = new FormGroup({
    nome: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(2)] }),
    schemaName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[a-z_][a-z0-9_]{0,62}$/)],
    }),
    unidadeNome: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    adminNome: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    adminEmail: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email] }),
    adminSenha: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(6)] }),
  });

  protected atualizarSegredo(event: Event): void {
    this.segredo.set((event.target as HTMLInputElement).value);
  }

  protected atualizarBusca(event: Event): void {
    this.busca.set((event.target as HTMLInputElement).value);
  }

  /** Portão: valida o segredo CARREGANDO a lista. 401 → segredo errado. */
  protected entrar(event: Event): void {
    event.preventDefault();
    const segredo = this.segredo();
    if (!segredo) {
      this.erroAcesso.set('Informe a chave de super-admin.');
      return;
    }
    this.carregando.set(true);
    this.erroAcesso.set(null);
    this.service.listar(segredo).subscribe({
      next: (lista) => {
        this.inquilinos.set(lista);
        this.autenticado.set(true);
        this.carregando.set(false);
      },
      error: (e: HttpErrorResponse) => {
        this.carregando.set(false);
        this.erroAcesso.set(
          e.status === 401
            ? 'Chave de super-admin inválida.'
            : 'Não foi possível carregar os inquilinos. Verifique a conexão e tente novamente.',
        );
      },
    });
  }

  /** Recarrega a lista (reusa o segredo já validado). */
  protected recarregar(): void {
    const segredo = this.segredo();
    if (!segredo) return;
    this.carregando.set(true);
    this.service.listar(segredo).subscribe({
      next: (lista) => {
        this.inquilinos.set(lista);
        this.carregando.set(false);
      },
      error: () => {
        this.carregando.set(false);
        this.toastr.error('Não foi possível recarregar a lista.');
      },
    });
  }

  protected sair(): void {
    this.autenticado.set(false);
    this.inquilinos.set([]);
    this.segredo.set('');
    this.busca.set('');
    this.mostrarForm.set(false);
    this.form.reset();
  }

  protected abrirForm(): void {
    this.form.reset();
    this.mostrarForm.set(true);
  }

  protected fecharForm(): void {
    this.mostrarForm.set(false);
  }

  protected invalido(campo: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  protected criar(event: Event): void {
    event.preventDefault();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const dados: CriarInquilino = {
      nome: this.form.controls.nome.value.trim(),
      schemaName: this.form.controls.schemaName.value.trim(),
      unidadeNome: this.form.controls.unidadeNome.value.trim(),
      adminNome: this.form.controls.adminNome.value.trim(),
      adminEmail: this.form.controls.adminEmail.value.trim(),
      adminSenha: this.form.controls.adminSenha.value,
    };
    this.criando.set(true);
    this.service.criar(this.segredo(), dados).subscribe({
      next: (inquilino) => {
        this.inquilinos.update((lista) => [inquilino, ...lista]);
        this.criando.set(false);
        this.mostrarForm.set(false);
        this.form.reset();
        this.toastr.success(`Inquilino "${inquilino.nome}" provisionado.`);
      },
      error: (e: HttpErrorResponse) => {
        this.criando.set(false);
        this.toastr.error(this.mensagemErro(e));
      },
    });
  }

  /** Mensagem amigável a partir do erro HTTP (prefere a razão do backend quando vem no corpo). */
  private mensagemErro(e: HttpErrorResponse): string {
    const doBackend = typeof e.error?.message === 'string' ? (e.error.message as string) : null;
    if (doBackend) return doBackend;
    switch (e.status) {
      case 401:
        return 'Chave de super-admin inválida.';
      case 400:
        return 'Dados inválidos. Confira o nome do schema (minúsculo, começa por letra).';
      case 409:
        return 'Já existe um inquilino com esse schema ou um login com esse e-mail.';
      default:
        return 'Não foi possível provisionar o inquilino. Tente novamente.';
    }
  }
}
