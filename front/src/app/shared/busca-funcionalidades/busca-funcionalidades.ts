import { isPlatformBrowser } from '@angular/common';
import { Component, ElementRef, PLATFORM_ID, computed, inject, signal, viewChild } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { FUNCIONALIDADES, Funcionalidade } from '../../core/funcionalidades';

/** Pedaço do rótulo, marcando o trecho que casou com a busca (para o destaque). */
interface Parte {
  readonly t: string;
  readonly hit: boolean;
}

/** Item já resolvido para a lista (com destaque e flag de recente). */
interface Resultado extends Funcionalidade {
  readonly recente: boolean;
  readonly partes: readonly Parte[];
}

const CHAVE_RECENTES = 'pop.busca.recentes';
const MAX_RECENTES = 6;

/** Minúsculas + sem acento, para busca tolerante a acentuação. */
function normalizar(texto: string): string {
  // NFD separa o acento em marca combinante (U+0300–U+036F); removê-las tira o acento.
  return texto
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '');
}

/** Pontua o quão bem a funcionalidade casa com a busca (menor = melhor; 99 = não casa). */
function pontuar(f: Funcionalidade, q: string): number {
  const rotulo = normalizar(f.rotulo);
  if (rotulo.startsWith(q)) return 0;
  if (rotulo.split(/\s+/).some((palavra) => palavra.startsWith(q))) return 1;
  if (rotulo.includes(q)) return 2;
  if (normalizar(f.grupo).includes(q)) return 3;
  if ((f.palavras ?? []).some((p) => normalizar(p).includes(q))) return 4;
  return 99;
}

/** Quebra o rótulo em [antes, casado, depois] preservando os acentos originais. */
function destacar(rotulo: string, buscaBruta: string): Parte[] {
  const q = normalizar(buscaBruta.trim());
  if (!q) return [{ t: rotulo, hit: false }];

  const chars = [...rotulo];
  const norms = chars.map((c) => normalizar(c));
  // Mapeia cada posição da string normalizada de volta ao índice do char original.
  const mapa: number[] = [];
  norms.forEach((n, i) => {
    for (let k = 0; k < n.length; k++) mapa.push(i);
  });
  const juntado = norms.join('');
  const idx = juntado.indexOf(q);
  if (idx === -1) return [{ t: rotulo, hit: false }];

  const inicio = mapa[idx];
  const fim = mapa[idx + q.length - 1] + 1;
  const partes: Parte[] = [];
  const pre = chars.slice(0, inicio).join('');
  const mid = chars.slice(inicio, fim).join('');
  const pos = chars.slice(fim).join('');
  if (pre) partes.push({ t: pre, hit: false });
  partes.push({ t: mid, hit: true });
  if (pos) partes.push({ t: pos, hit: false });
  return partes;
}

@Component({
  selector: 'app-busca-funcionalidades',
  imports: [],
  templateUrl: './busca-funcionalidades.html',
  styleUrl: './busca-funcionalidades.css',
  host: { '(document:keydown)': 'aoTeclar($event)' },
})
export class BuscaFuncionalidades {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly ehNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly campo = viewChild<ElementRef<HTMLInputElement>>('campo');
  private focoAnterior: HTMLElement | null = null;

  protected readonly aberto = signal(false);
  protected readonly query = signal('');
  protected readonly indiceAtivo = signal(0);
  private readonly recentes = signal<readonly string[]>(this.lerRecentes());

  /** Funcionalidades que o usuário tem acesso (ordem do menu). */
  private readonly acessiveis = computed(() => FUNCIONALIDADES.filter((f) => this.auth.temTela(f.tela)));

  /** Lista final exibida: busca ranqueada; se vazia, recentes no topo + todas. */
  protected readonly resultados = computed<Resultado[]>(() => {
    const base = this.acessiveis();
    const q = normalizar(this.query().trim());

    if (!q) {
      const rec = this.recentes();
      const ordenado = [...base].sort((a, b) => posicaoRecente(rec, a.tela) - posicaoRecente(rec, b.tela));
      return ordenado.map((f) => ({ ...f, recente: rec.includes(f.tela), partes: [{ t: f.rotulo, hit: false }] }));
    }

    return base
      .map((f) => ({ f, score: pontuar(f, q) }))
      .filter((x) => x.score < 99)
      .sort((a, b) => a.score - b.score)
      .map((x) => ({ ...x.f, recente: false, partes: destacar(x.f.rotulo, this.query()) }));
  });

  /** Há alguma recente entre os resultados atuais (só faz sentido com busca vazia). */
  protected readonly temRecentes = computed(() => !this.query().trim() && this.resultados().some((r) => r.recente));

  // ---------- abertura / fechamento ----------

  protected abrir(): void {
    if (this.aberto()) return;
    this.focoAnterior = this.ehNavegador ? (document.activeElement as HTMLElement | null) : null;
    this.query.set('');
    this.indiceAtivo.set(0);
    this.aberto.set(true);
    // O campo só existe depois que o overlay renderiza.
    setTimeout(() => this.campo()?.nativeElement.focus(), 0);
  }

  protected fechar(): void {
    if (!this.aberto()) return;
    this.aberto.set(false);
    this.query.set('');
    this.focoAnterior?.focus?.();
    this.focoAnterior = null;
  }

  // ---------- teclado ----------

