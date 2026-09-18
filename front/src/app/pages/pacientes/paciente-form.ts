import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
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
import { FuncionalidadeAppService } from '../../core/funcionalidade-app.service';
import { PodeSair } from '../../core/pending-changes.guard';
import { CepService } from '../../shared/cep.service';
import { TelefoneBrDirective } from '../../shared/telefone-br.directive';
import { Unidade } from '../unidades/unidade.model';
import { UnidadeService } from '../unidades/unidade.service';
import {
  FUNCIONALIDADES_APP,
  FuncionalidadeApp,
  NIVEIS_ACESSO,
  NIVEIS_SEM_LANCAMENTO,
  NivelAcesso,
  PacienteEntrada,
  PermissoesResponsavel,
  Responsavel,
  SituacaoCadastro,
} from './paciente.model';
import { PacienteLogModal } from './paciente-log-modal';
import { PacienteService } from './paciente.service';

/** Sub-grupo com o nível de acesso do responsável por funcionalidade do app. */
type PermissoesForm = FormGroup<Record<FuncionalidadeApp, FormControl<NivelAcesso>>>;

/** Grupo do formulário para um responsável (cadastro paralelo). */
type ResponsavelForm = FormGroup<{
  id: FormControl<number | null>;
  nome: FormControl<string>;
  /** CPF (só dígitos); obrigatório. */
  cpf: FormControl<string>;
  telefone: FormControl<string>;
  /** Data de nascimento (DD/MM/AAAA); valida a idade mínima p/ comentar na rede social. */
  dataNascimento: FormControl<string>;
  permissoes: PermissoesForm;
  /** Situação (soft-delete): inativo perde acesso ao perfil no app. */
  ativo: FormControl<boolean>;
  /** Metadado (não editável): tem lançamentos → não pode remover, só inativar. */
  temLancamentos: FormControl<boolean>;
}>;

/** Abas do cadastro (dividem o formulário longo em seções). */
type AbaId = 'pessoais' | 'contato' | 'endereco' | 'responsaveis' | 'unidades';

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

/** Exige ao menos um telefone preenchido na lista (strings vazias/whitespace não contam). */
function aoMenosUmTelefoneValidator(control: AbstractControl): ValidationErrors | null {
  const array = control as FormArray<FormControl<string>>;
  const algumPreenchido = array.controls.some((c) => ((c.value ?? '') as string).trim().length > 0);
  return algumPreenchido ? null : { telefoneObrigatorio: true };
}

