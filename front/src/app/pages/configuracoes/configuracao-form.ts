import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { PodeSair } from '../../core/pending-changes.guard';
import { PaletaTema, TemaService } from '../../core/tema.service';
import { StorageService } from '../prontuarios/storage.service';
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
      .img-config { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
      .img-config__preview {
        display: grid; place-items: center; width: 8rem; height: 5rem; padding: 0.5rem;
        border: 1px solid var(--line); border-radius: 0.6rem; background: #f7faf9;
      }
      .img-config__preview img { max-width: 100%; max-height: 100%; object-fit: contain; }
      .img-config__vazio {
        display: grid; place-items: center; width: 8rem; height: 5rem;
        border: 1px dashed var(--line); border-radius: 0.6rem; color: var(--muted); font-size: 0.85rem;
      }
      .img-config__acoes { display: flex; gap: 0.5rem; flex-wrap: wrap; }
      .img-config__acoes .btn { cursor: pointer; }
      .img-config__acoes .is-disabled { opacity: 0.6; pointer-events: none; }
    `,
  ],
})
export class ConfiguracaoForm implements PodeSair, OnDestroy {
  private readonly service = inject(ConfiguracaoService);
  private readonly temaService = inject(TemaService);
  private readonly storage = inject(StorageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  /** Paleta derivada da cor escolhida (preview do tipo COR). */
  protected readonly preview = signal<PaletaTema | null>(null);

  /** URL para exibir a imagem atual (tipo IMAGEM): presigned (salva) ou blob local (recém-enviada). */
  protected readonly imagemPreview = signal<string | null>(null);
  protected readonly enviandoImagem = signal(false);

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
    valorImagem: new FormControl<string | null>(null),
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
          valorImagem: c.valorImagem ?? null,
        });
        if (c.tipoConfiguracao === 'COR') {
          this.atualizarPreview(c.valorCor ?? '#0E8C7F');
        }
        if (c.tipoConfiguracao === 'IMAGEM' && c.valorImagem) {
          // Resolve uma URL assinada para exibir a imagem já salva (o bucket é privado).
          this.storage.urlDownload(c.valorImagem).then(
            (url) => this.imagemPreview.set(url),
            () => this.imagemPreview.set(null),
          );
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
          : tipo === 'IMAGEM'
            ? 'Imagem'
            : 'Texto';
  }

  /** Sobe a imagem escolhida ao S3 (pasta "configuracao") e guarda a URL no formulário. */
  protected async aoSelecionarImagem(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0] ?? null;
    input.value = ''; // permite re-selecionar o mesmo arquivo
    if (!arquivo) return;
    if (!arquivo.type.startsWith('image/')) {
      this.toastr.error('Selecione um arquivo de imagem.');
      return;
    }
    this.enviandoImagem.set(true);
    try {
      const url = await this.storage.enviar(arquivo, 'configuracao');
      this.form.controls.valorImagem.setValue(url);
      this.form.markAsDirty();
      this.revogarPreviewBlob();
      this.imagemPreview.set(URL.createObjectURL(arquivo)); // preview instantâneo (blob local)
    } catch {
      this.toastr.error('Não foi possível enviar a imagem.');
    } finally {
      this.enviandoImagem.set(false);
    }
  }

  /** Remove a imagem (volta ao padrão/SVG ao salvar). */
  protected removerImagem(): void {
    this.form.controls.valorImagem.setValue(null);
    this.form.markAsDirty();
    this.revogarPreviewBlob();
    this.imagemPreview.set(null);
  }

  /** Libera o blob local do preview (se houver) para não vazar memória. */
  private revogarPreviewBlob(): void {
    const atual = this.imagemPreview();
    if (atual?.startsWith('blob:')) URL.revokeObjectURL(atual);
  }

  ngOnDestroy(): void {
    this.revogarPreviewBlob();
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
            : c.tipoConfiguracao === 'IMAGEM'
              ? { valorImagem: v.valorImagem }
              : { valorTexto: v.valorTexto };
    this.salvando.set(true);
    // Ao trocar/remover a imagem, remove o objeto antigo do S3 (evita órfãos na pasta "configuracao").
    const imagemAntiga = c.tipoConfiguracao === 'IMAGEM' ? c.valorImagem : null;
    this.service.atualizar(this.codigo()!, dados).subscribe({
      next: () => {
        if (imagemAntiga && imagemAntiga !== v.valorImagem) {
          this.storage.excluir(imagemAntiga).catch(() => undefined);
        }
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