  protected aoTeclar(evento: KeyboardEvent): void {
    // Abrir/fechar com Alt+1 (funciona de qualquer lugar do back-office).
    if (evento.altKey && (evento.key === '1' || evento.code === 'Digit1')) {
      evento.preventDefault();
      this.aberto() ? this.fechar() : this.abrir();
      return;
    }
    if (!this.aberto()) return;

    switch (evento.key) {
      case 'Escape':
        evento.preventDefault();
        this.fechar();
        break;
      case 'ArrowDown':
        evento.preventDefault();
        this.mover(1);
        break;
      case 'ArrowUp':
        evento.preventDefault();
        this.mover(-1);
        break;
      case 'Home':
        evento.preventDefault();
        this.irPara(0);
        break;
      case 'End':
        evento.preventDefault();
        this.irPara(this.resultados().length - 1);
        break;
      case 'Enter':
        evento.preventDefault();
        // Ctrl+Enter (ou Cmd+Enter no Mac) abre a tela numa nova guia.
        if (evento.ctrlKey || evento.metaKey) {
          this.abrirNovaGuia(this.resultados()[this.indiceAtivo()]);
        } else {
          this.selecionar(this.resultados()[this.indiceAtivo()]);
        }
        break;
      case 'Tab':
        // O campo é o único elemento focável do diálogo — prende o Tab aqui para o
        // foco não escapar para o menu/topbar que ficam escondidos atrás do overlay.
        evento.preventDefault();
        this.campo()?.nativeElement.focus();
        break;
    }
  }

  protected aoDigitar(evento: Event): void {
    this.query.set((evento.target as HTMLInputElement).value);
    this.indiceAtivo.set(0);
  }

  private mover(passo: number): void {
    const n = this.resultados().length;
    if (n === 0) return;
    this.irPara((this.indiceAtivo() + passo + n) % n);
  }

  private irPara(indice: number): void {
    if (indice < 0) return;
    this.indiceAtivo.set(indice);
    if (this.ehNavegador) {
      queueMicrotask(() => document.getElementById('bf-opt-' + indice)?.scrollIntoView({ block: 'nearest' }));
    }
  }

  // ---------- seleção ----------

  protected selecionar(item: Resultado | undefined): void {
    if (!item) return;
    this.registrarRecente(item.tela);
    const rota = item.rota;
    this.fechar();
    this.router.navigateByUrl(rota);
  }

  /** Abre a funcionalidade numa nova guia (a sessão fica em localStorage, então já entra logada). */
  protected abrirNovaGuia(item: Resultado | undefined): void {
    if (!item) return;
    this.registrarRecente(item.tela);
    if (this.ehNavegador) {
      const url = this.router.serializeUrl(this.router.createUrlTree([item.rota]));
      // IMPORTANTE: abre num setTimeout (FORA do evento de teclado). Se o window.open roda
      // dentro do keydown com o Ctrl pressionado, o Chrome aplica a regra do Ctrl+clique e
      // joga a guia para o fundo — e nem .focus() nem <a> sintético vencem isso (a disposição
      // vem do estado físico do modificador no momento da chamada). Deferido, ele usa a
      // disposição padrão (guia em foco). Comportamento confirmado no Chrome; a ativação do
      // usuário continua valendo dentro deste setTimeout(0), então o pop-up não é bloqueado.
      setTimeout(() => {
        const janela = window.open(url, '_blank');
        janela?.focus();
      }, 0);
    }
    this.fechar();
  }

  /** Clique: com Ctrl/Cmd abre em nova guia; sem modificador, navega na mesma. */
  protected aoClicar(item: Resultado, evento: MouseEvent): void {
    if (evento.ctrlKey || evento.metaKey) {
      evento.preventDefault();
      this.abrirNovaGuia(item);
    } else {
      this.selecionar(item);
    }
  }

  /** Clique do meio do mouse abre em nova guia (convenção do navegador). */
  protected aoClicarMeio(item: Resultado, evento: MouseEvent): void {
    if (evento.button === 1) {
      evento.preventDefault();
      this.abrirNovaGuia(item);
    }
  }

  protected passarMouse(indice: number): void {
    this.indiceAtivo.set(indice);
  }

  // ---------- recentes (localStorage) ----------

  private lerRecentes(): readonly string[] {
    if (!this.ehNavegador) return [];
    try {
      const bruto = localStorage.getItem(CHAVE_RECENTES);
      const lista = bruto ? (JSON.parse(bruto) as unknown) : [];
      return Array.isArray(lista) ? lista.filter((x): x is string => typeof x === 'string') : [];
    } catch {
      return [];
    }
  }

  private registrarRecente(tela: string): void {
    const atual = [tela, ...this.recentes().filter((t) => t !== tela)].slice(0, MAX_RECENTES);
    this.recentes.set(atual);
    if (!this.ehNavegador) return;
    try {
      localStorage.setItem(CHAVE_RECENTES, JSON.stringify(atual));
    } catch {
      /* storage indisponível — mantém só em memória */
    }
  }

  // ---------- ícone (SVG estático confiável) ----------

  private readonly cacheIcone = new Map<string, SafeHtml>();

  protected icone(html: string): SafeHtml {
    let seguro = this.cacheIcone.get(html);
    if (!seguro) {
      seguro = this.sanitizer.bypassSecurityTrustHtml(html);
      this.cacheIcone.set(html, seguro);
    }
    return seguro;
  }
}

/** Posição da tela na lista de recentes (Infinity quando não é recente). */
function posicaoRecente(recentes: readonly string[], tela: string): number {
  const i = recentes.indexOf(tela);
  return i === -1 ? Number.POSITIVE_INFINITY : i;
}
