import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { PaletaTema } from '../../core/tema.service';
import { CriarInquilino, Inquilino } from './inquilino.model';
import { SalvarConfigPlataforma } from './plataforma-config.model';
import { SuperadminService } from './superadmin.service';

/**
 * Console de super-admin: cadastro/pesquisa de inquilinos + identidade da plataforma (cor, logomarca,
 * imagem de fundo e frases do login — os defaults do /login e o fallback dos inquilinos). Fica FORA do
 * sistema dos inquilinos (sem login de admin): a porta é um segredo fixo digitado aqui e enviado no
 * header de cada chamada. Enquanto o segredo não é validado, só o portão aparece.
 */
@Component({
  selector: 'app-superadmin',
  imports: [ReactiveFormsModule],
  templateUrl: './superadmin.html',
  styleUrl: './superadmin.css',
})
export class Superadmin implements OnDestroy {
  private readonly service = inject(SuperadminService);
  private readonly toastr = inject(ToastrService);

  /** Logo da PLATAFORMA (de /marca/plataforma, sempre a da plataforma) para o quadrado de marca do portão. */
  protected readonly logoPlataforma = signal<string | null>(null);

  constructor() {
    // Busca a logo da plataforma no boot da tela (pública; ignora o inquilino mesmo se houver admin logado).
    this.service.marcaPlataforma().subscribe({
      next: (m) => this.logoPlataforma.set(m.logoUrl),
      error: () => {
        /* mantém o ícone padrão do portão */
      },
    });
  }

  /** A logo assinada da plataforma falhou/expirou ao carregar: descarta (cai no ícone padrão). */
  protected descartarLogoPlataforma(): void {
    this.logoPlataforma.set(null);
  }

  /** Segredo do super-admin (só na memória; enviado no header de cada chamada). */
  protected readonly segredo = signal('');
  /** Entrou no console (a lista carregou com o segredo correto). */
  protected readonly autenticado = signal(false);
  protected readonly carregando = signal(false);
  protected readonly erroAcesso = signal<string | null>(null);

  /** Aba ativa do console. */
  protected readonly aba = signal<'inquilinos' | 'plataforma'>('inquilinos');

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

  // ---------- Identidade da plataforma ----------

  protected readonly configCarregada = signal(false);
  protected readonly carregandoConfig = signal(false);
  protected readonly salvandoConfig = signal(false);
  /** Paleta derivada da cor escolhida (preview dos tons). */
  protected readonly previewCor = signal<PaletaTema | null>(null);
  /** URL p/ exibir a logo atual: assinada (salva) ou blob local (recém-enviada); null = sem logo. */
  protected readonly logoPreview = signal<string | null>(null);
  protected readonly fundoPreview = signal<string | null>(null);
  protected readonly enviandoLogo = signal(false);
  protected readonly enviandoFundo = signal(false);

