import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { PaletaTema, TemaService } from '../../core/tema.service';
import { Configuracao, ConfiguracaoValor, TipoConfiguracao } from './configuracao.model';
import { ConfiguracaoService } from './configuracao.service';

@Component({
  selector: 'app-configuracao-form',
  imports: [ReactiveFormsModule],
  templateUrl: './configuracao-form.html',
  styles: [
    `
      .cor-pick { display: flex; align-items: center; gap: 0.75rem; }
      .cor-pick__input {
        width: 3.5rem; height: 2.6rem; padding: 0; cursor: pointer;
        border: 1px solid var(--line); border-radius: 0.5rem; background: none;
      }
      .cor-preview { display: flex; gap: 0.5rem; margin-top: 0.25rem; }
      .cor-swatch {
        width: 3.25rem; height: 2.6rem; border-radius: 0.5rem;
        border: 1px solid var(--line); display: grid; place-items: center;
      }
      .cor-swatch b { font-size: 0.95rem; font-weight: 700; }
    `,
  ],
})
export class ConfiguracaoForm implements PodeSair {
  private readonly service = inject(ConfiguracaoService);
  private readonly temaService = inject(TemaService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  /** Paleta derivada da cor escolhida (preview do tipo COR). */
  protected readonly preview = signal<PaletaTema | null>(null);

  /** Config carregada (nome/chave/tipo/descrição ficam read-only na tela). */
  protected readonly config = signal<Configuracao | null>(null);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly erroCarregar = signal(false);

  /** Só o valor é editável; o servidor aplica o campo do tipo do registro. */
  protected readonly form = new FormGroup({
    valorBooleano: new FormControl(false, { nonNullable: true }),
    valorTexto: new FormControl('', { nonNullable: true }),
    valorNumerico: new FormControl<number | null>(null),
    valorCor: new FormControl('#0E8C7F', { nonNullable: true }),
  });

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.erroCarregar.set(true);
      return;
    }
    const id = Number(idParam);
    this.codigo.set(id);
    this.service.buscarPorId(id).subscribe({
      next: (c) => {
        this.config.set(c);
        this.form.patchValue({
          valorBooleano: c.valorBooleano ?? false,
          valorTexto: c.valorTexto ?? '',
          valorNumerico: c.valorNumerico ?? null,
          valorCor: c.valorCor ?? '#0E8C7F',
        });
        if (c.tipoConfiguracao === 'COR') {
          this.atualizarPreview(c.valorCor ?? '#0E8C7F');
        }
      },
      error: () => this.erroCarregar.set(true),
    });
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || !this.form.dirty) return true;
    return this.confirmar('Existe alteração não salva na tela, deseja sair?');
  }

  /** Rótulo amigável do tipo. */
  protected rotuloTipo(tipo: TipoConfiguracao): string {
    return tipo === 'BOOLEANO'
      ? 'Sim/Não'
      : tipo === 'NUMERICO'
        ? 'Numérico'
        : tipo === 'COR'
          ? 'Cor'
          : 'Texto';
  }

  private ultimaCorPreview = '';

  /** Busca a paleta derivada da cor (preview) — só reflete depois de salvar. */
  protected atualizarPreview(cor: string): void {
    if (!/^#[0-9a-fA-F]{6}$/.test(cor)) {
      return;
    }
    this.ultimaCorPreview = cor;
    this.temaService.preview(cor).subscribe({
      // Ignora resposta fora de ordem (cor mudou de novo antes desta chegar).
      next: (p) => {
        if (this.ultimaCorPreview === cor) {
          this.preview.set(p);
        }
      },
      error: () => {
        if (this.ultimaCorPreview === cor) {
          this.preview.set(null);
        }
      },
    });
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    const c = this.config();
    if (!c || this.codigo() == null) return;
    const v = this.form.getRawValue();
    const dados: ConfiguracaoValor =
      c.tipoConfiguracao === 'BOOLEANO'
        ? { valorBooleano: v.valorBooleano }
        : c.tipoConfiguracao === 'NUMERICO'
          ? { valorNumerico: v.valorNumerico }
          : c.tipoConfiguracao === 'COR'
            ? { valorCor: v.valorCor }
            : { valorTexto: v.valorTexto };
    this.salvando.set(true);
    this.service.atualizar(this.codigo()!, dados).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Configuração salva');
        this.router.navigate(['/configuracoes']);
      },
      error: () => {
        this.salvando.set(false);
        this.toastr.error('Não foi possível salvar a configuração.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/configuracoes']);
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
