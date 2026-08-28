import { DatePipe } from '@angular/common';
import { afterNextRender, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { firstValueFrom } from 'rxjs';
import { PodeSair } from '../../core/pending-changes.guard';
import { StorageService } from '../prontuarios/storage.service';
import { Unidade } from '../unidades/unidade.model';
import { UnidadeService } from '../unidades/unidade.service';
import { Imagem45 } from './imagem-45';
import { Comentario, PostagemRequest } from './postagem.model';
import { PostagemService } from './postagem.service';

/** Recorta a imagem para a proporção 4:5 (largura:altura), gerando um JPEG. */
async function recortar45(arquivo: File): Promise<Blob> {
  const bitmap = await createImageBitmap(arquivo);
  const alvo = 4 / 5; // largura / altura
  const atual = bitmap.width / bitmap.height;
  let sx = 0;
  let sy = 0;
  let sw = bitmap.width;
  let sh = bitmap.height;
  if (atual > alvo) {
    // imagem larga demais → corta as laterais
    sw = bitmap.height * alvo;
    sx = (bitmap.width - sw) / 2;
  } else {
    // imagem alta demais → corta topo e base
    sh = bitmap.width / alvo;
    sy = (bitmap.height - sh) / 2;
  }
  const outW = 1080;
  const outH = 1350;
  const canvas = document.createElement('canvas');
  canvas.width = outW;
  canvas.height = outH;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('canvas indisponível');
  ctx.drawImage(bitmap, sx, sy, sw, sh, 0, 0, outW, outH);
  return new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('falha ao gerar imagem'))), 'image/jpeg', 0.9),
  );
}

