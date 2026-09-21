import { DatePipe, DecimalPipe } from '@angular/common';
import { MarkdownPipe } from '../../shared/markdown.pipe';
import { afterNextRender, Component, inject, signal } from '@angular/core';
import { FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { firstValueFrom } from 'rxjs';
import { PodeSair } from '../../core/pending-changes.guard';
import { Agendamento, filtroVazio } from '../agendamentos/agendamento.model';
import { AgendamentoService } from '../agendamentos/agendamento.service';
import { TipoDocumentoProntuarioAtivo } from '../tipos-documento-prontuario/tipo-documento-prontuario.model';
import { TipoDocumentoProntuarioService } from '../tipos-documento-prontuario/tipo-documento-prontuario.service';
import {
  DecisaoValidacao,
  DocumentoAdmin,
  ProntuarioDetalhe,
  ProntuarioRequest,
  StatusAnaliseDocumento,
} from './prontuario.model';
import { ProntuarioService } from './prontuario.service';
import { StorageService } from './storage.service';

interface OpcaoAgendamento {
  id: number;
  rotulo: string;
}

type DocumentoGroup = FormGroup<{
  /** id do documento salvo (null para os novos, ainda não persistidos). */
  id: FormControl<number | null>;
  nome: FormControl<string>;
  url: FormControl<string | null>;
  arquivo: FormControl<File | null>;
  tipoId: FormControl<number | null>;
  // Dados da análise por IA (somente leitura, para exibição):
  statusAnalise: FormControl<StatusAnaliseDocumento | null>;
  statusAnaliseDescricao: FormControl<string | null>;
  resumoClinico: FormControl<string | null>;
  tipoNome: FormControl<string | null>;
  validadoPorNome: FormControl<string | null>;
  validadoEm: FormControl<string | null>;
  /** Observação da decisão humana (editável na rotina de validação). */
  observacaoValidacao: FormControl<string | null>;
  /** Tokens gastos pela IA (somente exibição). */
  tokensEntrada: FormControl<number | null>;
  tokensSaida: FormControl<number | null>;
  /** Modelo de IA usado (somente exibição). */
  modeloIa: FormControl<string | null>;
  /** Custo (US$) do uso da IA (somente exibição). */
  custoUsd: FormControl<number | null>;
  /** Nº de gerações da IA (somente exibição). */
  geracoesIa: FormControl<number | null>;
}>;

@Component({
  selector: 'app-prontuario-form',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe, DecimalPipe, MarkdownPipe],
  templateUrl: './prontuario-form.html',
})
export class ProntuarioForm implements PodeSair {
  private readonly service = inject(ProntuarioService);
  private readonly agendamentoService = inject(AgendamentoService);
  private readonly tipoDocumentoService = inject(TipoDocumentoProntuarioService);
  private readonly storage = inject(StorageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly agendamentos = signal<OpcaoAgendamento[]>([]);
  protected readonly tiposDocumento = signal<TipoDocumentoProntuarioAtivo[]>([]);

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

  /** Índice do documento aberto no modal de detalhe (null = modal fechado). */
  protected readonly documentoAberto = signal<number | null>(null);
  /** Ids dos documentos com a decisão de validação em andamento. */
  protected readonly validando = signal<Set<number>>(new Set());
  /** Ids dos documentos em reprocessamento (assíncrono; o resultado aparece ao recarregar). */
  protected readonly reanalisando = signal<Set<number>>(new Set());

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

  /** Índice do documento aberto (dentro do @if do modal é sempre válido). */
  protected get docAbertoIdx(): number {
    return this.documentoAberto() ?? -1;
  }

  /** Rótulo do tipo para a lista: usa o tipoNome salvo, senão resolve pelo tipoId, senão "Sem tipo". */
  protected tipoRotulo(indice: number): string {
    const grupo = this.documentos.at(indice).controls;
    const nome = grupo.tipoNome.value?.trim();
    if (nome) return nome;
    const id = grupo.tipoId.value;
    if (id != null) {
      const tipo = this.tiposDocumento().find((t) => t.id === id);
      if (tipo) return tipo.nome;
    }
    return 'Sem tipo';
  }

  /** Nome do documento para a lista (ou "Sem nome" quando vazio). */
  protected nomeRotulo(indice: number): string {
    return this.documentos.at(indice).controls.nome.value.trim() || 'Sem nome';
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

  // --- Leitura dos dados de análise por índice (para o template) ---
  protected docId(indice: number): number | null {
    return this.documentos.at(indice).controls.id.value;
  }

  protected statusAnalise(indice: number): StatusAnaliseDocumento | null {
    return this.documentos.at(indice).controls.statusAnalise.value;
  }

  protected statusAnaliseDescricao(indice: number): string | null {
    return this.documentos.at(indice).controls.statusAnaliseDescricao.value;
  }

  protected resumoClinico(indice: number): string | null {
    return this.documentos.at(indice).controls.resumoClinico.value;
  }

  protected validadoPorNome(indice: number): string | null {
    return this.documentos.at(indice).controls.validadoPorNome.value;
  }

  protected validadoEm(indice: number): string | null {
    return this.documentos.at(indice).controls.validadoEm.value;
  }

  protected tokensEntrada(indice: number): number | null {
    return this.documentos.at(indice).controls.tokensEntrada.value;
  }

  protected tokensSaida(indice: number): number | null {
    return this.documentos.at(indice).controls.tokensSaida.value;
  }

  protected modeloIa(indice: number): string | null {
    return this.documentos.at(indice).controls.modeloIa.value;
  }

  protected custoUsd(indice: number): number | null {
    return this.documentos.at(indice).controls.custoUsd.value;
  }

  protected geracoesIa(indice: number): number | null {
    return this.documentos.at(indice).controls.geracoesIa.value;
  }

  protected temTipo(indice: number): boolean {
    return this.documentos.at(indice).controls.tipoId.value != null;
  }

  /** Abre o modal de detalhe do documento no índice informado. */
  protected abrirDocumento(indice: number): void {
    this.documentoAberto.set(indice);
  }

  /** Fecha o modal; trava enquanto o nome estiver inválido (mas marca como touched). */
  protected fecharDocumento(): void {
    const i = this.documentoAberto();
    if (i === null) return;
    const nome = this.documentos.at(i).controls.nome;
    nome.markAsTouched();
    if (nome.invalid) return;
    this.documentoAberto.set(null);
  }

  /** Cria um documento novo e já abre o modal nele. */
  protected novoDocumento(): void {
    this.adicionarDocumento();
    this.documentoAberto.set(this.documentos.length - 1);
  }

  protected estaValidando(id: number | null): boolean {
    return id != null && this.validando().has(id);
  }

  protected estaReanalisando(id: number | null): boolean {
    return id != null && this.reanalisando().has(id);
  }

  protected adicionarDocumento(doc?: DocumentoAdmin): void {
    this.documentos.push(
      new FormGroup({
        id: new FormControl<number | null>(doc?.id ?? null),
        nome: new FormControl(doc?.nome ?? '', { nonNullable: true, validators: [Validators.required] }),
        url: new FormControl<string | null>(doc?.url ?? null),
        arquivo: new FormControl<File | null>(null),
        tipoId: new FormControl<number | null>(doc?.tipoId ?? null),
        statusAnalise: new FormControl<StatusAnaliseDocumento | null>(doc?.statusAnalise ?? null),
        statusAnaliseDescricao: new FormControl<string | null>(doc?.statusAnaliseDescricao ?? null),
        resumoClinico: new FormControl<string | null>(doc?.resumoClinico ?? null),
        tipoNome: new FormControl<string | null>(doc?.tipoNome ?? null),
        validadoPorNome: new FormControl<string | null>(doc?.validadoPorNome ?? null),
        validadoEm: new FormControl<string | null>(doc?.validadoEm ?? null),
        observacaoValidacao: new FormControl<string | null>(doc?.observacaoValidacao ?? null),
        tokensEntrada: new FormControl<number | null>(doc?.tokensEntrada ?? null),
        tokensSaida: new FormControl<number | null>(doc?.tokensSaida ?? null),
        modeloIa: new FormControl<string | null>(doc?.modeloIa ?? null),
        custoUsd: new FormControl<number | null>(doc?.custoUsd ?? null),
        geracoesIa: new FormControl<number | null>(doc?.geracoesIa ?? null),
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
    this.documentoAberto.set(null);
    this.form.markAsDirty();
  }

  /**
   * Pode decidir (confirmar/negar) quando o documento está aguardando validação OU quando já foi
   * decidido por um humano (permite corrigir a decisão).
   */
  protected podeDecidir(indice: number): boolean {
    return this.statusAnalise(indice) === 'AGUARDANDO_VALIDACAO' || this.validadoPorNome(indice) != null;
  }

  /** Registra a decisão humana do alerta: confirmar a alteração ou marcar sem alteração. */
  protected decidir(indice: number, decisao: DecisaoValidacao): void {
    const id = this.docId(indice);
    if (id == null || this.estaValidando(id)) return;
    const observacao = this.documentos.at(indice).controls.observacaoValidacao.value;
    this.validando.update((set) => new Set(set).add(id));
    this.service.validarDocumento(id, decisao, observacao).subscribe({
      next: (detalhe) => {
        this.aplicarAnalise(detalhe);
        this.validando.update((set) => {
          const novo = new Set(set);
          novo.delete(id);
          return novo;
        });
        // Decisão registrada → fecha o modal do documento.
        this.documentoAberto.set(null);
        this.toastr.success(
          decisao === 'ALTERACAO_CONFIRMADA' ? 'Alteração confirmada' : 'Documento marcado sem alteração',
        );
      },
      error: () => {
        this.validando.update((set) => {
          const novo = new Set(set);
          novo.delete(id);
          return novo;
        });
        this.toastr.error('Não foi possível registrar a validação.');
      },
    });
  }

  /** Reprocessa a análise por IA de um documento (assíncrono). */
  protected reanalisar(indice: number): void {
    const id = this.docId(indice);
    if (id == null || this.estaReanalisando(id)) return;
    this.service.reanalisarDocumento(id).subscribe({
      next: () => {
        this.reanalisando.update((set) => new Set(set).add(id));
        // Reanálise solicitada → fecha o modal do documento.
        this.documentoAberto.set(null);
        this.toastr.info('Análise em reprocessamento. O resultado aparece ao recarregar.');
      },
      error: () => this.toastr.error('Não foi possível solicitar a reanálise.'),
    });
  }

  /** Atualiza os campos de análise dos documentos a partir do detalhe devolvido pelo backend. */
  private aplicarAnalise(detalhe: ProntuarioDetalhe): void {
    for (const d of detalhe.documentos) {
      const grupo = this.documentos.controls.find((g) => g.controls.id.value === d.id);
      if (!grupo) continue;
      grupo.patchValue(
        {
          statusAnalise: d.statusAnalise,
          statusAnaliseDescricao: d.statusAnaliseDescricao,
          resumoClinico: d.resumoClinico ?? null,
          tipoNome: d.tipoNome ?? null,
          validadoPorNome: d.validadoPorNome ?? null,
          validadoEm: d.validadoEm ?? null,
          observacaoValidacao: d.observacaoValidacao ?? null,
          tokensEntrada: d.tokensEntrada ?? null,
          tokensSaida: d.tokensSaida ?? null,
          modeloIa: d.modeloIa ?? null,
          custoUsd: d.custoUsd ?? null,
          geracoesIa: d.geracoesIa ?? null,
        },
        { emitEvent: false },
      );
    }
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
        documentos: grupos.map((g) => ({
          nome: g.controls.nome.value.trim(),
          url: urlPorGrupo.get(g) ?? null,
          tipoId: g.controls.tipoId.value ?? null,
        })),
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
    // Todos os agendamentos (sem filtro) para o seletor; unidadeId nulo = todas as unidades.
    this.agendamentoService.listar(filtroVazio(), null, 0, 100).subscribe({
      next: (p) => this.agendamentos.set(p.content.map((a) => ({ id: a.id!, rotulo: this.rotuloAgendamento(a) }))),
      error: () => this.toastr.error('Não foi possível carregar os agendamentos.'),
    });
    // Tipos de documento ativos para o seletor de cada documento.
    this.tipoDocumentoService.listarAtivos().subscribe({
      next: (tipos) => this.tiposDocumento.set(tipos),
      error: () => this.tiposDocumento.set([]),
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
        this.documentoAberto.set(null);
        p.documentos.forEach((d) => this.adicionarDocumento(d));
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
