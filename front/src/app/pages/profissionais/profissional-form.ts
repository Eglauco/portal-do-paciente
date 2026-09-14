import {
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { NgxMaskDirective } from 'ngx-mask';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { CepService } from '../../shared/cep.service';
import { TelefoneBrDirective } from '../../shared/telefone-br.directive';
import { ConselhoService } from '../conselhos/conselho.service';
import { Especialidade } from '../especialidades/especialidade.model';
import { EspecialidadeService } from '../especialidades/especialidade.service';
import { StorageService } from '../prontuarios/storage.service';
import { Unidade } from '../unidades/unidade.model';
import { UnidadeService } from '../unidades/unidade.service';
import { ProfissionalSaudeEntrada } from './profissional.model';
import { ProfissionalSaudeService } from './profissional.service';

const SEXOS = [
  { value: 'MASCULINO', label: 'Masculino' },
  { value: 'FEMININO', label: 'Feminino' },
  { value: 'OUTRO', label: 'Outro' },
  { value: 'NAO_INFORMADO', label: 'Prefiro não informar' },
];

const UFS = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS', 'MG', 'PA',
  'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
];

/** Opção de conselho para o select (id + rótulo "SIGLA — Nome"). */
interface ConselhoOpcao {
  id: number;
  label: string;
}

/** Data dd/mm/aaaa: opcional, mas se preenchida precisa ser válida e não futura. */
function dataNascimentoValidator(control: AbstractControl): ValidationErrors | null {
  const v = ((control.value ?? '') as string).trim();
  if (!v) return null;
  const m = v.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (!m) return { dataInvalida: true };
  const dia = +m[1];
  const mes = +m[2];
  const ano = +m[3];
  const data = new Date(ano, mes - 1, dia);
  const real = data.getFullYear() === ano && data.getMonth() === mes - 1 && data.getDate() === dia;
  if (!real || ano < 1900 || data.getTime() > Date.now()) return { dataInvalida: true };
  return null;
}

@Component({
  selector: 'app-profissional-form',
  imports: [ReactiveFormsModule, NgxMaskDirective, NgSelectModule, TelefoneBrDirective],
  templateUrl: './profissional-form.html',
  styles: [`
    .foto { display: flex; align-items: center; gap: 1rem; }
    .foto__preview {
      width: 5rem; height: 5rem; border-radius: 50%; object-fit: cover;
      border: 1px solid var(--line); background: #f7faf9; display: grid; place-items: center;
      color: var(--muted); font-weight: 700; font-size: 1.3rem; overflow: hidden;
    }
    .foto__preview img { width: 100%; height: 100%; object-fit: cover; }
    .foto__acoes { display: flex; gap: 0.5rem; flex-wrap: wrap; }
  `],
})
export class ProfissionalSaudeForm implements PodeSair {
  private readonly service = inject(ProfissionalSaudeService);
  private readonly cepService = inject(CepService);
  private readonly conselhoService = inject(ConselhoService);
  private readonly especialidadeService = inject(EspecialidadeService);
  private readonly unidadeService = inject(UnidadeService);
  private readonly storage = inject(StorageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly sexos = SEXOS;
  protected readonly ufs = UFS;

  protected readonly form = new FormGroup({
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    conselhoId: new FormControl<number | null>(null),
    numeroConselho: new FormControl('', { nonNullable: true }),
    codigoIntegracao: new FormControl('', { nonNullable: true }),
    sexo: new FormControl<string | null>(null),
    dataNascimento: new FormControl('', { nonNullable: true, validators: [dataNascimentoValidator] }),
    rg: new FormControl('', { nonNullable: true }),
    cpf: new FormControl('', { nonNullable: true }),
    cns: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    telefone: new FormControl('', { nonNullable: true }),
    telefonesAdicionais: new FormArray<FormControl<string>>([]),
    cep: new FormControl('', { nonNullable: true }),
    rua: new FormControl('', { nonNullable: true }),
    numero: new FormControl('', { nonNullable: true }),
    bairro: new FormControl('', { nonNullable: true }),
    municipio: new FormControl('', { nonNullable: true }),
    uf: new FormControl<string | null>(null),
    complemento: new FormControl('', { nonNullable: true }),
    especialidadeIds: new FormControl<number[]>([], { nonNullable: true }),
    unidadeIds: new FormControl<number[]>([], { nonNullable: true }),
  });

  protected readonly conselhos = signal<ConselhoOpcao[]>([]);
  protected readonly especialidades = signal<Especialidade[]>([]);
  protected readonly unidades = signal<Unidade[]>([]);

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);
  protected readonly buscandoCep = signal(false);