  protected readonly formPlataforma = new FormGroup({
    corPrimaria: new FormControl('#0E8C7F', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^#[0-9a-fA-F]{6}$/)],
    }),
    nomePlataforma: new FormControl('', { nonNullable: true }),
    loginTitulo: new FormControl('', { nonNullable: true }),
    loginSubtitulo: new FormControl('', { nonNullable: true }),
    logoUrl: new FormControl<string | null>(null),
    loginFundoUrl: new FormControl<string | null>(null),
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
    this.aba.set('inquilinos');
    this.configCarregada.set(false);
    this.previewCor.set(null);
    this.revogarPreviewBlob(this.logoPreview());
    this.revogarPreviewBlob(this.fundoPreview());
    this.logoPreview.set(null);
    this.fundoPreview.set(null);
    this.formPlataforma.reset({ corPrimaria: '#0E8C7F' });
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

  // ---------- Identidade da plataforma ----------

  /** Troca de aba; carrega a identidade da plataforma sob demanda (uma vez). */
  protected irPara(aba: 'inquilinos' | 'plataforma'): void {
    this.aba.set(aba);
    if (aba === 'plataforma' && !this.configCarregada() && !this.carregandoConfig()) {
      this.carregarConfigPlataforma();
    }
  }

  private carregarConfigPlataforma(): void {
    const segredo = this.segredo();
    if (!segredo) return;
    this.carregandoConfig.set(true);
    this.service.lerConfigPlataforma(segredo).subscribe({
      next: (c) => {
        const cor = c.corPrimaria ?? '#0E8C7F';
        this.formPlataforma.reset({
          corPrimaria: cor,
          nomePlataforma: c.nomePlataforma ?? '',
          loginTitulo: c.loginTitulo ?? '',
          loginSubtitulo: c.loginSubtitulo ?? '',
          logoUrl: c.logoUrl ?? null,
          loginFundoUrl: c.loginFundoUrl ?? null,
        });
        this.revogarPreviewBlob(this.logoPreview());
        this.revogarPreviewBlob(this.fundoPreview());
        this.logoPreview.set(c.logoUrlVisualizacao ?? null);
        this.fundoPreview.set(c.loginFundoUrlVisualizacao ?? null);
        this.atualizarPreviewCor(cor);
        this.configCarregada.set(true);
        this.carregandoConfig.set(false);
      },
      error: () => {
        this.carregandoConfig.set(false);
        this.toastr.error('Não foi possível carregar a identidade da plataforma.');
      },
    });
  }

  private ultimaCorPreview = '';

  /** Busca a paleta derivada da cor (preview dos tons) — só reflete no sistema depois de salvar. */
  protected atualizarPreviewCor(cor: string): void {
    if (!/^#[0-9a-fA-F]{6}$/.test(cor)) return;
    this.formPlataforma.controls.corPrimaria.setValue(cor);
    this.ultimaCorPreview = cor;
    this.service.temaPreview(this.segredo(), cor).subscribe({
      next: (p) => {
        if (this.ultimaCorPreview === cor) this.previewCor.set(p);
      },
      error: () => {
        if (this.ultimaCorPreview === cor) this.previewCor.set(null);
      },
    });
  }

  protected aoMudarCor(event: Event): void {
    this.atualizarPreviewCor((event.target as HTMLInputElement).value);
  }

  protected async aoSelecionarLogo(event: Event): Promise<void> {
    await this.subirImagem(event, this.enviandoLogo, this.logoPreview, this.formPlataforma.controls.logoUrl);
  }

  protected async aoSelecionarFundo(event: Event): Promise<void> {
    await this.subirImagem(event, this.enviandoFundo, this.fundoPreview, this.formPlataforma.controls.loginFundoUrl);
  }

  /** Sobe a imagem escolhida ao S3 (sob o segredo) e guarda a URL canônica no formulário. */
  private async subirImagem(
    event: Event,
    enviando: ReturnType<typeof signal<boolean>>,
    preview: ReturnType<typeof signal<string | null>>,
    controle: FormControl<string | null>,
  ): Promise<void> {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0] ?? null;
    input.value = ''; // permite re-selecionar o mesmo arquivo
    if (!arquivo) return;
    if (!arquivo.type.startsWith('image/')) {
      this.toastr.error('Selecione um arquivo de imagem.');
      return;
    }
    enviando.set(true);
    try {
      const url = await this.service.enviarImagemPlataforma(this.segredo(), arquivo);
      controle.setValue(url);
      this.revogarPreviewBlob(preview());
      preview.set(URL.createObjectURL(arquivo)); // preview instantâneo (blob local)
    } catch {
      this.toastr.error('Não foi possível enviar a imagem.');
    } finally {
      enviando.set(false);
    }
  }

  protected removerLogo(): void {
    this.formPlataforma.controls.logoUrl.setValue(null);
    this.revogarPreviewBlob(this.logoPreview());
    this.logoPreview.set(null);
  }

  protected removerFundo(): void {
    this.formPlataforma.controls.loginFundoUrl.setValue(null);
    this.revogarPreviewBlob(this.fundoPreview());
    this.fundoPreview.set(null);
  }

  protected salvarConfigPlataforma(event: Event): void {
    event.preventDefault();
    if (this.formPlataforma.controls.corPrimaria.invalid) {
      this.toastr.error('Cor inválida. Use o formato #RRGGBB.');
      return;
    }
    const v = this.formPlataforma.getRawValue();
    const dados: SalvarConfigPlataforma = {
      corPrimaria: v.corPrimaria,
      nomePlataforma: v.nomePlataforma.trim() || null,
      loginTitulo: v.loginTitulo.trim() || null,
      loginSubtitulo: v.loginSubtitulo.trim() || null,
      logoUrl: v.logoUrl,
      loginFundoUrl: v.loginFundoUrl,
    };
    this.salvandoConfig.set(true);
    this.service.salvarConfigPlataforma(this.segredo(), dados).subscribe({
      next: (c) => {
        // Atualiza os previews com as URLs assinadas devolvidas (substitui os blobs locais).
        this.revogarPreviewBlob(this.logoPreview());
        this.revogarPreviewBlob(this.fundoPreview());
        this.logoPreview.set(c.logoUrlVisualizacao ?? null);
        this.fundoPreview.set(c.loginFundoUrlVisualizacao ?? null);
        this.formPlataforma.markAsPristine();
        this.salvandoConfig.set(false);
        this.toastr.success('Identidade da plataforma salva.');
      },
      error: (e: HttpErrorResponse) => {
        this.salvandoConfig.set(false);
        this.toastr.error(
          e.status === 422 ? 'Cor inválida. Use o formato #RRGGBB.' : 'Não foi possível salvar a identidade.',
        );
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

  /** Libera um blob local de preview (se for blob:) para não vazar memória. */
  private revogarPreviewBlob(url: string | null): void {
    if (url?.startsWith('blob:')) URL.revokeObjectURL(url);
  }

  ngOnDestroy(): void {
    this.revogarPreviewBlob(this.logoPreview());
    this.revogarPreviewBlob(this.fundoPreview());
  }
}
