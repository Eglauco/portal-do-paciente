import { afterNextRender, Component, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { firstValueFrom } from 'rxjs';
import { PodeSair } from '../../core/pending-changes.guard';
import { Agendamento } from '../agendamentos/agendamento.model';
import { AgendamentoService } from '../agendamentos/agendamento.service';
import { ProntuarioRequest } from './prontuario.model';
import { ProntuarioService } from './prontuario.service';
import { StorageService } from './storage.service';

interface OpcaoAgendamento {
  id: number;
  rotulo: string;
}

type DocumentoGroup = FormGroup<{
  nome: FormControl<string>;
  url: FormControl<string | null>;
  arquivo: FormControl<File | null>;
}>;

@Component({
  selector: 'app-prontuario-form',
  imports: [ReactiveFormsModule, NgSelectModule],
  templateUrl: './prontuario-form.html',
})
export class ProntuarioForm implements PodeSair {
  private readonly service = inject(ProntuarioService);
  private readonly agendamentoService = inject(AgendamentoService);
  private readonly storage = inject(StorageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly agendamentos = signal<OpcaoAgendamento[]>([]);

  protected readonly form = new FormGroup({
    agendamentoId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    numeroAtendimento: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    documentos: new FormArray<DocumentoGroup>([]),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);

  /** URLs dos arquivos que já estavam salvos (para limpar órfãos ao salvar). */
  private urlsOriginais: string[] = [];

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editando.set(true);
      this.codigo.set(Number(idParam));
    }
    afterNextRender(() => {
      this.carregarOpcoes();
      if (this.editando()) this.carregarProntuario();
    });
  }

  protected get documentos(): FormArray<DocumentoGroup> {
    return this.form.controls.documentos;
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected numeroInvalido(): boolean {
    const c = this.form.controls.numeroAtendimento;
    return c.invalid && (c.touched || c.dirty);
  }

  protected agendamentoInvalido(): boolean {
    const c = this.form.controls.agendamentoId;
    return c.invalid && (c.touched || c.dirty);
  }

  protected documentoInvalido(indice: number): boolean {
    const c = this.documentos.at(indice).controls.nome;
    return c.invalid && (c.touched || c.dirty);
  }

  /** Arquivo selecionado em memória (ainda não enviado ao S3). */
  protected arquivoPendente(indice: number): File | null {
    return this.documentos.at(indice).controls.arquivo.value;
  }

  /** Já existe um arquivo salvo (URL no S3). */
  protected temUrlSalva(indice: number): boolean {
    return !!this.documentos.at(indice).controls.url.value;
  }

  protected urlDoc(indice: number): string | null {
    return this.documentos.at(indice).controls.url.value;
  }

  protected adicionarDocumento(nome = '', url: string | null = null): void {
    this.documentos.push(
      new FormGroup({
        nome: new FormControl(nome, { nonNullable: true, validators: [Validators.required] }),
        url: new FormControl<string | null>(url),
        arquivo: new FormControl<File | null>(null),
      }),
    );
    this.form.markAsDirty();
  }

  /** Guarda o arquivo em memória (o envio ao S3 só ocorre no Salvar). */
  protected aoSelecionarArquivo(indice: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0] ?? null;
    input.value = ''; // permite re-selecionar o mesmo arquivo
    if (!arquivo) return;

    const grupo = this.documentos.at(indice);
    grupo.controls.arquivo.setValue(arquivo);
    if (!grupo.controls.nome.value.trim()) {
      grupo.controls.nome.setValue(arquivo.name);
    }
    this.form.markAsDirty();
  }

  /** Abre o arquivo: o pendente via blob local; o salvo via URL assinada. */
  protected async verArquivo(indice: number): Promise<void> {
    const pendente = this.arquivoPendente(indice);
    if (pendente) {
      const blob = URL.createObjectURL(pendente);
      window.open(blob, '_blank');
      return;
    }
    const url = this.urlDoc(indice);
    if (!url) return;
    const aba = window.open('about:blank', '_blank');
    try {
      const link = await this.storage.urlDownload(url);
      if (aba) aba.location.href = link;
      else window.open(link, '_blank');
    } catch {
      aba?.close();
      this.toastr.error('Não foi possível abrir o arquivo.');
    }
  }

  protected removerDocumento(indice: number): void {
    // Não mexe no S3 aqui: a limpeza de órfãos acontece no Salvar.
    this.documentos.removeAt(indice);
    this.form.markAsDirty();
  }

  protected async salvar(event: Event): Promise<void> {
    event.preventDefault();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);

    const grupos = this.documentos.controls;
    const urlPorGrupo = new Map<DocumentoGroup, string | null>();
    const enviadasAgora: string[] = [];

    try {
      // 1) Envia ao S3 os arquivos pendentes (em memória).
      for (const g of grupos) {
        const arquivo = g.controls.arquivo.value;
        if (arquivo) {
          const novaUrl = await this.storage.enviar(arquivo);
          enviadasAgora.push(novaUrl);
          urlPorGrupo.set(g, novaUrl);
        } else {
          urlPorGrupo.set(g, g.controls.url.value);
        }
      }

      // 2) Salva o prontuário com as URLs resolvidas.
      const v = this.form.getRawValue();
      const dados: ProntuarioRequest = {
        agendamentoId: v.agendamentoId!,
        numeroAtendimento: v.numeroAtendimento.trim(),
        documentos: grupos.map((g) => ({ nome: g.controls.nome.value.trim(), url: urlPorGrupo.get(g) ?? null })),
      };
      const requisicao = this.editando()
        ? this.service.atualizar(this.codigo()!, dados)
        : this.service.criar(dados);
      await firstValueFrom(requisicao);

      // 3) Remove do S3 os arquivos antigos que não são mais referenciados.
      const finais = new Set(
        grupos.map((g) => urlPorGrupo.get(g) ?? null).filter((u): u is string => !!u),
      );
      this.urlsOriginais
        .filter((u) => !finais.has(u))
        .forEach((u) => this.storage.excluir(u).catch(() => undefined));

      this.saidaAutorizada = true;
      this.toastr.success('Prontuário salvo');
      this.router.navigate(['/prontuarios']);
    } catch (e: unknown) {
      // Desfaz os uploads desta tentativa para não deixar lixo no S3.
      enviadasAgora.forEach((u) => this.storage.excluir(u).catch(() => undefined));
      this.salvando.set(false);
      const status = (e as { status?: number })?.status;
      this.toastr.error(
        status === 409 ? 'Número do atendimento já cadastrado.' : 'Não foi possível salvar o prontuário.',
      );
    }
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir o prontuário?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Prontuário excluído');
        this.router.navigate(['/prontuarios']);
      },
      error: () => {
        this.excluindo.set(false);
        this.toastr.error('Não foi possível excluir o prontuário.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/prontuarios']);
  }

  private carregarOpcoes(): void {
    // Assinatura: listar(status, unidadeId, page, size). Sem o unidadeId a chamada
    // ficava desalinhada (unidadeId=0, page=100) e o seletor vinha sempre vazio.
    this.agendamentoService.listar(null, null, 0, 100).subscribe({
      next: (p) => this.agendamentos.set(p.content.map((a) => ({ id: a.id!, rotulo: this.rotuloAgendamento(a) }))),
      error: () => this.toastr.error('Não foi possível carregar os agendamentos.'),
    });
  }

  private rotuloAgendamento(a: Agendamento): string {
    const [dataIso, horaIso] = (a.dataHora ?? '').split('T');
    const [ano, mes, dia] = dataIso.split('-');
    const hora = horaIso ? horaIso.slice(0, 5) : '';
    return `#${a.id} · ${a.paciente.nome} · ${a.especialidade.nome} · ${dia}/${mes}/${ano} ${hora}`;
  }

  private carregarProntuario(): void {
    this.service.buscarPorId(this.codigo()!).subscribe({
      next: (p) => {
        this.form.patchValue({
          agendamentoId: p.agendamentoId,
          numeroAtendimento: p.numeroAtendimento,
        });
        this.documentos.clear();
        p.documentos.forEach((d) => this.adicionarDocumento(d.nome, d.url ?? null));
        this.urlsOriginais = p.documentos.map((d) => d.url).filter((u): u is string => !!u);
        this.form.markAsPristine();
      },
      error: () => this.erroCarregar.set(true),
    });
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