  /** Foto: URL crua (round-trip) + URL de exibição (assinada ou blob local). */
  private fotoRaw: string | null = null;
  protected readonly fotoPreview = signal<string | null>(null);
  protected readonly enviandoFoto = signal(false);

  /** Situação: inativo deixa o formulário somente leitura. */
  protected readonly ativo = signal(true);
  protected readonly inativo = computed(() => !this.ativo());
  protected readonly alterandoSituacao = signal(false);
  protected readonly nomeProfissional = signal('');

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  /** Botão "Não" (padrão seguro) do diálogo — recebe o foco ao abrir. */
  private readonly botaoNao = viewChild<ElementRef<HTMLButtonElement>>('botaoNao');
  /** Elemento que disparou o diálogo, para devolver o foco ao fechar. */
  private gatilhoConfirmacao: HTMLElement | null = null;
  private saidaAutorizada = false;
  private preenchendo = false;

  constructor() {
    this.form.controls.cep.valueChanges.pipe(takeUntilDestroyed()).subscribe((cep) => {
      if (this.preenchendo) return;
      if ((cep ?? '').replace(/\D/g, '').length === 8) this.buscarCep();
    });

    // Ao abrir o diálogo, move o foco para o botão "Não" (evita confirmar sem querer).
    effect(() => {
      const aberto = this.confirmacao() !== null;
      const botao = this.botaoNao();
      if (aberto && botao) botao.nativeElement.focus();
    });

    // Libera o object URL da prévia da foto ao destruir o componente (evita vazamento).
    inject(DestroyRef).onDestroy(() => this.revogarBlob());

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editando.set(true);
      this.codigo.set(Number(idParam));
    }
    afterNextRender(() => {
      this.carregarListas();
      if (this.editando() && this.codigo() != null) this.carregar(this.codigo()!);
    });
  }

  private carregarListas(): void {
    this.conselhoService.listar({}, 0, 100).subscribe({
      next: (p) => this.conselhos.set(p.content.map((c) => ({ id: c.id!, label: `${c.sigla} — ${c.nome}` }))),
      error: () => {},
    });
    this.especialidadeService.listar({}, 0, 100).subscribe({
      next: (p) => this.especialidades.set(p.content),
      error: () => {},
    });
    this.unidadeService.listar({}, 0, 100).subscribe({
      next: (p) => this.unidades.set(p.content),
      error: () => {},
    });
  }

  protected get telefonesAdicionais(): FormArray<FormControl<string>> {
    return this.form.controls.telefonesAdicionais;
  }

  protected adicionarTelefone(valor = ''): void {
    this.telefonesAdicionais.push(new FormControl(valor, { nonNullable: true }));
    this.form.markAsDirty();
  }

  protected removerTelefone(indice: number): void {
    this.telefonesAdicionais.removeAt(indice);
    this.form.markAsDirty();
  }

  private setTelefonesAdicionais(numeros: string[]): void {
    this.telefonesAdicionais.clear();
    numeros.forEach((n) => this.telefonesAdicionais.push(new FormControl(n, { nonNullable: true })));
  }

  private carregar(id: number): void {
    this.service.buscarPorId(id).subscribe({
      next: (p) => {
        this.preenchendo = true;
        this.form.patchValue({
          nome: p.nome,
          conselhoId: p.conselho?.id ?? null,
          numeroConselho: p.numeroConselho ?? '',
          codigoIntegracao: p.codigoIntegracao ?? '',
          sexo: p.sexo ?? null,
          dataNascimento: this.isoParaData(p.dataNascimento ?? null),
          rg: p.rg ?? '',
          cpf: p.cpf ?? '',
          cns: p.cns ?? '',
          email: p.email ?? '',
          telefone: p.telefone ?? '',
          cep: p.cep ?? '',
          rua: p.rua ?? '',
          numero: p.numero ?? '',
          bairro: p.bairro ?? '',
          municipio: p.municipio ?? '',
          uf: p.uf ?? null,
          complemento: p.complemento ?? '',
        });
        this.setTelefonesAdicionais(p.telefonesAdicionais ?? []);
        this.form.controls.especialidadeIds.setValue((p.especialidades ?? []).map((e) => e.id));
        this.form.controls.unidadeIds.setValue((p.unidades ?? []).map((u) => u.id));
        this.preenchendo = false;
        this.nomeProfissional.set(p.nome ?? '');
        this.aplicarSituacao(p.ativo !== false);
        this.fotoRaw = p.fotoUrl ?? null;
        if (this.fotoRaw) {
          this.storage.urlDownload(this.fotoRaw).then(
            (url) => this.fotoPreview.set(url),
            () => this.fotoPreview.set(null),
          );
        }
      },
      error: () => this.erroCarregar.set(true),
    });
  }

  /** Ativo = editável; inativo = somente leitura (form desabilitado). */
  private aplicarSituacao(ativo: boolean): void {
    this.ativo.set(ativo);
    if (ativo) {
      this.form.enable({ emitEvent: false });
    } else {
      this.form.disable({ emitEvent: false });
    }
  }

  // ---------- Foto ----------

  protected async aoSelecionarFoto(evento: Event): Promise<void> {
    const input = evento.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    input.value = ''; // permite reenviar o mesmo arquivo
    if (!arquivo) return;
    if (!arquivo.type.startsWith('image/')) {
      this.toastr.error('Selecione uma imagem.');
      return;
    }
    this.enviandoFoto.set(true);
    try {
      // Reduz para um quadrado 512×512 (recorte central) em JPEG antes de subir ao S3.
      const url = await this.storage.enviarImagem(arquivo, 'foto-profissional', {
        maxLado: 512,
        qualidade: 0.8,
      });
      this.fotoRaw = url;
      this.revogarBlob();
      this.fotoPreview.set(URL.createObjectURL(arquivo));
      this.form.markAsDirty();
    } catch {
      this.toastr.error('Não foi possível enviar a foto.');
    } finally {
      this.enviandoFoto.set(false);
    }
  }

  protected removerFoto(): void {
    this.fotoRaw = null;
    this.revogarBlob();
    this.fotoPreview.set(null);
    this.form.markAsDirty();
  }

  private revogarBlob(): void {
    const atual = this.fotoPreview();
    if (atual?.startsWith('blob:')) URL.revokeObjectURL(atual);
  }

  // ---------- CEP ----------

  private buscarCep(): void {
    this.buscandoCep.set(true);
    this.cepService.buscar(this.form.controls.cep.value).subscribe((end) => {
      this.buscandoCep.set(false);
      if (!end) return;
      this.form.patchValue({
        rua: end.logradouro || this.form.controls.rua.value,
        bairro: end.bairro || this.form.controls.bairro.value,
        municipio: end.municipio || this.form.controls.municipio.value,
        uf: end.uf || this.form.controls.uf.value,
      });
      this.form.markAsDirty();
    });
  }

  private isoParaData(iso: string | null): string {
    const m = (iso ?? '').match(/^(\d{4})-(\d{2})-(\d{2})/);
    return m ? `${m[3]}/${m[2]}/${m[1]}` : '';
  }

  private dataParaIso(valor: string): string | null {
    const m = (valor ?? '').trim().match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
    return m ? `${m[3]}-${m[2]}-${m[1]}` : null;
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: string): boolean {
    const control = this.form.get(campo);
    return !!control && control.invalid && (control.touched || control.dirty);
  }

  protected iniciais(nome: string | null | undefined): string {
    const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) return '—';
    const a = partes[0].charAt(0);
    const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (a + b).toUpperCase();
  }

  private valores(): ProfissionalSaudeEntrada {
    const f = this.form.getRawValue();
    const texto = (v: string) => (v.trim() ? v.trim() : null);
    return {
      nome: f.nome.trim(),
      conselhoId: f.conselhoId ?? null,
      numeroConselho: texto(f.numeroConselho),
      codigoIntegracao: texto(f.codigoIntegracao),
      sexo: f.sexo ?? null,
      dataNascimento: this.dataParaIso(f.dataNascimento),
      rg: texto(f.rg),
      cpf: texto(f.cpf),
      cns: texto(f.cns),
      email: texto(f.email),
      telefone: texto(f.telefone),
      telefonesAdicionais: this.telefonesAdicionais.controls
        .map((c) => (c.value ?? '').trim())
        .filter((v) => v.length > 0),
      cep: texto(f.cep),
      rua: texto(f.rua),
      numero: texto(f.numero),
      bairro: texto(f.bairro),
      municipio: texto(f.municipio),
      uf: f.uf ?? null,
      complemento: texto(f.complemento),
      fotoUrl: this.fotoRaw,
      especialidadeIds: f.especialidadeIds,
      unidadeIds: f.unidadeIds,
    };
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    const requisicao = this.editando()
      ? this.service.atualizar(this.codigo()!, this.valores())
      : this.service.criar(this.valores());
    requisicao.subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Profissional salvo');
        this.router.navigate(['/profissionais']);
      },
      error: (e) => {
        this.salvando.set(false);
        this.toastr.error(this.mensagemErro(e));
      },
    });
  }

  private mensagemErro(e: { status?: number; error?: { message?: string } }): string {
    if (e?.status === 409) return e.error?.message ?? 'Já existe um profissional com um dos dados únicos.';
    if (e?.status === 400) return e.error?.message ?? 'Verifique os dados informados (CPF/CNS inválido?).';
    return 'Não foi possível salvar o profissional.';
  }

  protected async inativar(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar(
      'Inativar este profissional? Ele fica somente leitura e sai das seleções (agendamento etc.). Você pode reativar depois.',
    );
    if (!confirmado) return;
    this.alterandoSituacao.set(true);
    this.service.inativar(this.codigo()!).subscribe({
      next: () => {
        this.alterandoSituacao.set(false);
        this.aplicarSituacao(false);
        this.toastr.success('Profissional inativado');
      },
      error: () => {
        this.alterandoSituacao.set(false);
        this.toastr.error('Não foi possível inativar o profissional.');
      },
    });
  }

  protected async reativar(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Reativar este profissional? Ele volta a aparecer nas seleções.');
    if (!confirmado) return;
    this.alterandoSituacao.set(true);
    this.service.reativar(this.codigo()!).subscribe({
      next: () => {
        this.alterandoSituacao.set(false);
        this.aplicarSituacao(true);
        this.toastr.success('Profissional reativado');
      },
      error: () => {
        this.alterandoSituacao.set(false);
        this.toastr.error('Não foi possível reativar o profissional.');
      },
    });
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir o profissional?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Profissional excluído');
        this.router.navigate(['/profissionais']);
      },
      error: (e) => {
        this.excluindo.set(false);
        // 409 = tem lançamento (agendamento/prontuário): não pode excluir, só inativar.
        this.toastr.error(
          e?.status === 409
            ? e.error?.message ?? 'Profissional com lançamentos. Inative-o em vez de excluir.'
            : 'Não foi possível excluir o profissional.',
        );
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/profissionais']);
  }

  private confirmar(mensagem: string): Promise<boolean> {
    this.gatilhoConfirmacao = document.activeElement as HTMLElement | null;
    this.confirmacao.set(mensagem);
    return new Promise<boolean>((resolve) => {
      this.resolverConfirmacao = resolve;
    });
  }

  protected responderConfirmacao(resposta: boolean): void {
    this.confirmacao.set(null);
    this.resolverConfirmacao?.(resposta);
    this.resolverConfirmacao = null;
    // Devolve o foco ao elemento que abriu o diálogo (após o fundo sair do inert).
    const gatilho = this.gatilhoConfirmacao;
    this.gatilhoConfirmacao = null;
    if (gatilho?.isConnected) queueMicrotask(() => gatilho.focus());
  }
}
