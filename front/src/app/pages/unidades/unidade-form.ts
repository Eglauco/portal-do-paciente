import { Component, computed, inject, signal } from '@angular/core';
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
import { ArmazenamentoS3Form } from '../../shared/armazenamento/armazenamento-s3-form';
import { EscopoS3Input } from '../../shared/armazenamento/armazenamento.model';
import { CredencialIaForm } from '../../shared/credencial-ia/credencial-ia-form';
import { EscopoIaInput } from '../../shared/credencial-ia/credencial-ia.model';
import { StorageService } from '../prontuarios/storage.service';
import { DiaSemana, Unidade, UnidadeFaqItem, UnidadeHorario } from './unidade.model';
import { UnidadeService } from './unidade.service';

/** Grupo do formulário para um item de FAQ (pergunta + resposta). */
type FaqItemForm = FormGroup<{
  id: FormControl<number | null>;
  pergunta: FormControl<string>;
  resposta: FormControl<string>;
}>;

/** Como o dia funciona: horário específico, aberta 24h o dia todo, ou fechada. */
type ModoHorario = 'ABERTO' | 'H24' | 'FECHADO';

/** Grupo do formulário de um dia de funcionamento. Hora no formato "HH:mm". */
type HorarioForm = FormGroup<{
  diaSemana: FormControl<DiaSemana>;
  modo: FormControl<ModoHorario>;
  abertura: FormControl<string>;
  fechamento: FormControl<string>;
}>;

/** Dias da semana na ordem de exibição (segunda → domingo) com o rótulo pt-BR. */
const DIAS: { dia: DiaSemana; label: string }[] = [
  { dia: 'MONDAY', label: 'Segunda-feira' },
  { dia: 'TUESDAY', label: 'Terça-feira' },
  { dia: 'WEDNESDAY', label: 'Quarta-feira' },
  { dia: 'THURSDAY', label: 'Quinta-feira' },
  { dia: 'FRIDAY', label: 'Sexta-feira' },
  { dia: 'SATURDAY', label: 'Sábado' },
  { dia: 'SUNDAY', label: 'Domingo' },
];

/** Abas do cadastro (dividem o formulário em seções). */
type AbaId = 'dados' | 'perfil' | 'faq' | 'armazenamento' | 'ia';

