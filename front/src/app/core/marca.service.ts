import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Injectable, PLATFORM_ID, computed, inject, signal } from '@angular/core';
import { environment } from '../../environments/environment';

/** Textos + imagens de marca (white-label). Espelha MarcaResponse no back. */
export interface Marca {
  nomePlataforma: string;
  loginTitulo: string;
  loginSubtitulo: string;
  /** URL (assinada) da logomarca; null → usa a logo padrão (SVG). */
  logoUrl: string | null;
  /** URL (assinada) da imagem de fundo do login; null → só o azul. */
  loginFundoUrl: string | null;
}

/**
 * Marca dinâmica (white-label): busca nome/título/subtítulo em GET /marca (público) no boot
 * e expõe como signals. Os PADRÕES abaixo são os textos originais hardcoded — cobrem o SSR, o
 * instante antes do GET e o fail-safe (o back também cai neles se a config faltar/estiver vazia).
 * Um cache em localStorage evita "flash" do texto padrão em cargas seguintes (igual ao tema).
 */
@Injectable({ providedIn: 'root' })
export class MarcaService {
  private readonly http = inject(HttpClient);
  private readonly platformId = inject(PLATFORM_ID);
  private readonly base = `${environment.apiUrl}/marca`;
  private static readonly CACHE = 'marca-plataforma';

  private static readonly PADRAO: Marca = {
    nomePlataforma: 'Portal do Paciente',
    loginTitulo: 'A gestão do cuidado começa aqui.',
    loginSubtitulo:
      'Cadastre e acompanhe exames, consultas e informações dos pacientes — ' +
      'com segurança e agilidade no dia a dia da equipe.',
    logoUrl: null,
    loginFundoUrl: null,
  };

  private readonly marca = signal<Marca>(MarcaService.PADRAO);

  readonly nomePlataforma = computed(() => this.marca().nomePlataforma);
  readonly loginTitulo = computed(() => this.marca().loginTitulo);
  readonly loginSubtitulo = computed(() => this.marca().loginSubtitulo);
  /** URL da logomarca configurada (null → os componentes usam o SVG padrão). */
  readonly logoUrl = computed(() => this.marca().logoUrl);
  /** Valor CSS do fundo do login: `url("...")` ou null (para [style.--login-fundo]). */
  readonly loginFundoCss = computed(() => {
    const u = this.marca().loginFundoUrl;
    return u ? `url("${u}")` : null;
  });

  /** A logo assinada falhou/expirou ao carregar: descarta para os componentes caírem no SVG. */
  descartarLogo(): void {
    this.marca.update((m) => ({ ...m, logoUrl: null }));
    this.aplicarFavicon(null);
  }

  /** Boot: aplica a marca em cache (sem flash) e busca a atual do servidor. */
  carregar(): void {
    if (!isPlatformBrowser(this.platformId)) return;
    const cache = this.lerCache();
    if (cache) {
      const c = this.normalizar(cache);
      this.marca.set(c);
      this.aplicarFavicon(c.logoUrl);
    }
    this.http.get<Marca>(this.base).subscribe({
      next: (m) => {
        const resolvida = this.normalizar(m);
        this.marca.set(resolvida);
        this.salvarCache(resolvida);
        this.aplicarFavicon(resolvida.logoUrl);
      },
      error: () => {
        /* mantém o cache/padrão */
      },
    });
  }

  /** Campo de texto vazio → cai no padrão; logo vazia → null (usa o SVG). */
  private normalizar(m: Partial<Marca> | null): Marca {
    return {
      nomePlataforma: m?.nomePlataforma?.trim() || MarcaService.PADRAO.nomePlataforma,
      loginTitulo: m?.loginTitulo?.trim() || MarcaService.PADRAO.loginTitulo,
      loginSubtitulo: m?.loginSubtitulo?.trim() || MarcaService.PADRAO.loginSubtitulo,
      logoUrl: m?.logoUrl?.trim() || null,
      loginFundoUrl: m?.loginFundoUrl?.trim() || null,
    };
  }

  /** Troca o favicon da aba pela logo configurada; sem logo, volta ao favicon padrão. */
  private aplicarFavicon(logoUrl: string | null): void {
    const link = document.querySelector<HTMLLinkElement>('link[rel~="icon"]');
    if (link) link.href = logoUrl || '/favicon.ico';
  }

  private lerCache(): Marca | null {
    try {
      const raw = localStorage.getItem(MarcaService.CACHE);
      return raw ? (JSON.parse(raw) as Marca) : null;
    } catch {
      return null;
    }
  }

  private salvarCache(m: Marca): void {
    try {
      localStorage.setItem(MarcaService.CACHE, JSON.stringify(m));
    } catch {
      /* localStorage indisponível: sem cache, sem problema */
    }
  }
}
