import { DatePipe } from '@angular/common';
import { Component, afterNextRender, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../core/auth.service';
import {
  AcaoAtualizacao,
  SirespConfigCampo,
  SirespProcedimentoOpcao,
  SirespResumo,
  SirespStatus,
} from './siresp.model';
import { SirespService } from './siresp.service';

export type PaginaItem = number | 'ellipsis';

/** Tela SIRESP: lista (só leitura) dos registros importados do CROSS + botão para importar um XML. */
@Component({
  selector: 'app-siresp-list',
  imports: [ReactiveFormsModule, DatePipe, RouterLink],
  templateUrl: './siresp-list.html',
  styles: [
    `
      .modal__overlay { position: fixed; inset: 0; z-index: 1000; background: rgba(0, 0, 0, 0.45); display: flex; align-items: flex-start; justify-content: center; padding: 3rem 1rem; overflow: auto; }
      .modal { width: 100%; max-width: 640px; background: var(--surface, #fff); color: var(--ink); border-radius: 0.8rem; box-shadow: 0 16px 48px rgba(0, 0, 0, 0.22); }
      .modal__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.1rem 1.25rem 0.5rem; }
      .modal__title { font-size: 1.1rem; font-weight: 700; margin: 0; }
      .modal__sub { font-size: 0.85rem; color: var(--muted); margin: 0.15rem 0 0; }
      .modal__x { border: 0; background: transparent; font-size: 1.4rem; line-height: 1; color: var(--muted); cursor: pointer; padding: 0 0.25rem; }
      .modal__body { padding: 0.5rem 1.25rem 1rem; }
      .modal__foot { display: flex; justify-content: flex-end; gap: 0.5rem; padding: 0.75rem 1.25rem 1.1rem; border-top: 1px solid var(--line); }
      .geral { display: flex; align-items: center; gap: 0.6rem; padding: 0.7rem 0.8rem; border: 1px solid var(--line); border-radius: 0.55rem; margin: 0.4rem 0 1rem; }
      .geral input { width: 1.1rem; height: 1.1rem; }
      .geral__txt b { display: block; font-size: 0.92rem; }
      .geral__txt span { font-size: 0.8rem; color: var(--muted); }
      .campos { display: flex; flex-direction: column; }
      .campo-linha { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 0.5rem 0; border-bottom: 1px solid var(--line); }
      .campo-linha:last-child { border-bottom: 0; }
      .campo-linha__rot { font-size: 0.9rem; }
      .campo-linha select { min-width: 190px; }
      .campos--off { opacity: 0.6; }

      /* Coluna "Ver" fixa à direita: fica sempre visível mesmo se a tabela rolar na horizontal. */
      .data-table th.col-act,
      .data-table td.col-act { position: sticky; right: 0; background: var(--surface, #fff); }

      /* Badge de status do registro (agendado = verde / revisão = âmbar). */
      .status-badge { display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.2rem 0.6rem; border-radius: 999px; font-size: 0.78rem; font-weight: 600; white-space: nowrap; line-height: 1.4; }
      .status-badge::before { content: ''; width: 0.5rem; height: 0.5rem; border-radius: 50%; background: currentColor; flex: none; }
      .status-badge--ok { color: #0a7a4b; background: color-mix(in srgb, #12b76a 15%, transparent); }
      .status-badge--rev { color: #9a6a00; background: color-mix(in srgb, #e6a700 20%, transparent); }
    `,
  ],
})
export class SirespList {
  private readonly service = inject(SirespService);
  private readonly auth = inject(AuthService);
  private readonly toastr = inject(ToastrService);

  protected readonly tamanhos = SirespService.TAMANHOS;
  protected readonly importando = signal(false);

  // --- Modal de configuração (atualização do paciente) ---
  protected readonly modalAberto = signal(false);
  protected readonly carregandoConfig = signal(false);
  protected readonly salvandoConfig = signal(false);
  protected readonly habilitado = signal(false);
  protected readonly criar = signal(false);
  protected readonly postUrl = signal('');
  protected readonly enviarAoImportar = signal(false);
  protected readonly procedimentoPadraoId = signal<number | null>(null);
  protected readonly procedimentos = signal<SirespProcedimentoOpcao[]>([]);
  protected readonly campos = signal<SirespConfigCampo[]>([]);
  protected readonly acoesOpcoes: { valor: AcaoAtualizacao; rotulo: string }[] = [
    { valor: 'SEMPRE', rotulo: 'Sempre atualizar' },
    { valor: 'SE_VAZIO', rotulo: 'Só preencher se vazio' },
    { valor: 'NUNCA', rotulo: 'Nunca' },
  ];

  protected readonly filtro = new FormGroup({
    busca: new FormControl<string>('', { nonNullable: true }),
    status: new FormControl<SirespStatus | ''>('', { nonNullable: true }),
  });

  protected readonly size = signal(SirespService.TAMANHO_PADRAO);
  protected readonly registros = signal<SirespResumo[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal(false);
  protected readonly carregado = signal(false);

  protected readonly page = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly first = signal(true);
  protected readonly last = signal(true);

  protected readonly inicioFaixa = computed(() =>
    this.totalElements() === 0 ? 0 : this.page() * this.size() + 1,
  );
  protected readonly fimFaixa = computed(() => this.page() * this.size() + this.registros().length);

  protected readonly paginasVisiveis = computed<PaginaItem[]>(() => {
    const total = this.totalPages();
    const atual = this.page();
    if (total <= 7) return Array.from({ length: total }, (_, i) => i);
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

  protected buscar(): void {
    this.page.set(0);
    this.carregar();
  }

  protected limpar(): void {
    this.filtro.reset({ busca: '', status: '' });
    this.page.set(0);
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

  /** Dispara o seletor de arquivo (input escondido no template). */
  protected escolherArquivo(input: HTMLInputElement): void {
    if (this.importando()) return;
    input.value = '';
    input.click();
  }

  protected aoSelecionarArquivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    if (!arquivo) return;
    this.importando.set(true);
    this.service.importar(arquivo).subscribe({
      next: (r) => {
        this.importando.set(false);
        const partes: string[] = [];
        if (r.pacientesCriados > 0) partes.push(`${r.pacientesCriados} paciente(s) criado(s)`);
        if (r.pacientesAtualizados > 0) partes.push(`${r.pacientesAtualizados} atualizado(s)`);
        const extra = partes.length ? ` ${partes.join(', ')}.` : '';
        this.toastr.success(`${r.importados} registro(s) importado(s).${extra}`);
        if (r.enviado) {
          if (r.envioSucesso) {
            this.toastr.success('XML enviado ao cliente — ' + (r.envioMensagem ?? ''));
          } else {
            this.toastr.error('Falha ao enviar ao cliente — ' + (r.envioMensagem ?? ''));
          }
        }
        this.page.set(0);
        this.carregar();
      },
      error: (e) => {
        this.importando.set(false);
        this.toastr.error(e?.error?.message ?? 'Não foi possível importar o XML.');
      },
    });
  }

  // --- Configuração (modal) ---

  protected abrirConfig(): void {
    this.modalAberto.set(true);
    this.carregandoConfig.set(true);
    this.service.lerConfig().subscribe({
      next: (c) => {
        this.habilitado.set(c.habilitado);
        this.criar.set(c.criar);
        this.postUrl.set(c.postUrl ?? '');
        this.enviarAoImportar.set(c.enviarAoImportar);
        this.procedimentoPadraoId.set(c.procedimentoPadraoId ?? null);
        this.procedimentos.set(c.procedimentos ?? []);
        this.campos.set(c.campos);
        this.carregandoConfig.set(false);
      },
      error: () => {
        this.carregandoConfig.set(false);
        this.modalAberto.set(false);
        this.toastr.error('Não foi possível carregar a configuração.');
      },
    });
  }

  protected fecharConfig(): void {
    if (!this.salvandoConfig()) this.modalAberto.set(false);
  }

  protected setProcedimento(valor: string): void {
    this.procedimentoPadraoId.set(valor ? Number(valor) : null);
  }

  protected setAcao(campo: string, acao: string): void {
    this.campos.update((lista) =>
      lista.map((c) => (c.campo === campo ? { ...c, acao: acao as AcaoAtualizacao } : c)),
    );
  }

  protected salvarConfig(): void {
    const campos: Record<string, AcaoAtualizacao> = {};
    for (const c of this.campos()) campos[c.campo] = c.acao;
    this.salvandoConfig.set(true);
    this.service
      .salvarConfig({
        habilitado: this.habilitado(),
        criar: this.criar(),
        postUrl: this.postUrl().trim() || null,
        enviarAoImportar: this.enviarAoImportar(),
        procedimentoPadraoId: this.procedimentoPadraoId(),
        campos,
      })
      .subscribe({
      next: (c) => {
        this.habilitado.set(c.habilitado);
        this.criar.set(c.criar);
        this.postUrl.set(c.postUrl ?? '');
        this.enviarAoImportar.set(c.enviarAoImportar);
        this.procedimentoPadraoId.set(c.procedimentoPadraoId ?? null);
        this.procedimentos.set(c.procedimentos ?? []);
        this.campos.set(c.campos);
        this.salvandoConfig.set(false);
        this.modalAberto.set(false);
        this.toastr.success('Configuração salva.');
      },
      error: () => {
        this.salvandoConfig.set(false);
        this.toastr.error('Não foi possível salvar.');
      },
    });
  }

  private carregar(): void {
    const bruto = this.filtro.getRawValue();
    const filtro = { busca: bruto.busca || null, status: bruto.status || null };
    this.loading.set(true);
    this.error.set(false);
    this.service.listar(filtro, this.auth.unidadeId(), this.page(), this.size()).subscribe({
      next: (pagina) => {
        this.registros.set(pagina.content);
        this.totalElements.set(pagina.totalElements);
        this.totalPages.set(pagina.totalPages);
        this.first.set(pagina.first);
        this.last.set(pagina.last);
        this.page.set(pagina.page);
        this.loading.set(false);
        this.carregado.set(true);
      },
      error: () => {
        this.registros.set([]);
        this.error.set(true);
        this.loading.set(false);
        this.carregado.set(true);
      },
    });
  }
}
