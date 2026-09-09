import { DatePipe } from '@angular/common';
import { afterNextRender, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { NgSelectModule } from '@ng-select/ng-select';
import { ToastrService } from 'ngx-toastr';
import { firstValueFrom, Observable } from 'rxjs';
import { AuthService } from '../../core/auth.service';
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
  private readonly auth = inject(AuthService);

  protected readonly unidades = signal<Unidade[]>([]);
  /** Unidade logada — a postagem é sempre publicada na unidade ativa. */
  protected readonly unidadeNome = this.auth.unidadeNome;

  protected readonly form = new FormGroup({
    titulo: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    descricao: new FormControl('', { nonNullable: true }),
    mostrarTotalCurtidas: new FormControl(true, { nonNullable: true }),
    habilitarComentarios: new FormControl(true, { nonNullable: true }),
    validarComentariosIa: new FormControl(false, { nonNullable: true }),
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
  /** Referência de "visto" (ISO) devolvida ao abrir: comentários posteriores são novos. */
  protected readonly comentariosVistosEm = signal<string | null>(null);
  /** Índice do próximo comentário novo para o atalho "ir para o próximo". */
  private readonly indiceProximoNovo = signal(0);

  /** Ids (na ordem de exibição) dos comentários novos — para destacar e navegar. */
  protected readonly idsNovos = computed<string[]>(() => {
    const ref = this.comentariosVistosEm();
    const ids: string[] = [];
    for (const c of this.comentarios()) {
      if (this.ehNovo(c, ref)) ids.push('coment-' + c.id);
      for (const r of c.respostas ?? []) {
        if (this.ehNovo(r, ref)) ids.push('coment-' + r.id);
      }
    }
    return ids;
  });
  protected readonly totalNovos = computed(() => this.idsNovos().length);
  protected readonly excluindoComentario = signal<number | null>(null);
  /** Comentário cuja decisão de moderação (aprovar/rejeitar) está em andamento. */
  protected readonly moderandoId = signal<number | null>(null);
  private pageComentarios = 0;

  // Responder comentários (administração) — o autor exibido ("Administração") é resolvido no servidor.
  protected readonly respondendoId = signal<number | null>(null);
  protected readonly textoResposta = signal('');
  protected readonly enviandoResposta = signal(false);

  // Editar o próprio comentário do admin (janela de 15 min conferida no servidor).
  protected readonly editandoComentario = signal<number | null>(null);
  protected readonly textoEdicao = signal('');
  protected readonly salvandoEdicao = signal(false);

  protected readonly confirmacao = signal<string | null>(null);
  private resolverConfirmacao: ((resposta: boolean) => void) | null = null;
  private saidaAutorizada = false;

  /** Fragmento da URL (ex.: "coment-42") — rola até o comentário ao abrir (vindo de notificação). */
  private fragmentoAlvo: string | null = null;

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editando.set(true);
      this.codigo.set(Number(idParam));
    }
    this.fragmentoAlvo = this.route.snapshot.fragment;
    afterNextRender(() => {
      this.carregarOpcoes();
      // Unidade travada na unidade logada (não editável).
      this.form.controls.unidadeSaudeId.setValue(this.auth.unidadeId());
      this.form.controls.unidadeSaudeId.disable();
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
        url = await this.storage.enviar(arquivo, 'rede-social');
      }
      const v = this.form.getRawValue();
      const dados: PostagemRequest = {
        titulo: v.titulo.trim(),
        descricao: v.descricao.trim() || null,
        mostrarTotalCurtidas: v.mostrarTotalCurtidas,
        habilitarComentarios: v.habilitarComentarios,
        validarComentariosIa: v.validarComentariosIa,
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
          validarComentariosIa: p.validarComentariosIa,
        });
        this.urlAtual = p.url;
        this.previewUrl.set(p.url);
        this.totalCurtidas.set(p.totalCurtidas);
        this.totalComentarios.set(p.totalComentarios);
        this.comentariosVistosEm.set(p.comentariosVistosEm ?? null);
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
        // Deep-link da notificação de moderação (/postagens/:id#coment-<id>): rola até o comentário.
        if (page === 0 && this.fragmentoAlvo) {
          const alvo = this.fragmentoAlvo;
          this.fragmentoAlvo = null;
          setTimeout(() => this.focarComentario(alvo), 150);
        }
      },
      error: () => this.carregandoComentarios.set(false),
    });
  }

  /** Rola até um comentário específico (id do elemento, ex.: "coment-42") e dá um flash. */
  private focarComentario(id: string): void {
    const el = document.getElementById(id);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      el.classList.add('coment--flash');
      setTimeout(() => el.classList.remove('coment--flash'), 1200);
    }
  }

  protected carregarMaisComentarios(): void {
    if (this.carregandoComentarios() || !this.temMaisComentarios()) return;
    this.carregarComentarios(this.pageComentarios + 1);
  }

  /** Comentário novo = de outra pessoa (não meu) criado depois da última vez que abri a postagem. */
  private ehNovo(c: Comentario, ref: string | null): boolean {
    if (c.meu) {
      return false;
    }
    if (!ref) {
      return true; // nunca abri antes: todos são novos
    }
    return new Date(c.criadoEm).getTime() > new Date(ref).getTime();
  }

  /** Usado no template para destacar o comentário/resposta. */
  protected comentarioNovo(c: Comentario): boolean {
    return this.ehNovo(c, this.comentariosVistosEm());
  }

  /** Rola até o próximo comentário novo (cicla), com um flash para localizar. */
  protected irParaProximoNovo(): void {
    const ids = this.idsNovos();
    if (ids.length === 0) {
      return;
    }
    const idx = this.indiceProximoNovo() % ids.length;
    const el = document.getElementById(ids[idx]);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      el.classList.add('coment--flash');
      setTimeout(() => el.classList.remove('coment--flash'), 1200);
    }
    this.indiceProximoNovo.set(idx + 1);
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

  /** Aprova um comentário em análise: passa a publicado (visível a todos no feed). */
  protected aprovarComentario(c: Comentario): void {
    this.decidirModeracao(c, this.service.aprovarComentario(c.id), 'Comentário aprovado e publicado');
  }

  /** Rejeita um comentário em análise: nunca é publicado (fica marcado como rejeitado). */
  protected rejeitarComentario(c: Comentario): void {
    this.decidirModeracao(c, this.service.rejeitarComentario(c.id), 'Comentário rejeitado');
  }

  /** Executa a decisão do admin e atualiza o comentário (raiz ou resposta) na lista. */
  private decidirModeracao(c: Comentario, requisicao: Observable<Comentario>, ok: string): void {
    if (this.moderandoId() != null) return;
    this.moderandoId.set(c.id);
    requisicao.subscribe({
      next: (atualizado) => {
        this.comentarios.update((lista) =>
          lista.map((raiz) =>
            raiz.id === c.id
              ? { ...raiz, statusModeracao: atualizado.statusModeracao, motivoModeracao: atualizado.motivoModeracao }
              : {
                  ...raiz,
                  respostas: raiz.respostas.map((r) =>
                    r.id === c.id
                      ? { ...r, statusModeracao: atualizado.statusModeracao, motivoModeracao: atualizado.motivoModeracao }
                      : r,
                  ),
                },
          ),
        );
        this.moderandoId.set(null);
        this.toastr.success(ok);
      },
      error: () => {
        this.moderandoId.set(null);
        this.toastr.error('Não foi possível concluir a moderação.');
      },
    });
  }

  protected iniciarEdicaoComentario(c: Comentario): void {
    this.respondendoId.set(null);
    this.editandoComentario.set(c.id);
    this.textoEdicao.set(c.texto);
  }

  protected cancelarEdicaoComentario(): void {
    this.editandoComentario.set(null);
    this.textoEdicao.set('');
  }

  protected aoDigitarEdicao(evento: Event): void {
    this.textoEdicao.set((evento.target as HTMLTextAreaElement).value);
  }

  /** Salva a edição do comentário do admin (raizId nulo = comentário-raiz). */
  protected salvarEdicaoComentario(c: Comentario, raizId: number | null): void {
    const texto = this.textoEdicao().trim();
    if (!texto || texto === c.texto || this.salvandoEdicao()) {
      this.cancelarEdicaoComentario();
      return;
    }
    this.salvandoEdicao.set(true);
    this.service.editarComentario(c.id, texto).subscribe({
      next: () => {
        this.comentarios.update((lista) =>
          raizId == null
            ? lista.map((x) => (x.id === c.id ? { ...x, texto, editado: true } : x))
            : lista.map((x) =>
                x.id === raizId
                  ? { ...x, respostas: x.respostas.map((r) => (r.id === c.id ? { ...r, texto, editado: true } : r)) }
                  : x,
              ),
        );
        this.cancelarEdicaoComentario();
        this.salvandoEdicao.set(false);
        this.toastr.success('Comentário editado');
      },
      error: () => {
        this.salvandoEdicao.set(false);
        this.toastr.error('Não foi possível editar (o prazo de edição pode ter expirado).');
      },
    });
  }

  protected abrirResposta(raizId: number): void {
    this.editandoComentario.set(null);
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
    this.service.responderComentario(raiz.id, texto).subscribe({
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
