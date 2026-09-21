import { DatePipe, DecimalPipe } from '@angular/common';
import { MarkdownPipe } from '../../shared/markdown.pipe';
import { afterNextRender, Component, computed, inject, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { DecisaoValidacao, DocumentoAdmin, ProntuarioDetalhe } from '../prontuarios/prontuario.model';
import { ProntuarioService } from '../prontuarios/prontuario.service';
import { StorageService } from '../prontuarios/storage.service';
import { DocumentoAberto, HistoricoMedico, TipoArquivo } from './prontuario-medico.model';
import { ProntuarioMedicoService } from './prontuario-medico.service';
import {
  detectarTipoArquivo,
  formatarCpf,
  formatarTelefone,
  idadeRotulo,
  iniciais,
  sexoRotulo,
} from './prontuario-medico.util';

/** Um marco da linha do tempo: o atendimento e os documentos já filtrados para exibição. */
interface MarcoTimeline {
  prontuario: ProntuarioDetalhe;
  documentos: DocumentoAdmin[];
}

/**
 * Tela 2 do Prontuário Médico (a estrela): cabeçalho clínico fixo, resumo do histórico por IA,
 * filtros rápidos e a linha do tempo dos atendimentos com visor de documento embutido.
 * Somente leitura — validar/editar/anexar é da tela de cadastro de prontuários.
 */
@Component({
  selector: 'app-prontuario-medico-historico',
  imports: [DatePipe, DecimalPipe, MarkdownPipe],
  templateUrl: './prontuario-medico-historico.html',
  host: {
    '(document:keydown.escape)': 'aoEscape()',
    '(document:keydown.arrowleft)': 'aoSetaTeclado(-1, $event)',
    '(document:keydown.arrowright)': 'aoSetaTeclado(1, $event)',
    '(document:keydown.r)': 'aoTeclaR($event)',
  },
})
export class ProntuarioMedicoHistorico {
  private readonly service = inject(ProntuarioMedicoService);
  private readonly prontuarioService = inject(ProntuarioService);
  private readonly storage = inject(StorageService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toastr = inject(ToastrService);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly pacienteId = Number(this.route.snapshot.paramMap.get('pacienteId'));

  protected readonly historico = signal<HistoricoMedico | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal(false);

  // Resumo do histórico (IA) — gerado AUTOMATICAMENTE no backend ao analisar documentos (sem botão).
  protected readonly resumoIa = signal<string | null>(null);
  protected readonly resumoGeradoEm = signal<string | null>(null);
  /** Card do resumo minimizado (preferência do médico, lembrada entre visitas). */
  protected readonly resumoMinimizado = signal(this.lerMinimizado());

  // Filtros client-side.
  protected readonly filtroEspecialidade = signal<number | null>(null);
  protected readonly filtroTipo = signal<string>('');
  protected readonly somenteAlertas = signal(false);
  protected readonly texto = signal('');

  // Visor de documento embutido.
  protected readonly docAberto = signal<DocumentoAberto | null>(null);
  protected readonly visorCarregando = signal(false);
  protected readonly visorErro = signal(false);
  protected readonly tipoArquivo = signal<TipoArquivo>('outro');
  protected readonly urlSegura = signal<SafeResourceUrl | null>(null);
  protected readonly urlAssinada = signal<string | null>(null);
  /** Painel direito (resumo + atendimento) recolhido para o documento ocupar mais espaço (lembrado). */
  protected readonly infoMinimizada = signal(this.lerInfoMin());
  /** Visor em tela cheia (ocupa toda a janela). */
  protected readonly visorFullscreen = signal(false);
  /** O documento aberto aguarda validação (destaque âmbar no cabeçalho do visor). */
  protected readonly docAlerta = computed(
    () => this.docAberto()?.documento.statusAnalise === 'AGUARDANDO_VALIDACAO',
  );
  /** O documento aberto teve alteração confirmada (destaque vermelho — atenção do médico). */
  protected readonly docConfirmado = computed(
    () => this.docAberto()?.documento.statusAnalise === 'ALTERACAO_CONFIRMADA',
  );

  // Validação do documento no próprio visor (o médico pode validar durante o atendimento).
  /** Observação (opcional) da decisão, editável no visor; pré-preenchida ao abrir o documento. */
  protected readonly observacaoValidacao = signal('');
  /** Decisão de validação em andamento (evita duplo clique). */
  protected readonly validando = signal(false);
  /** Pode decidir quando o documento aguarda validação OU já foi decidido por um humano (correção). */
  protected readonly podeDecidir = computed(() => {
    const d = this.docAberto()?.documento;
    if (!d) return false;
    return d.statusAnalise === 'AGUARDANDO_VALIDACAO' || d.validadoPorNome != null;
  });

  protected readonly skeletons = Array.from({ length: 4 });

  protected readonly iniciais = iniciais;
  protected readonly idadeRotulo = idadeRotulo;
  protected readonly sexoRotulo = sexoRotulo;
  protected readonly formatarCpf = formatarCpf;
  protected readonly formatarTelefone = formatarTelefone;

  protected readonly paciente = computed(() => this.historico()?.paciente ?? null);
  protected readonly iaHabilitada = computed(() => this.historico()?.iaHabilitada ?? false);
  protected readonly resumoIaHabilitado = computed(() => this.historico()?.resumoIaHabilitado ?? false);
  /** Mostra a nota "será gerado automaticamente" quando ainda não há resumo, mas o recurso está ligado. */
  protected readonly mostrarNotaResumo = computed(
    () => !this.resumoIa() && this.iaHabilitada() && this.resumoIaHabilitado(),
  );

  // Consumo ACUMULADO de IA do resumo do histórico (exibido no card do resumo, na tela do médico).
  protected readonly resumoConsumo = computed(() => {
    const h = this.historico();
    if (!h || !h.resumoHistoricoGeracoes) return null;
    return {
      modelo: h.resumoHistoricoModeloIa ?? null,
      geracoes: h.resumoHistoricoGeracoes,
      tokensEntrada: h.resumoHistoricoTokensEntrada ?? 0,
      tokensSaida: h.resumoHistoricoTokensSaida ?? 0,
      custoUsd: h.resumoHistoricoCustoUsd ?? null,
    };
  });

  /** Atendimentos ordenados do mais recente ao mais antigo (defensivo). */
  protected readonly prontuarios = computed<ProntuarioDetalhe[]>(() =>
    [...(this.historico()?.prontuarios ?? [])].sort((a, b) => (b.dataHora ?? '').localeCompare(a.dataHora ?? '')),
  );

  /** Especialidades presentes no histórico (para o filtro). */
  protected readonly especialidades = computed(() => {
    const mapa = new Map<number, string>();
    for (const p of this.prontuarios()) {
      if (p.especialidade?.id != null) mapa.set(p.especialidade.id, p.especialidade.nome);
    }
    return [...mapa.entries()].map(([id, nome]) => ({ id, nome })).sort((a, b) => a.nome.localeCompare(b.nome));
  });

  /** Tipos de documento presentes no histórico (para o filtro). */
  protected readonly tiposDocumento = computed(() => {
    const set = new Set<string>();
    for (const p of this.prontuarios()) {
      for (const d of p.documentos) set.add(d.tipoNome?.trim() || 'Sem tipo');
    }
    return [...set].sort((a, b) => a.localeCompare(b));
  });

  protected readonly filtrosAtivos = computed(
    () =>
      this.filtroEspecialidade() != null ||
      this.filtroTipo() !== '' ||
      this.somenteAlertas() ||
      this.texto().trim() !== '',
  );

  /** Linha do tempo após aplicar todos os filtros. */
  protected readonly marcos = computed<MarcoTimeline[]>(() => {
    const espId = this.filtroEspecialidade();
    const tipo = this.filtroTipo();
    const soAlerta = this.somenteAlertas();
    const q = this.texto().trim().toLowerCase();

    return this.prontuarios()
      .filter((p) => espId == null || p.especialidade?.id === espId)
      .filter((p) => !soAlerta || p.statusAlerta === 'AGUARDANDO_VALIDACAO')
      .map<MarcoTimeline>((p) => ({
        prontuario: p,
        documentos: tipo ? p.documentos.filter((d) => (d.tipoNome?.trim() || 'Sem tipo') === tipo) : p.documentos,
      }))
      .filter((m) => !tipo || m.documentos.length > 0)
      .filter((m) => !q || this.textoDoMarco(m).includes(q));
  });

  constructor() {
    afterNextRender(() => {
      if (!Number.isFinite(this.pacienteId) || this.pacienteId <= 0) {
        this.erro.set(true);
        this.carregando.set(false);
        return;
      }
      this.carregar();
    });
  }

  private carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.service.historico(this.pacienteId).subscribe({
      next: (h) => {
        this.historico.set(h);
        this.resumoIa.set(h.resumoHistoricoIa);
        this.resumoGeradoEm.set(h.resumoHistoricoGeradoEm);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  /** String pesquisável de um marco (para o filtro por texto). */
  private textoDoMarco(m: MarcoTimeline): string {
    const p = m.prontuario;
    const partes: (string | null | undefined)[] = [
      p.numeroAtendimento,
      p.especialidade?.nome,
      p.profissionalSaude?.nome,
      p.unidadeSaude?.nome,
      p.statusAlertaDescricao,
    ];
    for (const d of m.documentos) partes.push(d.nome, d.tipoNome, d.resumoClinico);
    return partes.filter(Boolean).join(' ').toLowerCase();
  }

  protected aoMudarEspecialidade(event: Event): void {
    const v = (event.target as HTMLSelectElement).value;
    this.filtroEspecialidade.set(v ? Number(v) : null);
  }

  protected aoMudarTipo(event: Event): void {
    this.filtroTipo.set((event.target as HTMLSelectElement).value);
  }

  protected aoMudarTexto(event: Event): void {
    this.texto.set((event.target as HTMLInputElement).value);
  }

  protected alternarAlertas(): void {
    this.somenteAlertas.update((v) => !v);
  }

  protected limparFiltros(): void {
    this.filtroEspecialidade.set(null);
    this.filtroTipo.set('');
    this.somenteAlertas.set(false);
    this.texto.set('');
  }

  /** Minimiza/expande o card do resumo (preferência lembrada entre visitas). */
  protected alternarResumoMinimizado(): void {
    this.resumoMinimizado.update((v) => !v);
    this.salvarMinimizado(this.resumoMinimizado());
  }

  private lerMinimizado(): boolean {
    try {
      return localStorage.getItem('pm-resumo-minimizado') === '1';
    } catch {
      return false;
    }
  }

  private salvarMinimizado(v: boolean): void {
    try {
      localStorage.setItem('pm-resumo-minimizado', v ? '1' : '0');
    } catch {
      /* sem persistência: sem problema */
    }
  }

  /** Recolhe/expande o painel de resumo+atendimento no visor (o documento ocupa mais espaço). */
  protected alternarInfo(): void {
    this.infoMinimizada.update((v) => !v);
    try {
      localStorage.setItem('pm-visor-info-min', this.infoMinimizada() ? '1' : '0');
    } catch {
      /* sem persistência: sem problema */
    }
  }

  private lerInfoMin(): boolean {
    try {
      return localStorage.getItem('pm-visor-info-min') === '1';
    } catch {
      return false;
    }
  }

  protected ehAlerta(p: ProntuarioDetalhe): boolean {
    return p.statusAlerta === 'AGUARDANDO_VALIDACAO';
  }

  protected ehConfirmada(p: ProntuarioDetalhe): boolean {
    return p.statusAlerta === 'ALTERACAO_CONFIRMADA';
  }

  // --- Navegação entre documentos dentro do visor (setas ◀ ▶ / teclado) ---

  /** Todos os documentos visíveis (após filtros), achatados na ordem da linha do tempo. */
  protected readonly documentosVisiveis = computed<DocumentoAberto[]>(() =>
    this.marcos().flatMap((m) => m.documentos.map((documento) => ({ documento, prontuario: m.prontuario }))),
  );

  /** Índice do documento aberto na lista visível (-1 se nenhum / não encontrado). */
  protected readonly indiceDocAberto = computed<number>(() => {
    const aberto = this.docAberto();
    if (!aberto) return -1;
    return this.documentosVisiveis().findIndex((d) => d.documento.id === aberto.documento.id);
  });

  protected readonly totalDocsVisiveis = computed(() => this.documentosVisiveis().length);
  protected readonly temDocAnterior = computed(() => this.indiceDocAberto() > 0);
  protected readonly temDocProximo = computed(() => {
    const i = this.indiceDocAberto();
    return i >= 0 && i < this.documentosVisiveis().length - 1;
  });

  /** Vai para o documento anterior/seguinte no visor (delta = -1 ou +1); para nas pontas. */
  protected navegarDoc(delta: number): void {
    const lista = this.documentosVisiveis();
    const alvo = this.indiceDocAberto() + delta;
    if (this.indiceDocAberto() < 0 || alvo < 0 || alvo >= lista.length) return;
    const proximo = lista[alvo];
    this.abrirDocumento(proximo.documento, proximo.prontuario);
  }

  /** Setas ←/→ do teclado navegam quando o visor está aberto. */
  protected aoSetaTeclado(delta: number, event: Event): void {
    if (!this.docAberto()) return;
    event.preventDefault();
    this.navegarDoc(delta);
  }

  /** Tecla "R" recolhe/expande o painel de informações quando o visor está aberto. */
  protected aoTeclaR(event: Event): void {
    if (!this.docAberto()) return;
    event.preventDefault();
    this.alternarInfo();
  }

  // --- Tela cheia do visor ---

  /** Alterna o visor em tela cheia (ocupa toda a janela). */
  protected alternarFullscreen(): void {
    this.visorFullscreen.update((v) => !v);
  }

  /** Abre o visor embutido para um documento (busca a URL assinada). */
  protected async abrirDocumento(documento: DocumentoAdmin, prontuario: ProntuarioDetalhe): Promise<void> {
    this.docAberto.set({ documento, prontuario });
    this.observacaoValidacao.set(documento.observacaoValidacao ?? '');
    this.visorErro.set(false);
    this.urlSegura.set(null);
    this.urlAssinada.set(null);
    this.tipoArquivo.set(detectarTipoArquivo(documento.url ?? documento.nome));

    if (!documento.url) {
      this.visorCarregando.set(false);
      this.visorErro.set(true);
      return;
    }

    this.visorCarregando.set(true);
    try {
      const assinada = await this.storage.urlDownload(documento.url);
      this.urlAssinada.set(assinada);
      if (this.tipoArquivo() === 'pdf') {
        this.urlSegura.set(this.sanitizer.bypassSecurityTrustResourceUrl(assinada));
      }
    } catch {
      this.visorErro.set(true);
      this.toastr.error('Não foi possível carregar o arquivo.');
    } finally {
      this.visorCarregando.set(false);
    }
  }

  protected fecharVisor(): void {
    this.docAberto.set(null);
    this.urlSegura.set(null);
    this.urlAssinada.set(null);
    this.visorErro.set(false);
    this.visorFullscreen.set(false);
  }

  protected aoMudarObservacao(event: Event): void {
    this.observacaoValidacao.set((event.target as HTMLTextAreaElement).value);
  }

  /**
   * Registra a decisão humana do alerta do documento aberto (confirmar alteração ou marcar sem
   * alteração), direto no visor. Atualiza o estado local em tempo real e mantém o visor aberto.
   */
  protected decidir(decisao: DecisaoValidacao): void {
    const aberto = this.docAberto();
    if (!aberto || this.validando()) return;
    this.validando.set(true);
    this.prontuarioService.validarDocumento(aberto.documento.id, decisao, this.observacaoValidacao()).subscribe({
      next: (detalhe) => {
        this.aplicarProntuarioAtualizado(detalhe);
        this.validando.set(false);
        this.toastr.success(
          decisao === 'ALTERACAO_CONFIRMADA' ? 'Alteração confirmada' : 'Documento marcado sem alteração',
        );
      },
      error: () => {
        this.validando.set(false);
        this.toastr.error('Não foi possível registrar a validação.');
      },
    });
  }

  /**
   * Substitui o prontuário atualizado no histórico, recalcula o contador de alertas do cabeçalho e
   * refaz o documento aberto (para o painel refletir o novo status na hora) — sem recarregar a tela.
   */
  private aplicarProntuarioAtualizado(detalhe: ProntuarioDetalhe): void {
    const h = this.historico();
    if (!h) return;
    const prontuarios = h.prontuarios.map((p) => (p.id === detalhe.id ? detalhe : p));
    const alertasPendentes = prontuarios.filter((p) => p.statusAlerta === 'AGUARDANDO_VALIDACAO').length;
    this.historico.set({ ...h, prontuarios, paciente: { ...h.paciente, alertasPendentes } });

    const aberto = this.docAberto();
    if (aberto) {
      const atual = detalhe.documentos.find((d) => d.id === aberto.documento.id);
      if (atual) {
        this.docAberto.set({ documento: atual, prontuario: detalhe });
        this.observacaoValidacao.set(atual.observacaoValidacao ?? '');
      }
    }
  }

  protected aoEscape(): void {
    if (this.docAberto()) this.fecharVisor();
  }

  protected voltar(): void {
    this.router.navigate(['/prontuario-medico']);
  }
}
