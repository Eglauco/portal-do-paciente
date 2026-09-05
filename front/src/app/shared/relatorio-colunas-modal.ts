import { HttpClient } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';

/**
 * Modal de seleção de colunas do relatório, reutilizável por qualquer tela.
 * Busca os campos disponíveis em {@code {base}/exportar/colunas} (todos marcados),
 * permite selecionar/limpar e emite os rótulos escolhidos ao exportar.
 */
@Component({
  selector: 'app-relatorio-colunas-modal',
  templateUrl: './relatorio-colunas-modal.html',
})
export class RelatorioColunasModal implements OnInit {
  private readonly http = inject(HttpClient);

  /** URL base do recurso; as colunas vêm de {base}/exportar/colunas. */
  readonly base = input.required<string>();
  /** Formato escolhido (só para o texto do botão). */
  readonly formato = input.required<'xlsx' | 'pdf'>();

  /** Emite os rótulos das colunas selecionadas (na ordem definida) ao confirmar. */
  readonly exportar = output<string[]>();
  readonly fechar = output<void>();

  protected readonly colunas = signal<string[]>([]);
  protected readonly selecionadas = signal<ReadonlySet<string>>(new Set<string>());
  protected readonly carregando = signal(true);
  protected readonly erro = signal(false);

  protected readonly rotuloFormato = computed(() => (this.formato() === 'pdf' ? 'PDF' : 'Excel'));
  protected readonly todasMarcadas = computed(
    () => this.colunas().length > 0 && this.selecionadas().size === this.colunas().length,
  );
  protected readonly podeExportar = computed(() => this.selecionadas().size > 0);

  ngOnInit(): void {
    this.http.get<string[]>(`${this.base()}/exportar/colunas`).subscribe({
      next: (cols) => {
        this.colunas.set(cols);
        this.selecionadas.set(new Set<string>()); // começa sem nada marcado (o usuário escolhe)
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  protected marcada(coluna: string): boolean {
    return this.selecionadas().has(coluna);
  }

  protected alternar(coluna: string): void {
    this.selecionadas.update((atual) => {
      const nova = new Set(atual);
      if (nova.has(coluna)) nova.delete(coluna);
      else nova.add(coluna);
      return nova;
    });
  }

  protected alternarTodas(): void {
    this.selecionadas.set(this.todasMarcadas() ? new Set<string>() : new Set(this.colunas()));
  }

  protected confirmar(): void {
    if (!this.podeExportar()) return;
    this.exportar.emit(this.colunas().filter((c) => this.selecionadas().has(c)));
  }
}