/** Data no formato dd/mm/aaaa: opcional, mas se preenchida precisa ser válida e não futura. */
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
  selector: 'app-paciente-form',
  imports: [ReactiveFormsModule, NgxMaskDirective, NgSelectModule, TelefoneBrDirective, PacienteLogModal],
  templateUrl: './paciente-form.html',
  styleUrl: './paciente-form.css',
})
export class PacienteForm implements PodeSair {
  private readonly service = inject(PacienteService);
  private readonly funcionalidadeAppService = inject(FuncionalidadeAppService);
  private readonly cepService = inject(CepService);
  private readonly unidadeService = inject(UnidadeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly sexos = SEXOS;
  protected readonly ufs = UFS;

  /** Abas do formulário (na ordem exibida). */
  protected readonly abas: { id: AbaId; label: string }[] = [
    { id: 'pessoais', label: 'Dados pessoais' },
    { id: 'contato', label: 'Contato' },
    { id: 'endereco', label: 'Endereço' },
    { id: 'responsaveis', label: 'Responsáveis' },
    { id: 'unidades', label: 'Unidades de acesso' },
  ];
  protected readonly abaAtiva = signal<AbaId>('pessoais');

  protected selecionarAba(id: AbaId): void {
    this.abaAtiva.set(id);
  }

  /** Controles (com validação) de cada aba — para sinalizar erro e pular até a aba certa. */
  private controlesDaAba(aba: AbaId): AbstractControl[] {
    switch (aba) {
      case 'pessoais':
        return [
          this.form.controls.nome,
          this.form.controls.dataNascimento,
          this.form.controls.cpf,
          this.form.controls.cns,
        ];
      case 'contato':
        return [this.form.controls.email, this.form.controls.telefonesAdicionais];
      case 'responsaveis':
        return [this.form.controls.responsaveis];
      default:
        return []; // Endereço e Unidades não têm campos obrigatórios
    }
  }

  /** A aba tem algum campo inválido já tocado/editado? (mostra o ponto de alerta na aba). */
  protected abaComErro(aba: AbaId): boolean {
    return this.controlesDaAba(aba).some((c) => c.invalid && (c.touched || c.dirty));
  }

  protected readonly form = new FormGroup({
    codigoIntegracao: new FormControl('', { nonNullable: true }),
    prontuario: new FormControl('', { nonNullable: true }),
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    sexo: new FormControl<string | null>(null),
    dataNascimento: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, dataNascimentoValidator],
    }),
    rg: new FormControl('', { nonNullable: true }),
    cpf: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    cns: new FormControl('', { nonNullable: true }),
    nomeMae: new FormControl('', { nonNullable: true }),
    nomePai: new FormControl('', { nonNullable: true }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.email] }),
    telefonesAdicionais: new FormArray<FormControl<string>>([], aoMenosUmTelefoneValidator),
    responsaveis: new FormArray<ResponsavelForm>([]),
    cep: new FormControl('', { nonNullable: true }),
    rua: new FormControl('', { nonNullable: true }),
    numero: new FormControl('', { nonNullable: true }),
    bairro: new FormControl('', { nonNullable: true }),
    municipio: new FormControl('', { nonNullable: true }),
    uf: new FormControl<string | null>(null),
    complemento: new FormControl('', { nonNullable: true }),
    unidadeIds: new FormControl<number[]>([], { nonNullable: true }),
  });

  /** Unidades de saúde disponíveis para vincular ao paciente. */
  protected readonly unidadesDisponiveis = signal<Unidade[]>([]);

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly erroCarregar = signal(false);
  protected readonly buscandoCep = signal(false);
  /** Foto (pré-assinada) do paciente para o avatar do form; null se não tiver. */
  protected readonly fotoUrl = signal<string | null>(null);

  // Acesso ao app (o paciente ativa sozinho por OTP; aqui o admin só revoga).
  protected readonly ativo = signal(false);
  protected readonly revogando = signal(false);

  // Situação do cadastro (soft-delete). Inativo = somente leitura (form desabilitado).
  protected readonly situacao = signal<SituacaoCadastro>('ATIVO');
  protected readonly inativo = computed(() => this.situacao() === 'INATIVO');
  protected readonly alterandoSituacao = signal(false);
  /** Nome do paciente carregado (título do histórico e mensagens). */
  protected readonly nomePaciente = signal('');
  /** Modal de histórico (auditoria LGPD) aberto? */
  protected readonly historicoAberto = signal(false);

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;
  /** Evita disparar a busca de CEP enquanto o formulário é preenchido pelo carregamento. */
  private preenchendo = false;

  constructor() {
    // Autopreenchimento de endereço ao completar o CEP (só em digitação do usuário).
    this.form.controls.cep.valueChanges.pipe(takeUntilDestroyed()).subscribe((cep) => {
      if (this.preenchendo) return;
      if ((cep ?? '').replace(/\D/g, '').length === 8) this.buscarCep();
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editando.set(true);
      this.codigo.set(Number(idParam));
    } else {
      // Novo paciente: começa com um campo de telefone em branco (a lista exige ≥1).
      this.setTelefonesAdicionais([]);
    }
    // Só carrega no navegador (evita chamada sem token no SSR/prerender).
    afterNextRender(() => {
      this.carregarTelasHabilitadas();
      this.carregarUnidades();
      if (this.editando() && this.codigo() != null) this.carregar(this.codigo()!);
    });
  }

  /** Carrega o kill-switch global; falha mantém {} = tudo visível (não esconde por engano). */
  private carregarTelasHabilitadas(): void {
    this.funcionalidadeAppService.carregar().subscribe({
      next: (telas) => this.telasHabilitadas.set(telas),
      error: () => {},
    });
  }

  private carregarUnidades(): void {
    this.unidadeService.listar({}, 0, 100).subscribe({
      next: (pagina) => this.unidadesDisponiveis.set(pagina.content),
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
    // A regra exige ≥1 telefone: garante um campo em branco quando a lista fica vazia
    // (novo cadastro ou cadastro legado sem telefones).
    if (this.telefonesAdicionais.length === 0) {
      this.telefonesAdicionais.push(new FormControl('', { nonNullable: true }));
    }
  }

  /** Erro "informe ao menos um telefone" (após toque/edição), no padrão dos demais campos. */
  protected telefonesInvalido(): boolean {
    const array = this.telefonesAdicionais;
    return array.hasError('telefoneObrigatorio') && (array.touched || array.dirty);
  }

  protected get responsaveis(): FormArray<ResponsavelForm> {
    return this.form.controls.responsaveis;
  }

  /** Funcionalidades e níveis para a matriz de permissões (template). */
  protected readonly funcionalidades = FUNCIONALIDADES_APP;
  protected readonly niveis = NIVEIS_ACESSO;

  /**
   * Kill-switch global das telas do app (GET /funcionalidades). {} = ainda não carregou →
   * trata tudo como habilitado (não esconde nada por engano). false = tela desligada.
   */
  protected readonly telasHabilitadas = signal<Record<string, boolean>>({});

  /**
   * Funcionalidades EXIBIDAS na matriz: esconde as telas desligadas globalmente (default:
   * visível). IMPORTANTE: filtra SOMENTE a exibição — grupoResponsavel() e permissoesDoGrupo()
   * seguem iterando FUNCIONALIDADES_APP completo, para o nível salvo de uma tela oculta ser
   * reenviado no PUT (que faz substituição total do mapa) e o vínculo sobreviver.
   */
  protected readonly funcionalidadesVisiveis = computed(() =>
    FUNCIONALIDADES_APP.filter((f) => this.telasHabilitadas()[f.value] !== false),
  );

  /** Níveis oferecidos para uma funcionalidade (Prontuário não tem "Visualizar e lançar"). */
  protected niveisPara(funcionalidade: { semLancamento?: boolean }): { value: NivelAcesso; label: string }[] {
    return funcionalidade.semLancamento ? NIVEIS_SEM_LANCAMENTO : NIVEIS_ACESSO;
  }

  /** Cria o grupo de um responsável (novo = sem id). */
  private grupoResponsavel(r: Responsavel = { nome: '' }): ResponsavelForm {
    const controlesPermissoes = {} as Record<FuncionalidadeApp, FormControl<NivelAcesso>>;
    for (const f of FUNCIONALIDADES_APP) {
      controlesPermissoes[f.value] = new FormControl<NivelAcesso>(
        r.permissoes?.[f.value] ?? 'SEM_ACESSO',
        { nonNullable: true },
      );
    }
    return new FormGroup({
      id: new FormControl<number | null>(r.id ?? null),
      nome: new FormControl(r.nome ?? '', {
        nonNullable: true,
        validators: [Validators.required, Validators.minLength(2)],
      }),
      cpf: new FormControl(r.cpf ?? '', {
        nonNullable: true,
        validators: [Validators.required],
      }),
      telefone: new FormControl(r.telefone ?? '', { nonNullable: true }),
      dataNascimento: new FormControl(this.isoParaData(r.dataNascimento ?? null), {
        nonNullable: true,
        validators: [Validators.required, dataNascimentoValidator],
      }),
      permissoes: new FormGroup(controlesPermissoes),
      ativo: new FormControl(r.ativo ?? true, { nonNullable: true }),
      temLancamentos: new FormControl(r.temLancamentos ?? false, { nonNullable: true }),
    });
  }

  /** Extrai as concessões do sub-grupo (só níveis diferentes de SEM_ACESSO). */
  private permissoesDoGrupo(grupo: PermissoesForm): PermissoesResponsavel {
    const perms: PermissoesResponsavel = {};
    for (const f of FUNCIONALIDADES_APP) {
      const nivel = grupo.controls[f.value].value;
      if (nivel !== 'SEM_ACESSO') {
        perms[f.value] = nivel;
      }
    }
    return perms;
  }

  protected adicionarResponsavel(): void {
    this.responsaveis.push(this.grupoResponsavel());
    this.form.markAsDirty();
  }

  protected removerResponsavel(indice: number): void {
    // Responsável com lançamentos não pode ser removido (só inativado) — o backend também
    // barra (409), mas evitamos a tentativa aqui.
    if (this.responsaveis.at(indice).controls.temLancamentos.value) {
      this.toastr.info('Este responsável tem lançamentos. Inative-o em vez de remover.');
      return;
    }
    this.responsaveis.removeAt(indice);
    this.form.markAsDirty();
  }

  /** Marca se um responsável tem lançamentos (não pode remover, só inativar). */
  protected temLancamentos(indice: number): boolean {
    return this.responsaveis.at(indice).controls.temLancamentos.value;
  }

  /** Situação atual do responsável no form (ativo/inativo). */
  protected responsavelAtivo(indice: number): boolean {
    return this.responsaveis.at(indice).controls.ativo.value;
  }

  /** Alterna ativo/inativo do responsável (salvo com o paciente). */
  protected alternarAtivoResponsavel(indice: number): void {
    const controle = this.responsaveis.at(indice).controls.ativo;
    controle.setValue(!controle.value);
    this.form.markAsDirty();
  }

  private setResponsaveis(itens: Responsavel[]): void {
    this.responsaveis.clear();
    itens.forEach((r) => this.responsaveis.push(this.grupoResponsavel(r)));
  }

  /** Erro de "nome obrigatório" de uma linha de responsável (após toque/edição). */
  protected responsavelNomeInvalido(indice: number): boolean {
    const c = this.responsaveis.at(indice).controls.nome;
    return c.invalid && (c.touched || c.dirty);
  }

  /** Erro de "CPF obrigatório" de uma linha de responsável (após toque/edição). */
  protected responsavelCpfInvalido(indice: number): boolean {
    const c = this.responsaveis.at(indice).controls.cpf;
    return c.invalid && (c.touched || c.dirty);
  }

  /** Erro de data de nascimento inválida de uma linha de responsável (após toque/edição). */
  protected responsavelDataInvalida(indice: number): boolean {
    const c = this.responsaveis.at(indice).controls.dataNascimento;
    return c.invalid && (c.touched || c.dirty);
  }

  private carregar(id: number): void {
    this.service.buscarPorId(id).subscribe({
      next: (p) => {
        this.preenchendo = true;
        this.form.patchValue({
          codigoIntegracao: p.codigoIntegracao ?? '',
          prontuario: p.prontuario ?? '',
          nome: p.nome,
          sexo: p.sexo ?? null,
          dataNascimento: this.isoParaData(p.dataNascimento ?? null),
          rg: p.rg ?? '',
          cpf: p.cpf ?? '',
          cns: p.cns ?? '',
          nomeMae: p.nomeMae ?? '',
          nomePai: p.nomePai ?? '',
          email: p.email ?? '',
          cep: p.cep ?? '',
          rua: p.rua ?? '',
          numero: p.numero ?? '',
          bairro: p.bairro ?? '',
          municipio: p.municipio ?? '',
          uf: p.uf ?? null,
          complemento: p.complemento ?? '',
        });
        this.setTelefonesAdicionais(p.telefonesAdicionais ?? []);
        this.setResponsaveis(p.responsaveis ?? []);
        this.form.controls.unidadeIds.setValue((p.unidades ?? []).map((u) => u.id));
        this.preenchendo = false;
        this.ativo.set(!!p.ativo);
        this.nomePaciente.set(p.nome ?? '');
        this.aplicarSituacao(p.situacao ?? 'ATIVO');
        this.fotoUrl.set(p.fotoUrl ?? null);
      },
      error: () => this.erroCarregar.set(true),
    });
  }

  /** Aplica a situação: inativo desabilita o formulário inteiro (somente leitura). */
  private aplicarSituacao(situacao: SituacaoCadastro): void {
    this.situacao.set(situacao);
    if (situacao === 'INATIVO') {
      this.form.disable({ emitEvent: false });
    } else {
      this.form.enable({ emitEvent: false });
    }
  }

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

  /** Iniciais do nome (fallback do avatar quando o paciente não tem foto). */
  protected iniciais(nome: string | null | undefined): string {
    const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean);
    if (partes.length === 0) return '—';
    const a = partes[0].charAt(0);
    const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (a + b).toUpperCase();
  }

  private valores(): PacienteEntrada {
    const f = this.form.getRawValue();
    const texto = (v: string) => (v.trim() ? v.trim() : null);
    return {
      nome: f.nome.trim(),
      codigoIntegracao: texto(f.codigoIntegracao),
      prontuario: texto(f.prontuario),
      sexo: (f.sexo as PacienteEntrada['sexo']) ?? null,
      dataNascimento: this.dataParaIso(f.dataNascimento),
      rg: texto(f.rg),
      cpf: texto(f.cpf),
      cns: texto(f.cns),
      nomeMae: texto(f.nomeMae),
      nomePai: texto(f.nomePai),
      email: texto(f.email),
      rua: texto(f.rua),
      numero: texto(f.numero),
      bairro: texto(f.bairro),
      municipio: texto(f.municipio),
      uf: f.uf ?? null,
      cep: texto(f.cep),
      complemento: texto(f.complemento),
      telefonesAdicionais: this.telefonesAdicionais.controls
        .map((c) => (c.value ?? '').trim())
        .filter((v) => v.length > 0),
      responsaveis: this.responsaveis.controls
        .map((g) => ({
          id: g.controls.id.value ?? null,
          nome: (g.controls.nome.value ?? '').trim(),
          cpf: (g.controls.cpf.value ?? '').trim(),
          telefone: (g.controls.telefone.value ?? '').trim() || null,
          dataNascimento: this.dataParaIso(g.controls.dataNascimento.value),
          permissoes: this.permissoesDoGrupo(g.controls.permissoes),
          ativo: g.controls.ativo.value,
        }))
        .filter((r) => r.nome.length > 0),
      unidadeIds: f.unidadeIds,
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
        this.toastr.success('Paciente salvo');
        this.router.navigate(['/pacientes']);
      },
      error: (e) => {
        this.salvando.set(false);
        this.toastr.error(this.mensagemErro(e));
      },
    });
  }

  /** Mensagem amigável a partir da resposta do backend (409 = dado único; 400 = validação). */
  private mensagemErro(e: { status?: number; error?: { message?: string } }): string {
    if (e?.status === 409) return e.error?.message ?? 'Já existe um paciente com um dos dados únicos.';
    if (e?.status === 400) return e.error?.message ?? 'Verifique os dados informados (CPF/CNS inválido?).';
    return 'Não foi possível salvar o paciente.';
  }

  protected async revogar(): Promise<void> {
    const confirmado = await this.confirmar(
      'Revogar o acesso do paciente ao app? Ele precisará de um novo código para entrar.',
    );
    if (!confirmado) return;
    this.revogando.set(true);
    this.service.revogarAcesso(this.codigo()!).subscribe({
      next: () => {
        this.ativo.set(false);
        this.revogando.set(false);
        this.toastr.success('Acesso revogado');
      },
      error: () => {
        this.revogando.set(false);
        this.toastr.error('Não foi possível revogar o acesso.');
      },
    });
  }

  protected async inativar(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar(
      'Inativar este cadastro? Ele fica somente leitura, sai das buscas e o acesso ao app é revogado. Você pode reativar depois.',
    );
    if (!confirmado) return;
    this.alterandoSituacao.set(true);
    this.service.inativar(this.codigo()!).subscribe({
      next: () => {
        this.alterandoSituacao.set(false);
        this.ativo.set(false);
        this.aplicarSituacao('INATIVO');
        this.toastr.success('Cadastro inativado');
      },
      error: () => {
        this.alterandoSituacao.set(false);
        this.toastr.error('Não foi possível inativar o cadastro.');
      },
    });
  }

  protected async reativar(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar(
      'Reativar este cadastro? Ele volta a ser editável e aparece nas buscas. O acesso ao app não é religado (gere um novo código se precisar).',
    );
    if (!confirmado) return;
    this.alterandoSituacao.set(true);
    this.service.reativar(this.codigo()!).subscribe({
      next: () => {
        this.alterandoSituacao.set(false);
        this.aplicarSituacao('ATIVO');
        this.toastr.success('Cadastro reativado');
      },
      error: () => {
        this.alterandoSituacao.set(false);
        this.toastr.error('Não foi possível reativar o cadastro.');
      },
    });
  }

  protected abrirHistorico(): void {
    this.historicoAberto.set(true);
  }

  protected fecharHistorico(): void {
    this.historicoAberto.set(false);
  }

  protected cancelar(): void {
    this.router.navigate(['/pacientes']);
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