@Component({
  selector: 'app-postagem-form',
  imports: [ReactiveFormsModule, NgSelectModule, DatePipe, Imagem45],
  templateUrl: './postagem-form.html',
})
export class PostagemForm implements PodeSair {
  private readonly service = inject(PostagemService);
  private readonly unidadeService = inject(UnidadeService);
  private readonly storage = inject(StorageService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly unidades = signal<Unidade[]>([]);

  protected readonly form = new FormGroup({
    titulo: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    descricao: new FormControl('', { nonNullable: true }),
    mostrarTotalCurtidas: new FormControl(true, { nonNullable: true }),
    habilitarComentarios: new FormControl(true, { nonNullable: true }),
    unidadeSaudeId: new FormControl<number | null>(null, { validators: [Validators.required] }),
  });

  protected readonly editando = signal(false);
  protected readonly codigo = signal<number | null>(null);
  protected readonly salvando = signal(false);
  protected readonly excluindo = signal(false);
  protected readonly erroCarregar = signal(false);
  protected readonly processandoImagem = signal(false);
  protected readonly previewUrl = signal<string | null>(null);
  protected readonly imagemTocada = signal(false);

  private imagemBlob: Blob | null = null;
  private urlAtual: string | null = null;

  // Curtidas e comentários (edição)
  protected readonly totalCurtidas = signal(0);
  protected readonly totalComentarios = signal(0);
  protected readonly comentarios = signal<Comentario[]>([]);
  protected readonly carregandoComentarios = signal(false);
  protected readonly temMaisComentarios = signal(false);
  protected readonly excluindoComentario = signal<number | null>(null);
  private pageComentarios = 0;

  // Responder comentários (administração)
  private readonly AUTOR_ADMIN = 'Administração';
  protected readonly respondendoId = signal<number | null>(null);
  protected readonly textoResposta = signal('');
  protected readonly enviandoResposta = signal(false);

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
      if (this.editando()) this.carregarPostagem();
    });
  }

  podeSair(): boolean | Promise<boolean> {
    if (this.saidaAutorizada || (!this.form.dirty && !this.imagemBlob)) return true;
    return this.confirmar('Existe dados preenchido na tela, deseja sair?');
  }

  protected invalido(campo: 'titulo' | 'unidadeSaudeId'): boolean {
    const c = this.form.controls[campo];
    return c.invalid && (c.touched || c.dirty);
  }

  protected temImagem(): boolean {
    return !!this.imagemBlob || !!this.urlAtual;
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const a = partes[0]?.charAt(0) ?? '';
    const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (a + b).toUpperCase();
  }

  protected async aoSelecionarImagem(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const arquivo = input.files?.[0];
    input.value = '';
    this.imagemTocada.set(true);
    if (!arquivo) return;
    if (!arquivo.type.startsWith('image/')) {
      this.toastr.error('Selecione um arquivo de imagem.');
      return;
    }
    this.processandoImagem.set(true);
    try {
      const blob = await recortar45(arquivo);
      const anterior = this.previewUrl();
      this.imagemBlob = blob;
      this.previewUrl.set(URL.createObjectURL(blob));
      if (anterior?.startsWith('blob:')) URL.revokeObjectURL(anterior);
      this.form.markAsDirty();
    } catch {
      this.toastr.error('Não foi possível processar a imagem.');
    } finally {
      this.processandoImagem.set(false);
    }
  }

  protected salvar(event: Event): void {
    event.preventDefault();
    this.imagemTocada.set(true);
    if (this.form.invalid || !this.temImagem()) {
      this.form.markAllAsTouched();
      return;
    }
    this.salvando.set(true);
    void this.enviar();
  }

  private async enviar(): Promise<void> {
    try {
      let url = this.urlAtual;
      if (this.imagemBlob) {
        const arquivo = new File([this.imagemBlob], 'postagem.jpg', { type: 'image/jpeg' });
        url = await this.storage.enviar(arquivo);
      }
      const v = this.form.getRawValue();
      const dados: PostagemRequest = {
        titulo: v.titulo.trim(),
        descricao: v.descricao.trim() || null,
        mostrarTotalCurtidas: v.mostrarTotalCurtidas,
        habilitarComentarios: v.habilitarComentarios,
        unidadeSaudeId: v.unidadeSaudeId!,
        url: url!,
      };
      const requisicao = this.editando()
        ? this.service.atualizar(this.codigo()!, dados)
        : this.service.criar(dados);
      await firstValueFrom(requisicao);
      this.saidaAutorizada = true;
      this.toastr.success('Postagem salva');
      this.router.navigate(['/postagens']);
    } catch {
      this.salvando.set(false);
      this.toastr.error('Não foi possível salvar a postagem.');
    }
  }

  protected async excluir(): Promise<void> {
    if (!this.editando() || this.codigo() == null) return;
    const confirmado = await this.confirmar('Deseja excluir esta postagem?');
    if (!confirmado) return;
    this.excluindo.set(true);
    this.service.excluir(this.codigo()!).subscribe({
      next: () => {
        this.saidaAutorizada = true;
        this.toastr.success('Postagem excluída');
        this.router.navigate(['/postagens']);
      },
      error: () => {
        this.excluindo.set(false);
        this.toastr.error('Não foi possível excluir a postagem.');
      },
    });
  }

  protected cancelar(): void {
    this.router.navigate(['/postagens']);
  }

  private carregarOpcoes(): void {
    this.unidadeService.listar({}, 0, 100).subscribe({ next: (p) => this.unidades.set(p.content) });
  }

  private carregarPostagem(): void {
    this.service.buscarPorId(this.codigo()!).subscribe({
      next: (p) => {
        this.form.patchValue({
          titulo: p.titulo,
          descricao: p.descricao ?? '',
          mostrarTotalCurtidas: p.mostrarTotalCurtidas,
          habilitarComentarios: p.habilitarComentarios,
          unidadeSaudeId: p.unidadeSaude.id,
        });
        this.urlAtual = p.url;
        this.previewUrl.set(p.url);
        this.totalCurtidas.set(p.totalCurtidas);
        this.totalComentarios.set(p.totalComentarios);
        this.form.markAsPristine();
        this.carregarComentarios(0);
      },
      error: () => this.erroCarregar.set(true),
    });
  }

  private carregarComentarios(page: number): void {
    if (this.codigo() == null) return;
    this.carregandoComentarios.set(true);
    this.service.listarComentarios(this.codigo()!, page, 20).subscribe({
      next: (pagina) => {
        this.comentarios.update((atual) => (page === 0 ? pagina.content : [...atual, ...pagina.content]));
        this.temMaisComentarios.set(!pagina.last);
        this.pageComentarios = page;
        this.carregandoComentarios.set(false);
      },
      error: () => this.carregandoComentarios.set(false),
    });
  }

  protected carregarMaisComentarios(): void {
    if (this.carregandoComentarios() || !this.temMaisComentarios()) return;
    this.carregarComentarios(this.pageComentarios + 1);
  }

  protected async excluirComentario(comentario: Comentario): Promise<void> {
    const confirmado = await this.confirmar('Deseja excluir este comentário e suas respostas?');
    if (!confirmado) return;
    this.excluindoComentario.set(comentario.id);
    this.service.excluirComentario(comentario.id).subscribe({
      next: () => {
        const removidos = 1 + (comentario.respostas?.length ?? 0); // comentário + respostas (cascade)
        this.comentarios.update((lista) => lista.filter((c) => c.id !== comentario.id));
        this.totalComentarios.update((n) => Math.max(0, n - removidos));
        this.excluindoComentario.set(null);
        this.toastr.success('Comentário excluído');
      },
      error: () => {
        this.excluindoComentario.set(null);
        this.toastr.error('Não foi possível excluir o comentário.');
      },
    });
  }

  protected async excluirResposta(raiz: Comentario, resposta: Comentario): Promise<void> {
    const confirmado = await this.confirmar('Deseja excluir esta resposta?');
    if (!confirmado) return;
    this.excluindoComentario.set(resposta.id);
    this.service.excluirComentario(resposta.id).subscribe({
      next: () => {
        this.comentarios.update((lista) =>
          lista.map((c) =>
            c.id === raiz.id ? { ...c, respostas: c.respostas.filter((r) => r.id !== resposta.id) } : c,
          ),
        );
        this.totalComentarios.update((n) => Math.max(0, n - 1));
        this.excluindoComentario.set(null);
        this.toastr.success('Resposta excluída');
      },
      error: () => {
        this.excluindoComentario.set(null);
        this.toastr.error('Não foi possível excluir a resposta.');
      },
    });
  }

  protected abrirResposta(raizId: number): void {
    this.respondendoId.set(raizId);
    this.textoResposta.set('');
  }

  protected cancelarResposta(): void {
    this.respondendoId.set(null);
    this.textoResposta.set('');
  }

  protected aoDigitarResposta(evento: Event): void {
    this.textoResposta.set((evento.target as HTMLTextAreaElement).value);
  }

  protected enviarResposta(raiz: Comentario): void {
    const texto = this.textoResposta().trim();
    if (!texto || this.enviandoResposta()) return;
    this.enviandoResposta.set(true);
    this.service.responderComentario(raiz.id, this.AUTOR_ADMIN, texto).subscribe({
      next: (resposta) => {
        this.comentarios.update((lista) =>
          lista.map((c) => (c.id === raiz.id ? { ...c, respostas: [...c.respostas, resposta] } : c)),
        );
        this.totalComentarios.update((n) => n + 1);
        this.respondendoId.set(null);
        this.textoResposta.set('');
        this.enviandoResposta.set(false);
      },
      error: () => {
        this.enviandoResposta.set(false);
        this.toastr.error('Não foi possível enviar a resposta.');
      },
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