@Component({
  selector: 'app-unidade-form',
  imports: [ReactiveFormsModule, ArmazenamentoS3Form, CredencialIaForm],
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

    /* Perfil (app): capa, logo e horário de funcionamento. */
    .perfil-aba { display: flex; flex-direction: column; gap: 1.25rem; }
    .perfil-capa {
      width: 100%; max-width: 420px; aspect-ratio: 16 / 7; overflow: hidden;
      border: 1px solid var(--line); border-radius: 0.6rem;
      background: color-mix(in srgb, var(--brand) 4%, var(--surface));
      display: flex; align-items: center; justify-content: center;
    }
    .perfil-capa img { width: 100%; height: 100%; object-fit: cover; }
    .perfil-logo {
      width: 96px; height: 96px; overflow: hidden; border-radius: 50%;
      border: 1px solid var(--line);
      background: color-mix(in srgb, var(--brand) 4%, var(--surface));
      display: flex; align-items: center; justify-content: center;
    }
    .perfil-logo img { width: 100%; height: 100%; object-fit: cover; }
    .perfil-capa__vazio, .perfil-logo__vazio { font-size: 0.82rem; color: var(--muted); }
    .perfil-acoes { display: flex; gap: 0.5rem; flex-wrap: wrap; margin-top: 0.5rem; }
    .perfil-acoes label.btn { cursor: pointer; margin: 0; }

    .horario-sempre {
      display: inline-flex; align-items: center; gap: 0.45rem;
      font-size: 0.9rem; font-weight: 600; color: var(--ink); margin: 0.6rem 0 0.1rem;
    }
    .horario-lista { display: flex; flex-direction: column; gap: 0.5rem; margin-top: 0.6rem; }
    .horario-linha {
      display: flex; align-items: center; gap: 0.75rem; flex-wrap: wrap;
      padding: 0.5rem 0.7rem; border: 1px solid var(--line); border-radius: 0.5rem;
    }
    .horario-dia { font-weight: 600; min-width: 130px; }
    .horario-modo { min-width: 120px; }
    .horario-tempos { display: inline-flex; align-items: center; gap: 0.5rem; margin-left: auto; }
    .horario-info { color: var(--muted); font-size: 0.9rem; margin-left: auto; }
    .horario-sep { color: var(--muted); font-size: 0.85rem; }
    .input--sm { width: auto; padding: 0.35rem 0.5rem; }
  `],
})
export class UnidadeForm implements PodeSair {
  private readonly service = inject(UnidadeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);
  private readonly storage = inject(StorageService);

  /** Perfil: URL crua (p/ salvar) e a prévia (assinada / object URL local) de logo e capa. */
  private logoRaw: string | null = null;
  private capaRaw: string | null = null;
  protected readonly logoPreview = signal<string | null>(null);
  protected readonly capaPreview = signal<string | null>(null);
  protected readonly enviandoLogo = signal(false);
  protected readonly enviandoCapa = signal(false);

  /** Abas do formulário (na ordem exibida). */
  protected readonly abas: { id: AbaId; label: string }[] = [
    { id: 'dados', label: 'Dados' },
    { id: 'perfil', label: 'Perfil (app)' },
    { id: 'faq', label: 'FAQ da assistente virtual' },
    { id: 'armazenamento', label: 'Armazenamento' },
    { id: 'ia', label: 'Inteligência Artificial' },
  ];
  protected readonly dias = DIAS;
  protected readonly abaAtiva = signal<AbaId>('dados');

  /** Escopo do S3 desta unidade (só quando editando — precisa do id). */
  protected readonly escopoS3 = computed<EscopoS3Input | null>(() => {
    const id = this.codigo();
    return id != null ? { tipo: 'unidade', unidadeId: id } : null;
  });

  /** Escopo da credencial de IA desta unidade (só quando editando — precisa do id). */
  protected readonly escopoIa = computed<EscopoIaInput | null>(() => {
    const id = this.codigo();
    return id != null ? { tipo: 'unidade', unidadeId: id } : null;
  });

  protected selecionarAba(id: AbaId): void {
    this.abaAtiva.set(id);
  }

  protected readonly form = new FormGroup({
    nome: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(3)],
    }),
    codigoIntegracao: new FormControl('', { nonNullable: true }),
    sobre: new FormControl('', { nonNullable: true }),
    telefone: new FormControl('', { nonNullable: true }),
    endereco: new FormControl('', { nonNullable: true }),
    faq: new FormArray<FaqItemForm>([]),
    horarios: new FormArray<HorarioForm>(DIAS.map((d) => this.grupoHorario(d.dia))),
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
          this.form.patchValue({
            nome: unidade.nome,
            codigoIntegracao: unidade.codigoIntegracao ?? '',
            sobre: unidade.sobre ?? '',
            telefone: unidade.telefone ?? '',
            endereco: unidade.endereco ?? '',
          });
          this.setFaq(unidade.faq ?? []);
          if (unidade.horarios?.length) this.setHorarios(unidade.horarios);
          this.logoRaw = unidade.logoUrl ?? null;
          this.capaRaw = unidade.capaUrl ?? null;
          this.logoPreview.set(unidade.logoUrlView ?? null); // URL já assinada pelo backend
          this.capaPreview.set(unidade.capaUrlView ?? null);
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

  // --- Horários de funcionamento -------------------------------------------------------------
  protected get horariosItens(): FormArray<HorarioForm> {
    return this.form.controls.horarios;
  }

  /** Grupo de um dia. Sem valor salvo: útil Seg–Sex 09:00–18:00, Sáb/Dom fechado (padrão sensato). */
  private grupoHorario(dia: DiaSemana, item?: UnidadeHorario): HorarioForm {
    const fds = dia === 'SATURDAY' || dia === 'SUNDAY';
    const modo: ModoHorario = item
      ? item.fechado
        ? 'FECHADO'
        : item.vinteQuatroHoras
          ? 'H24'
          : 'ABERTO'
      : fds
        ? 'FECHADO'
        : 'ABERTO';
    return new FormGroup({
      diaSemana: new FormControl<DiaSemana>(dia, { nonNullable: true }),
      modo: new FormControl<ModoHorario>(modo, { nonNullable: true }),
      // Mantém HH:mm para reaparecer ao voltar de 24h/Fechada; default sensato quando não há valor.
      abertura: new FormControl(item?.abertura?.slice(0, 5) || '09:00', { nonNullable: true }),
      fechamento: new FormControl(item?.fechamento?.slice(0, 5) || '18:00', { nonNullable: true }),
    });
  }

  /** Marca/desmarca "aberta 24 horas todos os dias" (sempre aberta): põe todos em 24h ou no padrão. */
  protected sempreAberta(): boolean {
    const ctrls = this.horariosItens.controls;
    return ctrls.length > 0 && ctrls.every((g) => g.controls.modo.value === 'H24');
  }

  protected alternarSempreAberta(event: Event): void {
    const marcar = (event.target as HTMLInputElement).checked;
    this.horariosItens.controls.forEach((g) => {
      const fds = g.controls.diaSemana.value === 'SATURDAY' || g.controls.diaSemana.value === 'SUNDAY';
      g.controls.modo.setValue(marcar ? 'H24' : fds ? 'FECHADO' : 'ABERTO');
    });
    this.form.markAsDirty();
  }

  /** Reconstrói os 7 dias na ordem fixa, preenchendo com o que veio salvo (dias ausentes = padrão). */
  private setHorarios(itens: UnidadeHorario[]): void {
    const porDia = new Map(itens.map((h) => [h.diaSemana, h]));
    this.horariosItens.clear();
    DIAS.forEach((d) => this.horariosItens.push(this.grupoHorario(d.dia, porDia.get(d.dia))));
  }

  // --- Logo / capa (upload direto ao S3 da unidade) -------------------------------------------
  protected get logo(): string | null {
    return this.logoPreview();
  }
  protected get capa(): string | null {
    return this.capaPreview();
  }

  protected async onSelectLogo(event: Event): Promise<void> {
    await this.enviarImagemPerfil(event, true);
  }
  protected async onSelectCapa(event: Event): Promise<void> {
    await this.enviarImagemPerfil(event, false);
  }

  /** Sobe a imagem pro S3 da unidade (logo = recorte quadrado; capa = original) e mostra a prévia. */
  private async enviarImagemPerfil(event: Event, ehLogo: boolean): Promise<void> {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    input.value = ''; // permite reenviar o mesmo arquivo
    if (!arquivo) return;
    if (!arquivo.type.startsWith('image/')) {
      this.toastr.error('Selecione uma imagem.');
      return;
    }
    const id = this.codigo();
    if (id == null) {
      this.toastr.info('Salve a unidade primeiro para enviar imagens.');
      return;
    }
    const sinal = ehLogo ? this.enviandoLogo : this.enviandoCapa;
    sinal.set(true);
    try {
      const url = ehLogo
        ? await this.storage.enviarImagem(arquivo, 'perfil-unidade', { maxLado: 512 }, id)
        : await this.storage.enviar(arquivo, 'perfil-unidade', id);
      const preview = URL.createObjectURL(arquivo);
      if (ehLogo) {
        this.logoRaw = url;
        this.logoPreview.set(preview);
      } else {
        this.capaRaw = url;
        this.capaPreview.set(preview);
      }
      this.form.markAsDirty();
    } catch {
      this.toastr.error('Não foi possível enviar a imagem.');
    } finally {
      sinal.set(false);
    }
  }

  protected removerLogo(): void {
    this.logoRaw = null;
    this.logoPreview.set(null);
    this.form.markAsDirty();
  }
  protected removerCapa(): void {
    this.capaRaw = null;
    this.capaPreview.set(null);
    this.form.markAsDirty();
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
      codigoIntegracao: this.form.controls.codigoIntegracao.value.trim() || null,
      logoUrl: this.logoRaw,
      capaUrl: this.capaRaw,
      sobre: this.form.controls.sobre.value.trim() || null,
      telefone: this.form.controls.telefone.value.trim() || null,
      endereco: this.form.controls.endereco.value.trim() || null,
      // Só envia itens completos; a ordem na lista é a posição enviada ao backend.
      faq: this.faqItens.controls
        .map((g) => ({
          pergunta: (g.controls.pergunta.value ?? '').trim(),
          resposta: (g.controls.resposta.value ?? '').trim(),
        }))
        .filter((f) => f.pergunta.length > 0 && f.resposta.length > 0),
      // Um registro por dia; fechado/24h zeram os horários (só "ABERTO" manda abertura/fechamento).
      horarios: this.horariosItens.controls.map((g) => {
        const modo = g.controls.modo.value;
        const aberto = modo === 'ABERTO';
        return {
          diaSemana: g.controls.diaSemana.value,
          fechado: modo === 'FECHADO',
          vinteQuatroHoras: modo === 'H24',
          abertura: aberto ? g.controls.abertura.value || null : null,
          fechamento: aberto ? g.controls.fechamento.value || null : null,
        };
      }),
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
