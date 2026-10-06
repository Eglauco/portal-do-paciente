import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { firstValueFrom } from 'rxjs';
import { PodeSair } from '../../core/pending-changes.guard';
import { StorageService } from '../prontuarios/storage.service';
import { Lembrete } from './lembrete.model';
import { LembreteService } from './lembrete.service';
import { ProcedimentoService } from './procedimento.service';
import {
  ModeloZapSign,
  ModeloZapSignDetalhe,
  OrigemModeloTermo,
  TermoProcedimento,
  VariavelTermo,
} from './termo-procedimento.model';
import { TermoProcedimentoService } from './termo-procedimento.service';

/** Abas do procedimento (na edição): dados + lembretes + termos de consentimento (TCLE). */
type AbaId = 'dados' | 'lembretes' | 'tcle';

@Component({
  selector: 'app-procedimento-form',
  imports: [ReactiveFormsModule],
  templateUrl: './procedimento-form.html',
  styles: [`
    /* Abas do procedimento: separam Dados / Lembretes (uma visível por vez). */
    .form-tabs {
      display: flex; flex-wrap: wrap; gap: 0.35rem;
      margin-bottom: 1.25rem; border-bottom: 1px solid var(--line);
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
    @media (max-width: 640px) {
      .form-tabs { gap: 0; }
      .form-tab { flex: 1 1 auto; justify-content: center; padding: 0.55rem 0.5rem; font-size: 0.82rem; }
    }
    /* Aba TCLE: lista de documentos Word do procedimento. */
    .tcle-lista { list-style: none; margin: 0 0 1.1rem; padding: 0; display: flex; flex-direction: column; gap: 0.5rem; }
    .tcle-item {
      display: flex; align-items: center; gap: 0.75rem;
      padding: 0.7rem 0.85rem; border: 1px solid var(--line); border-radius: 0.6rem; background: var(--surface);
    }
    .tcle-item__info { flex: 1; min-width: 0; }
    .tcle-item__nome { font-weight: 600; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .tcle-item__meta { font-size: 0.78rem; color: var(--muted); margin-top: 0.1rem; }
    .tcle-item__acoes { display: flex; align-items: center; gap: 0.4rem; flex-shrink: 0; }
    .tcle-btn {
      display: inline-flex; align-items: center; gap: 0.3rem; margin: 0;
      padding: 0.4rem 0.6rem; border: 1px solid var(--line); border-radius: 0.5rem;
      background: none; cursor: pointer; font-size: 0.8rem; font-weight: 600; color: var(--brand-deep);
    }
    .tcle-btn:hover { background: color-mix(in srgb, var(--brand) 8%, transparent); }
    .tcle-btn--danger { color: #b23b4e; }
    .tcle-btn input[type=file] { display: none; }
    .tcle-vazio { color: var(--muted); font-size: 0.9rem; margin: 0 0 1.1rem; }
    .tcle-arquivo { font-size: 0.85rem; color: var(--muted); margin-top: 0.35rem; display: inline-block; }
    /* Modal de variáveis dinâmicas */
    .var-btn { display: inline-flex; align-items: center; gap: 0.4rem; }
    .var-btn svg { width: 1rem; height: 1rem; }
    .var-modal { max-width: 720px; width: 92vw; max-height: 85vh; display: flex; flex-direction: column; text-align: left; }
    .var-modal__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
    .var-modal__title { font-size: 1.05rem; font-weight: 700; color: var(--ink); margin: 0; }
    .var-modal__sub { font-size: 0.85rem; color: var(--muted); margin: 0.25rem 0 0.85rem; line-height: 1.4; }
    .var-modal__close { border: none; background: none; cursor: pointer; color: var(--muted); font-size: 1.5rem; line-height: 1; padding: 0 0.3rem; }
    .var-modal__body { overflow-y: auto; }
    .var-grupo { margin-bottom: 1rem; }
    .var-grupo__titulo { font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.05em; color: var(--brand-deep); margin: 0 0 0.4rem; }
    .var-lista { display: flex; flex-direction: column; gap: 0.35rem; }
    .var-item { display: flex; align-items: center; gap: 0.6rem; padding: 0.45rem 0.6rem; border: 1px solid var(--line); border-radius: 0.5rem; background: var(--surface); }
    .var-item__info { flex: 1; min-width: 0; }
    .var-item__token { font-family: ui-monospace, "Cascadia Code", monospace; font-size: 0.85rem; color: var(--ink); font-weight: 600; }
    .var-item__desc { font-size: 0.77rem; color: var(--muted); margin-top: 0.1rem; }
    .var-copiar { border: 1px solid var(--line); background: none; cursor: pointer; border-radius: 0.45rem; padding: 0.35rem 0.65rem; font-size: 0.78rem; font-weight: 600; color: var(--brand-deep); white-space: nowrap; }
    .var-copiar:hover { background: color-mix(in srgb, var(--brand) 8%, transparent); }
    .var-copiar--ok { color: #1e9e5a; border-color: #1e9e5a; }
    /* Adicionar termo: origem (arquivo x modelo ZapSign) + variáveis do modelo. */
    .tcle-add-titulo { font-size: 0.95rem; font-weight: 700; color: var(--ink); margin: 0.5rem 0 0.75rem; }
    .tcle-tag {
      display: inline-block; font-size: 0.68rem; font-weight: 700; text-transform: uppercase;
      letter-spacing: 0.04em; color: var(--brand-deep); background: color-mix(in srgb, var(--brand) 12%, transparent);
      border-radius: 0.35rem; padding: 0.05rem 0.4rem; margin-right: 0.4rem;
    }
    .origem-toggle { display: inline-flex; gap: 0.35rem; padding: 0.2rem; border: 1px solid var(--line); border-radius: 0.6rem; background: var(--surface); }
    .origem-opt {
      border: none; background: none; cursor: pointer; padding: 0.45rem 0.8rem; border-radius: 0.45rem;
      font-size: 0.85rem; font-weight: 600; color: var(--muted);
    }
    .origem-opt:hover { color: var(--ink); }
    .origem-opt--ativa { background: var(--brand); color: #fff; }
    .state--inline { flex-direction: row; gap: 0.5rem; padding: 0.6rem 0; justify-content: flex-start; }
    .state--inline p { margin: 0; font-size: 0.85rem; color: var(--muted); }
    .modelo-vars { margin-top: 0.75rem; padding: 0.75rem 0.85rem; border: 1px solid var(--line); border-radius: 0.6rem; background: var(--surface); }
    .modelo-vars__titulo { font-size: 0.78rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; color: var(--brand-deep); margin-bottom: 0.5rem; }
    .modelo-vars__chips { display: flex; flex-wrap: wrap; gap: 0.35rem; }
    .modelo-chip {
      font-family: ui-monospace, "Cascadia Code", monospace; font-size: 0.78rem; color: var(--ink);
      border: 1px solid var(--line); border-radius: 0.4rem; padding: 0.2rem 0.5rem; background: var(--bg);
    }
    .modelo-chip--alerta { color: #9a5b00; border-color: #e0a24a; background: #fff7e8; }
    .modelo-aviso { margin: 0.6rem 0 0; font-size: 0.82rem; color: #9a5b00; line-height: 1.45; }
    .check-linha { display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-size: 0.9rem; color: var(--ink); font-weight: 600; }
    .check-linha input { width: 1.05rem; height: 1.05rem; cursor: pointer; accent-color: var(--brand); }
    .check-linha--sub { margin-top: 0.6rem; margin-left: 1.55rem; font-weight: 600; }
  `],
})
export class ProcedimentoForm implements PodeSair {
  private readonly service = inject(ProcedimentoService);
  private readonly lembreteService = inject(LembreteService);
  private readonly termoService = inject(TermoProcedimentoService);
  private readonly storage = inject(StorageService);
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
    horasNps: new FormControl<number | null>(0, {
      validators: [Validators.required, Validators.min(0)],
    }),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);

  /** Aba ativa (só aparece na edição): dados do procedimento ou lembretes. */
  protected readonly abaAtiva = signal<AbaId>('dados');
  protected selecionarAba(id: AbaId): void {
    this.abaAtiva.set(id);
  }

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

  // Termos de Consentimento (TCLE) — só ao editar um procedimento existente.
  protected readonly termos = signal<TermoProcedimento[]>([]);
  protected readonly carregandoTermos = signal(false);
  protected readonly salvandoTermo = signal(false);
  protected readonly substituindoId = signal<number | null>(null);
  protected arquivoTermo: File | null = null;
  protected readonly nomeArquivoTermo = signal<string | null>(null);
  protected readonly termoNome = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(120)],
  });

  /**
   * Seleção de "Modelo do ZapSign" temporariamente OCULTA no front (backend + lógica prontos; será melhorada
   * e liberada depois). Com `false`, o seletor de origem some e a origem fica sempre ARQUIVO (upload do .docx,
   * comportamento atual). Para reativar a funcionalidade ao usuário, trocar para `true`.
   */
  protected readonly modeloZapsignHabilitado = false;

  /** Coassinatura: se o profissional de saúde do atendimento também assina o termo a adicionar. */
  protected readonly profissionalAssinaTermo = signal(false);
  /** Se o profissional assina com certificado digital (ICP) em vez de em tela (só com o check acima). */
  protected readonly profissionalCertificadoTermo = signal(false);

  // Origem do modelo do termo a adicionar: arquivo .docx (padrão) ou modelo pronto no ZapSign.
  protected readonly origemTermo = signal<OrigemModeloTermo>('ARQUIVO');
  protected readonly modelosZapsign = signal<ModeloZapSign[]>([]);
  protected readonly carregandoModelos = signal(false);
  protected readonly erroModelos = signal(false);
  protected readonly modeloSelecionadoToken = signal<string | null>(null);
  protected readonly modeloDetalhe = signal<ModeloZapSignDetalhe | null>(null);
  protected readonly carregandoDetalhe = signal(false);
  /** Variáveis que o modelo exige mas o POP não sabe preencher (aviso no cadastro). */
  protected readonly variaveisDesconhecidas = computed(
    () => this.modeloDetalhe()?.variaveis.filter((v) => !v.conhecida) ?? [],
  );

  // Variáveis dinâmicas (catálogo do backend) para o admin copiar no Word.
  protected readonly variaveis = signal<VariavelTermo[]>([]);
  protected readonly carregandoVariaveis = signal(false);
  protected readonly mostrarVariaveis = signal(false);
  protected readonly tokenCopiado = signal<string | null>(null);
  /** Variáveis agrupadas por "grupo" (Paciente, Atendimento…), na ordem em que chegam. */
  protected readonly variaveisPorGrupo = computed(() => {
    const grupos = new Map<string, VariavelTermo[]>();
    for (const v of this.variaveis()) {
      const lista = grupos.get(v.grupo);
      if (lista) lista.push(v);
      else grupos.set(v.grupo, [v]);
    }
    return Array.from(grupos, ([grupo, itens]) => ({ grupo, itens }));
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
            horasNps: procedimento.horasNps ?? 0,
          }),
        error: () => this.erroCarregar.set(true),
      });
      this.carregarLembretes(id);
      this.carregarTermos(id);
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

  private carregarTermos(procedimentoId: number): void {
    this.carregandoTermos.set(true);
    this.termoService.listar(procedimentoId).subscribe({
      next: (termos) => {
        this.termos.set(termos);
        this.carregandoTermos.set(false);
      },
      error: () => this.carregandoTermos.set(false),
    });
  }

  /** Aceita apenas Word (.docx / .doc). */
  private nomeArquivoValido(nome: string): boolean {
    return /\.docx?$/i.test(nome);
  }

  protected onArquivoTermo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0] ?? null;
    if (arquivo && !this.nomeArquivoValido(arquivo.name)) {
      this.toastr.error('Envie um arquivo Word (.docx ou .doc).');
      input.value = '';
      this.arquivoTermo = null;
      this.nomeArquivoTermo.set(null);
      return;
    }
    this.arquivoTermo = arquivo;
    this.nomeArquivoTermo.set(arquivo?.name ?? null);
    // Sugere o nome do termo a partir do arquivo, quando ainda vazio (limitado ao maxLength do nome).
    if (arquivo && !this.termoNome.value.trim()) {
      this.termoNome.setValue(arquivo.name.replace(/\.[^.]+$/, '').slice(0, 120));
    }
  }

  /** Troca a origem do termo a adicionar; ao ir para ZapSign, carrega os modelos (uma vez). */
  protected selecionarOrigem(origem: OrigemModeloTermo): void {
    this.origemTermo.set(origem);
    if (origem === 'ZAPSIGN_MODELO' && this.modelosZapsign().length === 0 && !this.carregandoModelos()) {
      this.carregarModelosZapsign();
    }
  }

  private carregarModelosZapsign(): void {
    this.carregandoModelos.set(true);
    this.erroModelos.set(false);
    this.termoService.modelosZapSign().subscribe({
      next: (modelos) => {
        this.modelosZapsign.set(modelos);
        this.carregandoModelos.set(false);
      },
      error: () => {
        this.erroModelos.set(true);
        this.carregandoModelos.set(false);
      },
    });
  }

  /** Ao escolher um modelo: guarda o token, sugere o nome e carrega as variáveis (para o aviso). */
  protected aoSelecionarModelo(event: Event): void {
    const token = (event.target as HTMLSelectElement).value || null;
    this.modeloSelecionadoToken.set(token);
    this.modeloDetalhe.set(null);
    if (!token) {
      this.carregandoDetalhe.set(false);
      return;
    }
    const modelo = this.modelosZapsign().find((m) => m.token === token);
    if (modelo && !this.termoNome.value.trim()) {
      this.termoNome.setValue(modelo.nome.slice(0, 120)); // respeita o maxLength(120) do nome
    }
    this.carregandoDetalhe.set(true);
    this.termoService.detalharModeloZapSign(token).subscribe({
      next: (detalhe) => {
        if (this.modeloSelecionadoToken() !== token) return; // seleção mudou: ignora resposta obsoleta
        this.modeloDetalhe.set(detalhe);
        this.carregandoDetalhe.set(false);
      },
      error: () => {
        if (this.modeloSelecionadoToken() !== token) return;
        this.carregandoDetalhe.set(false);
      },
    });
  }

  /** Liga/desliga a coassinatura; ao desligar, zera também a opção de certificado. */
  protected aoMarcarAssinaProfissional(marcado: boolean): void {
    this.profissionalAssinaTermo.set(marcado);
    if (!marcado) this.profissionalCertificadoTermo.set(false);
  }

  private resetarFormTermo(): void {
    this.termoNome.reset('');
    this.arquivoTermo = null;
    this.nomeArquivoTermo.set(null);
    this.modeloSelecionadoToken.set(null);
    this.modeloDetalhe.set(null);
    this.profissionalAssinaTermo.set(false);
    this.profissionalCertificadoTermo.set(false);
    // Volta à origem padrão: além de simplificar, remonta o <select> limpo (evita valor "fantasma").
    this.origemTermo.set('ARQUIVO');
  }

  protected async adicionarTermo(): Promise<void> {
    const id = this.codigo();
    if (id == null || this.salvandoTermo()) return;
    if (this.termoNome.invalid) {
      this.termoNome.markAsTouched();
      return;
    }

    // Origem "modelo do ZapSign": sem upload, envia só o token do modelo selecionado.
    if (this.origemTermo() === 'ZAPSIGN_MODELO') {
      const token = this.modeloSelecionadoToken();
      if (!token) {
        this.toastr.error('Selecione um modelo do ZapSign.');
        return;
      }
      const modelo = this.modelosZapsign().find((m) => m.token === token);
      this.salvandoTermo.set(true);
      try {
        const termo = await firstValueFrom(
          this.termoService.criar(id, {
            nome: this.termoNome.value.trim(),
            origemModelo: 'ZAPSIGN_MODELO',
            url: null,
            contentType: null,
            providerTemplateToken: token,
            modeloProviderNome: modelo?.nome ?? null,
            profissionalAssina: this.profissionalAssinaTermo(),
            profissionalCertificado: this.profissionalCertificadoTermo(),
          }),
        );
        this.termos.update((atual) => [termo, ...atual]);
        this.resetarFormTermo();
        this.toastr.success('Termo adicionado');
      } catch {
        this.toastr.error('Não foi possível adicionar o termo.');
      } finally {
        this.salvandoTermo.set(false);
      }
      return;
    }

    // Origem "arquivo Word": sobe o .docx e cadastra.
    if (!this.arquivoTermo) {
      this.toastr.error('Selecione o arquivo Word do termo.');
      return;
    }
    this.salvandoTermo.set(true);
    try {
      const url = await this.storage.enviar(this.arquivoTermo, 'tcle');
      const termo = await firstValueFrom(
        this.termoService.criar(id, {
          nome: this.termoNome.value.trim(),
          origemModelo: 'ARQUIVO',
          url,
          contentType: this.arquivoTermo.type || null,
          providerTemplateToken: null,
          modeloProviderNome: null,
          profissionalAssina: this.profissionalAssinaTermo(),
          profissionalCertificado: this.profissionalCertificadoTermo(),
        }),
      );
      this.termos.update((atual) => [termo, ...atual]);
      this.resetarFormTermo();
      this.toastr.success('Termo adicionado');
    } catch {
      this.toastr.error('Não foi possível adicionar o termo.');
    } finally {
      this.salvandoTermo.set(false);
    }
  }

  protected async substituirArquivo(termo: TermoProcedimento, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0] ?? null;
    input.value = '';
    if (!arquivo) return;
    if (!this.nomeArquivoValido(arquivo.name)) {
      this.toastr.error('Envie um arquivo Word (.docx ou .doc).');
      return;
    }
    this.substituindoId.set(termo.id);
    try {
      const url = await this.storage.enviar(arquivo, 'tcle');
      const atualizado = await firstValueFrom(
        this.termoService.atualizar(termo.id, {
          nome: termo.nome,
          origemModelo: 'ARQUIVO',
          url,
          contentType: arquivo.type || null,
          providerTemplateToken: null,
          modeloProviderNome: null,
          profissionalAssina: termo.profissionalAssina,
          profissionalCertificado: termo.profissionalCertificado,
        }),
      );
      this.termos.update((atual) => atual.map((t) => (t.id === termo.id ? atualizado : t)));
      this.toastr.success('Arquivo substituído');
    } catch {
      this.toastr.error('Não foi possível substituir o arquivo.');
    } finally {
      this.substituindoId.set(null);
    }
  }

  protected async removerTermo(termo: TermoProcedimento): Promise<void> {
    const confirmado = await this.confirmar('Deseja excluir este termo?');
    if (!confirmado) return;
    this.termoService.excluir(termo.id).subscribe({
      next: () => {
        this.termos.update((atual) => atual.filter((t) => t.id !== termo.id));
        this.toastr.success('Termo excluído');
      },
      error: () => this.toastr.error('Não foi possível excluir o termo.'),
    });
  }

  protected async baixarTermo(termo: TermoProcedimento): Promise<void> {
    if (!termo.url) return; // termo por modelo do ZapSign não tem arquivo nosso
    try {
      const url = await this.storage.urlDownload(termo.url);
      window.open(url, '_blank', 'noopener');
    } catch {
      this.toastr.error('Não foi possível abrir o arquivo.');
    }
  }

  protected abrirVariaveis(): void {
    this.mostrarVariaveis.set(true);
    if (this.variaveis().length === 0 && !this.carregandoVariaveis()) {
      this.carregandoVariaveis.set(true);
      this.termoService.variaveis().subscribe({
        next: (vs) => {
          this.variaveis.set(vs);
          this.carregandoVariaveis.set(false);
        },
        error: () => {
          this.carregandoVariaveis.set(false);
          this.toastr.error('Não foi possível carregar as variáveis.');
        },
      });
    }
  }

  protected fecharVariaveis(): void {
    this.mostrarVariaveis.set(false);
  }

  protected async copiarVariavel(token: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(token);
      this.tokenCopiado.set(token);
      setTimeout(() => {
        if (this.tokenCopiado() === token) this.tokenCopiado.set(null);
      }, 1500);
    } catch {
      this.toastr.error('Não foi possível copiar. Selecione e copie manualmente.');
    }
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: 'nome' | 'horasCancelamento' | 'horasNps'): boolean {
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
      horasNps: this.form.controls.horasNps.value ?? 0,
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
