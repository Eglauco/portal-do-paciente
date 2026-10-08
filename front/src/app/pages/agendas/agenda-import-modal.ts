import { Component, computed, inject, input, output, signal } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../core/auth.service';
import { AgendaImportPreview, CampoPreview } from './agenda-import.model';
import { AgendaImportService } from './agenda-import.service';

interface CampoAgenda {
  rotulo: string;
  campo: CampoPreview;
}

/**
 * Modal de importação de Agenda por Excel (Fase 1 — só preview; nada é gravado).
 * Baixa a planilha-modelo, sobe a planilha preenchida e mostra o preview com os erros destacados.
 * "Confirmar importação" existe porém fica DESABILITADO (a gravação vem na Fase 2).
 */
@Component({
  selector: 'app-agenda-import-modal',
  templateUrl: './agenda-import-modal.html',
  styles: [
    `
      .modal__overlay { position: fixed; inset: 0; z-index: 1000; background: rgba(0, 0, 0, 0.45); display: flex; align-items: flex-start; justify-content: center; padding: 3rem 1rem; overflow: auto; }
      .modal { width: 100%; max-width: 960px; background: var(--surface, #fff); color: var(--ink); border-radius: 0.8rem; box-shadow: 0 16px 48px rgba(0, 0, 0, 0.22); }
      .modal__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding: 1.1rem 1.25rem 0.5rem; }
      .modal__title { font-size: 1.1rem; font-weight: 700; margin: 0; }
      .modal__sub { font-size: 0.85rem; color: var(--muted); margin: 0.15rem 0 0; }
      .modal__x { border: 0; background: transparent; font-size: 1.4rem; line-height: 1; color: var(--muted); cursor: pointer; padding: 0 0.25rem; }
      .modal__body { padding: 0.5rem 1.25rem 1rem; }
      .modal__foot { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; padding: 0.75rem 1.25rem 1.1rem; border-top: 1px solid var(--line); }
      .modal__foot-actions { display: flex; gap: 0.5rem; }

      .passos { display: flex; flex-wrap: wrap; gap: 0.6rem; align-items: center; margin: 0.4rem 0 1rem; }
      .passos__sep { color: var(--muted); }

      .resumo { display: flex; flex-wrap: wrap; gap: 0.5rem 1rem; align-items: baseline; margin: 0.25rem 0 0.9rem; font-size: 0.9rem; }
      .pill { display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.2rem 0.65rem; border-radius: 999px; font-size: 0.8rem; font-weight: 600; }
      .pill--ok { color: #0a7a4b; background: color-mix(in srgb, #12b76a 15%, transparent); }
      .pill--erro { color: #b42318; background: color-mix(in srgb, #f04438 15%, transparent); }
      .arquivo { color: var(--muted); font-size: 0.82rem; }

      .section-label { margin: 1rem 0 0.4rem; }

      /* Cabeçalho da agenda em cartões de campo. */
      .agenda-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0.6rem; }
      .campo { border: 1px solid var(--line); border-radius: 0.6rem; padding: 0.55rem 0.7rem; }
      .campo__rot { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--muted); }
      .campo__val { font-size: 0.95rem; margin-top: 0.1rem; word-break: break-word; }
      .campo__resolvido { font-size: 0.78rem; color: var(--muted); margin-top: 0.1rem; }
      .campo__msg { font-size: 0.78rem; color: #b42318; margin-top: 0.2rem; }
      .campo.is-erro { border-color: #f04438; background: color-mix(in srgb, #f04438 7%, transparent); }

      /* Tabela de horários. */
      .table-wrap { overflow: auto; border: 1px solid var(--line); border-radius: 0.6rem; }
      .data-table { width: 100%; border-collapse: collapse; font-size: 0.88rem; }
      .data-table th, .data-table td { text-align: left; padding: 0.5rem 0.6rem; border-bottom: 1px solid var(--line); vertical-align: top; }
      .data-table thead th { background: color-mix(in srgb, var(--muted) 10%, transparent); font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.02em; color: var(--muted); }
      .data-table tr:last-child td { border-bottom: 0; }
      .col-linha { width: 3.2rem; color: var(--muted); }
      .cell__val { word-break: break-word; }
      .cell__resolvido { font-size: 0.76rem; color: var(--muted); margin-top: 0.1rem; }
      .cell__msg { font-size: 0.76rem; color: #b42318; margin-top: 0.15rem; }
      .cell.is-erro { background: color-mix(in srgb, #f04438 9%, transparent); box-shadow: inset 2px 0 0 #f04438; }
      .cell.is-erro .cell__val { color: #b42318; }
      .vazio { color: var(--muted); }

      .dropzone { border: 1px dashed var(--line); border-radius: 0.7rem; padding: 1.6rem 1rem; text-align: center; }
      .dropzone p { margin: 0.4rem 0 0; color: var(--muted); font-size: 0.86rem; }
      .foot-note { color: var(--muted); font-size: 0.8rem; }
    `,
  ],
})
export class AgendaImportModal {
  private readonly service = inject(AgendaImportService);
  private readonly toastr = inject(ToastrService);
  private readonly auth = inject(AuthService);

  /** Controla a exibição; o pai abre/fecha. */
  readonly aberto = input(false);
  readonly fechar = output<void>();

  protected readonly baixando = signal(false);
  protected readonly enviando = signal(false);
  protected readonly arquivoNome = signal<string | null>(null);
  protected readonly preview = signal<AgendaImportPreview | null>(null);

  /** Campos do cabeçalho da agenda, já rotulados, para iterar no template. */
  protected readonly agendaCampos = computed<CampoAgenda[]>(() => {
    const p = this.preview();
    if (!p) return [];
    const a = p.agenda;
    return [
      { rotulo: 'Data', campo: a.data },
      { rotulo: 'Profissional', campo: a.profissional },
      { rotulo: 'Especialidade', campo: a.especialidade },
      { rotulo: 'Configuração da Agenda', campo: a.configuracaoAgenda },
      { rotulo: 'Unidade executante', campo: a.unidade },
      { rotulo: 'Nome da agenda', campo: a.nome },
    ];
  });

  /** Baixa a planilha de exemplo. */
  protected baixarModelo(): void {
    if (this.baixando()) return;
    this.baixando.set(true);
    this.service.baixarModelo().subscribe({
      next: (blob) => {
        this.baixar(blob, 'modelo-importacao-agenda.xlsx');
        this.baixando.set(false);
      },
      error: () => {
        this.baixando.set(false);
        this.toastr.error('Não foi possível baixar a planilha de exemplo.');
      },
    });
  }

  /** Abre o seletor de arquivo (input escondido no template). */
  protected escolherArquivo(input: HTMLInputElement): void {
    if (this.enviando()) return;
    input.value = '';
    input.click();
  }

  protected aoSelecionarArquivo(event: Event): void {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    if (!arquivo) return;
    this.arquivoNome.set(arquivo.name);
    this.enviando.set(true);
    this.service.preview(arquivo, this.auth.unidadeId()).subscribe({
      next: (p) => {
        this.preview.set(p);
        this.enviando.set(false);
      },
      error: (e) => {
        this.preview.set(null);
        this.enviando.set(false);
        this.toastr.error(e?.error?.message ?? 'Não foi possível ler a planilha.');
      },
    });
  }

  protected limparPreview(): void {
    this.preview.set(null);
    this.arquivoNome.set(null);
  }

  protected fecharModal(): void {
    if (this.enviando()) return;
    this.limparPreview();
    this.fechar.emit();
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
