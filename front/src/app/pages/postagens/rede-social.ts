import { Component, afterNextRender, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Postagem } from './postagem.model';
import { PostagemService } from './postagem.service';

/** Postagem + o "há quanto tempo" já calculado (evita chamar Date no template/SSR). */
type PostView = Postagem & { tempo: string };

@Component({
  selector: 'app-rede-social',
  imports: [RouterLink],
  templateUrl: './rede-social.html',
  styleUrl: './rede-social.css',
})
export class RedeSocial {
  private readonly service = inject(PostagemService);
  private readonly auth = inject(AuthService);

  protected readonly unidadeNome = this.auth.unidadeNome;

  protected readonly posts = signal<PostView[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  protected readonly carregado = signal(false);

  constructor() {
    // Só no navegador (usa datas relativas e imagens assinadas).
    afterNextRender(() => this.carregar());
  }

  protected carregar(): void {
    this.loading.set(true);
    this.error.set(false);
    // Mesma visão do paciente, porém restrita à unidade ativa do back-office; mais
    // recentes primeiro (o endpoint ordena por criadoEm desc). Só leitura.
    this.service.listar({ unidadeId: this.auth.unidadeId() }, 0, 100).subscribe({
      next: (pagina) => {
        this.posts.set(pagina.content.map((p) => ({ ...p, tempo: this.haQuanto(p.criadoEm) })));
        this.loading.set(false);
        this.carregado.set(true);
      },
      error: () => {
        this.posts.set([]);
        this.error.set(true);
        this.loading.set(false);
        this.carregado.set(true);
      },
    });
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const a = partes[0]?.charAt(0) ?? '';
    const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (a + b).toUpperCase();
  }

  /** "agora / há X min / há X h / há X d / dd/mm/aaaa" — igual ao feed do app. */
  private haQuanto(iso: string): string {
    const d = new Date(iso).getTime();
    const min = Math.floor((Date.now() - d) / 60000);
    if (min < 1) return 'agora';
    if (min < 60) return `há ${min} min`;
    const h = Math.floor(min / 60);
    if (h < 24) return `há ${h} h`;
    const dias = Math.floor(h / 24);
    if (dias < 7) return `há ${dias} d`;
    const dt = new Date(iso);
    const dd = String(dt.getDate()).padStart(2, '0');
    const mm = String(dt.getMonth() + 1).padStart(2, '0');
    return `${dd}/${mm}/${dt.getFullYear()}`;
  }
}
