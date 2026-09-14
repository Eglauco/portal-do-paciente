import { DatePipe } from '@angular/common';
import { afterNextRender, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { RelatorioColunasModal } from '../../shared/relatorio-colunas-modal';
import { Ordenacao, alternarOrdenacao } from '../../shared/ordenacao/ordenacao.model';
import { Ordenavel } from '../../shared/ordenacao/ordenavel';
import { AuthService } from '../../core/auth.service';
import {
  Agendamento,
  AgendamentoFiltro,
  EstadoEntrega,
  STATUS_OPTIONS,
  StatusAgendamento,
  entregaLabel,
  statusLabel,
} from './agendamento.model';
import { AgendamentoBuscaStore } from './agendamento-busca.store';
import { AgendamentoService } from './agendamento.service';

export type PaginaItem = number | 'ellipsis';

/** Estados de entrega oferecidos no filtro "Notificação". */
const ESTADOS_ENTREGA: EstadoEntrega[] = [
  'NOTIFICACAO_ENTREGUE',
  'NOTIFICACAO_ENVIADA',
  'PACIENTE_SEM_APLICATIVO',
  'SEM_NOTIFICACAO_ATIVA',
];

@Component({
  selector: 'app-agendamentos-list',
  imports: [RouterLink, DatePipe, RelatorioColunasModal, Ordenavel],
  templateUrl: './agendamentos-list.html',
})
export class AgendamentosList {
  private readonly service = inject(AgendamentoService);
  private readonly router = inject(Router);
  private readonly store = inject(AgendamentoBuscaStore);
  private readonly auth = inject(AuthService);
  private readonly toastr = inject(ToastrService);

  protected readonly exportando = signal<'xlsx' | 'pdf' | null>(null);
  /** Formato escolhido enquanto o modal de colunas está aberto (null = fechado). */
  protected readonly formatoModal = signal<'xlsx' | 'pdf' | null>(null);
  protected readonly base = this.service.base;

  protected readonly tamanhos = AgendamentoService.TAMANHOS;
  protected readonly statusOpcoes = STATUS_OPTIONS;
  protected readonly rotuloStatus = statusLabel;
  protected readonly rotuloEntrega = entregaLabel;
  protected readonly entregaOpcoes = ESTADOS_ENTREGA.map((value) => ({ value, label: entregaLabel(value) }));

  // Filtros — iniciados do store (persistem ao sair e voltar).
  protected readonly status = signal<StatusAgendamento | null>(this.store.status);
  protected readonly nome = signal(this.store.nome);
  protected readonly especialidadeNome = signal(this.store.especialidadeNome);
  protected readonly profissionalNome = signal(this.store.profissionalNome);
  protected readonly entregaResumo = signal<EstadoEntrega | null>(this.store.entregaResumo);
  protected readonly data = signal(this.store.data);

  /**
   * Ordenação multi-coluna (vazia = padrão do backend: mais recentes primeiro). Diferente
   * dos filtros, a ordenação aplica IMEDIATAMENTE (não espera Pesquisar), como a paginação.
   */
  protected readonly ordenacoes = signal<Ordenacao[]>(this.store.ordenacoes);

  /**
   * Filtro efetivamente APLICADO à lista exibida (o que foi enviado ao servidor).
   * Separado dos signals do formulário (rascunho): a aplicação só acontece ao clicar
   * Pesquisar/Limpar. Paginação, tamanho de página e exportação usam ESTE snapshot,
   * para não vazar filtros digitados mas ainda não pesquisados. Inicia do store.
   */
  private readonly aplicado = signal<AgendamentoFiltro>({
    status: this.store.status,
    nome: this.store.nome.trim() || null,
    especialidadeNome: this.store.especialidadeNome.trim() || null,
    profissionalNome: this.store.profissionalNome.trim() || null,
    entregaResumo: this.store.entregaResumo,
    data: this.store.data || null,
  });

  protected readonly size = signal(this.store.size);
  protected readonly agendamentos = signal<Agendamento[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly carregado = signal(false);

  protected readonly page = signal(this.store.page);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly first = signal(true);
  protected readonly last = signal(true);

  protected readonly inicioFaixa = computed(() =>
    this.totalElements() === 0 ? 0 : this.page() * this.size() + 1,
  );
  protected readonly fimFaixa = computed(() => this.page() * this.size() + this.agendamentos().length);

  protected readonly paginasVisiveis = computed<PaginaItem[]>(() => {
    const total = this.totalPages();
    const atual = this.page();
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i);
    }
    const itens: PaginaItem[] = [0];
    const inicio = Math.max(1, atual - 1);
    const fim = Math.min(total - 2, atual + 1);
    if (inicio > 1) itens.push('ellipsis');
    for (let i = inicio; i <= fim; i++) itens.push(i);
    if (fim < total - 2) itens.push('ellipsis');
    itens.push(total - 1);
    return itens;
  });

  constructor() {
    afterNextRender(() => this.carregar());
  }

  protected updateNome(event: Event): void {
    this.nome.set((event.target as HTMLInputElement).value);
  }
  protected updateStatus(event: Event): void {
    const v = (event.target as HTMLSelectElement).value;
    this.status.set(v ? (v as StatusAgendamento) : null);
  }
  protected updateEspecialidade(event: Event): void {
    this.especialidadeNome.set((event.target as HTMLInputElement).value);
  }
  protected updateProfissional(event: Event): void {
    this.profissionalNome.set((event.target as HTMLInputElement).value);
  }
  protected updateEntrega(event: Event): void {
    const v = (event.target as HTMLSelectElement).value;
    this.entregaResumo.set(v ? (v as EstadoEntrega) : null);
  }
  protected updateData(event: Event): void {
    this.data.set((event.target as HTMLInputElement).value);
  }

  /** Aplica os filtros do formulário (congela o snapshot e volta à primeira página). */
  protected pesquisar(event?: Event): void {
    event?.preventDefault();
    this.aplicado.set(this.snapshotDoFormulario());
    this.page.set(0);
    this.carregar();
  }

  protected limpar(): void {
    this.store.limpar();
    this.status.set(null);
    this.nome.set('');
    this.especialidadeNome.set('');
    this.profissionalNome.set('');
    this.entregaResumo.set(null);
    this.data.set('');
    this.aplicado.set(this.snapshotDoFormulario());
    this.page.set(0);
    this.carregar();
  }

  /** Reexecuta a MESMA busca aplicada (retry do estado de erro, sem apagar filtros). */
  protected tentarNovamente(): void {
    this.carregar();
  }

  protected alterarTamanho(event: Event): void {
    this.size.set(Number((event.target as HTMLSelectElement).value));
    this.page.set(0);
    this.carregar();
  }

  protected irParaPagina(indice: number): void {
    if (indice < 0 || indice >= this.totalPages() || indice === this.page()) return;
    this.page.set(indice);
    this.carregar();
  }

  protected paginaAnterior(): void {
    if (!this.first()) this.irParaPagina(this.page() - 1);
  }

  protected proximaPagina(): void {
    if (!this.last()) this.irParaPagina(this.page() + 1);
  }

  /** Lê os filtros do FORMULÁRIO (rascunho). Só vira filtro aplicado em pesquisar()/limpar(). */
  private snapshotDoFormulario(): AgendamentoFiltro {
    return {
      status: this.status(),
      nome: this.nome().trim() || null,
      especialidadeNome: this.especialidadeNome().trim() || null,
      profissionalNome: this.profissionalNome().trim() || null,
      entregaResumo: this.entregaResumo(),
      data: this.data() || null,
    };
  }

  private carregar(): void {
    // Persiste o filtro APLICADO (não o rascunho) para restaurar ao voltar à tela.
    const filtro = this.aplicado();
    this.store.status = filtro.status;
    this.store.nome = filtro.nome ?? '';
    this.store.especialidadeNome = filtro.especialidadeNome ?? '';
    this.store.profissionalNome = filtro.profissionalNome ?? '';
    this.store.entregaResumo = filtro.entregaResumo;
    this.store.data = filtro.data ?? '';
    this.store.ordenacoes = this.ordenacoes();
    this.store.size = this.size();
    this.store.page = this.page();

    this.loading.set(true);
    this.error.set(false);
    this.service.listar(filtro, this.auth.unidadeId(), this.page(), this.size(), this.ordenacoes()).subscribe({
      next: (pagina) => {
        this.agendamentos.set(pagina.content);
        this.totalElements.set(pagina.totalElements);
        this.totalPages.set(pagina.totalPages);
        this.first.set(pagina.first);
        this.last.set(pagina.last);
        this.page.set(pagina.page);
        this.loading.set(false);
        this.carregado.set(true);
      },
      error: () => {
        this.agendamentos.set([]);
        this.error.set(true);
        this.loading.set(false);
        this.carregado.set(true);
      },
    });
  }

  protected editar(agendamento: Agendamento): void {
    this.router.navigate(['/agendamentos', agendamento.id]);
  }

  /** Clique num cabeçalho: cicla asc→desc→nenhuma; com Shift, combina com as demais colunas. */
  protected aoAlternar(e: { campo: string; combinar: boolean }): void {
    this.ordenacoes.set(alternarOrdenacao(this.ordenacoes(), e.campo, e.combinar));
    this.page.set(0);
    this.carregar();
  }

  /** Remove toda a ordenação (volta ao padrão do backend). */
  protected limparOrdenacao(): void {
    if (this.ordenacoes().length === 0) return;
    this.ordenacoes.set([]);
    this.page.set(0);
    this.carregar();
  }

  /** Abre o modal de seleção de colunas para o formato escolhido. */
  protected abrirExportacao(formato: 'xlsx' | 'pdf'): void {
    if (this.exportando()) return;
    this.formatoModal.set(formato);
  }

  /** Exporta com as colunas escolhidas no modal (mesmos filtros da tela). */
  protected confirmarExportacao(colunas: string[]): void {
    const formato = this.formatoModal();
    this.formatoModal.set(null);
    if (!formato) return;
    this.exportando.set(formato);
    // Exporta o filtro APLICADO (o que está na tela), não o rascunho ainda não pesquisado.
    this.service.exportar(formato, this.aplicado(), this.auth.unidadeId(), colunas, this.ordenacoes()).subscribe({
      next: (blob) => {
        this.baixar(blob, `agendamentos.${formato}`);
        this.exportando.set(null);
      },
      error: () => {
        this.exportando.set(null);
        this.toastr.error('Não foi possível exportar os dados.');
      },
    });
  }

  private baixar(blob: Blob, nome: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = nome;
    link.click();
    URL.revokeObjectURL(url);
  }
}
