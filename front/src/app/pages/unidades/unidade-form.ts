import { Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { Unidade, UnidadeFaqItem } from './unidade.model';
import { UnidadeService } from './unidade.service';

/** Grupo do formulário para um item de FAQ (pergunta + resposta). */
type FaqItemForm = FormGroup<{
  id: FormControl<number | null>;
  pergunta: FormControl<string>;
  resposta: FormControl<string>;
}>;

/** Abas do cadastro (dividem o formulário em seções). */
type AbaId = 'dados' | 'faq';

@Component({
  selector: 'app-unidade-form',
  imports: [ReactiveFormsModule],
  templateUrl: './unidade-form.html',
  styles: [`
    /* Abas do cadastro: separam Dados / FAQ (uma visível por vez). */
    .form-tabs {
      display: flex; flex-wrap: wrap; gap: 0.35rem;
      margin-bottom: 1.5rem; border-bottom: 1px solid var(--line);
    }
    .form-tab {
      position: relative; display: inline-flex; align-items: center; gap: 0.4rem;
      padding: 0.6rem 0.95rem; border: none; background: none; cursor: pointer;
      font-size: 0.9rem; font-weight: 600; color: var(--muted);
      border-bottom: 2px solid transparent; margin-bottom: -1px;
      border-radius: 0.4rem 0.4rem 0 0;
      transition: color 0.15s, background 0.15s, border-color 0.15s;
    }
    .form-tab:hover { color: var(--ink); background: color-mix(in srgb, var(--brand) 8%, transparent); }
    .form-tab--ativa { color: var(--brand-deep); border-bottom-color: var(--brand); }
    /* Ponto de alerta: a aba tem campos obrigatórios pendentes. */
    .form-tab__erro { width: 7px; height: 7px; border-radius: 50%; background: #b42318; flex: 0 0 auto; }
    @media (max-width: 640px) {
      .form-tabs { gap: 0; }
      .form-tab { flex: 1 1 auto; justify-content: center; padding: 0.55rem 0.5rem; font-size: 0.82rem; }
    }

    /* Lista editável de FAQ (pergunta + resposta). */
    .faq-lista { display: flex; flex-direction: column; gap: 0.75rem; }
    .faq-item {
      display: flex; gap: 0.6rem; align-items: flex-start;
      padding: 0.85rem; border: 1px solid var(--line); border-radius: 0.6rem;
      background: color-mix(in srgb, var(--brand) 3%, var(--surface));
    }
    .faq-item__campos { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 0.6rem; }
    .faq-item__campos .form-field { min-width: 0; }
    .faq-item__rm { flex: 0 0 auto; margin-top: 0.15rem; }
    .faq-add { margin-top: 0.85rem; align-self: flex-start; }
    .faq-vazio { margin: 0.25rem 0 0; font-size: 0.9rem; color: var(--muted); }
  `],
})
export class UnidadeForm implements PodeSair {
  private readonly service = inject(UnidadeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  /** Abas do formulário (na ordem exibida). */
  protected readonly abas: { id: AbaId; label: string }[] = [
    { id: 'dados', label: 'Dados' },
    { id: 'faq', label: 'FAQ da assistente virtual' },
  ];
  protected readonly abaAtiva = signal<AbaId>('dados');

  protected selecionarAba(id: AbaId): void {
    this.abaAtiva.set(id);
  }

  protected readonly form = new FormGroup({
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    faq: new FormArray<FaqItemForm>([]),
  });

  /** Controles (com validação) de cada aba — para sinalizar erro e pular até a aba certa. */
  private controlesDaAba(aba: AbaId): AbstractControl[] {
    switch (aba) {
      case 'dados':
        return [this.form.controls.nome];
      default:
        return []; // FAQ não tem campos obrigatórios (itens em branco são ignorados)
    }
  }

  /** A aba tem algum campo inválido já tocado/editado? (mostra o ponto de alerta na aba). */
  protected abaComErro(aba: AbaId): boolean {
    return this.controlesDaAba(aba).some((c) => c.invalid && (c.touched || c.dirty));
  }

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
        next: (unidade) => {
          this.form.patchValue({ nome: unidade.nome });
          this.setFaq(unidade.faq ?? []);
        },
        error: () => this.erroCarregar.set(true),
      });
    }
  }

  protected get faqItens(): FormArray<FaqItemForm> {
    return this.form.controls.faq;
  }

  /** Cria o grupo de um item de FAQ (novo = sem id). */
  private grupoFaq(item: UnidadeFaqItem = { pergunta: '', resposta: '' }): FaqItemForm {
    return new FormGroup({
      id: new FormControl<number | null>(item.id ?? null),
      pergunta: new FormControl(item.pergunta ?? '', { nonNullable: true }),
      resposta: new FormControl(item.resposta ?? '', { nonNullable: true }),
    });
  }

  protected adicionarFaq(): void {
    this.faqItens.push(this.grupoFaq());
    this.form.markAsDirty();
  }

  protected removerFaq(indice: number): void {
    this.faqItens.removeAt(indice);
    this.form.markAsDirty();
  }

  private setFaq(itens: UnidadeFaqItem[]): void {
    this.faqItens.clear();
    itens.forEach((i) => this.faqItens.push(this.grupoFaq(i)));
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: 'nome'): boolean {
    const control = this.form.controls[campo];
    return control.invalid && (control.touched || control.dirty);
  }

  private valores(): Unidade {
    return {
      nome: this.form.controls.nome.value.trim(),
      // Só envia itens completos; a ordem na lista é a posição enviada ao backend.
      faq: this.faqItens.controls
        .map((g) => ({
          pergunta: (g.controls.pergunta.value ?? '').trim(),
          resposta: (g.controls.resposta.value ?? '').trim(),
        }))
        .filter((f) => f.pergunta.length > 0 && f.resposta.length > 0),
    };
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      // Leva o usuário até a primeira aba que tem pendência (o erro pode estar numa aba oculta).
      const abaComPendencia = this.abas.find((a) => this.abaComErro(a.id));
      if (abaComPendencia) this.selecionarAba(abaComPendencia.id);
      return;
    }
    this.salvando.set(true);
    const requisicao = this.editando()
      ? this.service.atualizar(this.codigo()!, this.valores())
      : this.service.criar(this.valores());
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Unidade salva');
        this.router.navigate(['/unidades']);
      },
      error: () => {
        this.salvando.set(false);
        this.toastr.error('Não foi possível salvar a unidade.');
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir a unidade?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Unidade excluída');
        this.router.navigate(['/unidades']);
      },
      error: () => {
        this.excluindo.set(false);
        this.toastr.error('Não foi possível excluir a unidade.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/unidades']);
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
