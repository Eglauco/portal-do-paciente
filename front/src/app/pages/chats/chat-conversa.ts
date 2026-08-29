import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterNextRender, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { retry, timer } from 'rxjs';
import { ChatDetalhe, Mensagem, novoClienteId } from './chat.model';
import { ChatRealtimeService, DigitandoEvento } from './chat-realtime.service';
import { ChatService } from './chat.service';

@Component({
  selector: 'app-chat-conversa',
  imports: [DatePipe],
  templateUrl: './chat-conversa.html',
})
export class ChatConversa {
  private readonly service = inject(ChatService);
  private readonly realtime = inject(ChatRealtimeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly digitando = signal(false);
  private cancelamentos: (() => void)[] = [];
  private timerDigitando: ReturnType<typeof setTimeout> | null = null;
  private ultimoSinalDigitando = 0;

  /** Atrasos entre as retentativas de envio (tentativa inicial + 4 ≈ 30s). */
  private readonly ATRASOS = [2000, 4000, 8000, 16000];

  protected readonly detalhe = signal<ChatDetalhe | null>(null);
  protected readonly idAtual = signal<number | null>(null);
  protected readonly carregandoDetalhe = signal(false);
  protected readonly texto = signal('');

  private readonly mensagensRef = viewChild<ElementRef<HTMLElement>>('mensagens');

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) this.idAtual.set(Number(idParam));

    afterNextRender(() => {
      if (this.idAtual()) this.abrir(this.idAtual()!);
    });

    this.destroyRef.onDestroy(() => this.cancelarTudo());
  }

  protected abrir(id: number): void {
    this.idAtual.set(id);
    this.carregandoDetalhe.set(true);
    // visualizar marca as mensagens do paciente como lidas ao abrir.
    this.service.visualizar(id).subscribe({
      next: (d) => {
        this.aplicarDetalhe(d);
        this.carregandoDetalhe.set(false);
        this.rolarParaFim();
      },
      error: () => this.carregandoDetalhe.set(false),
    });
    this.observarTempoReal(id);
  }

  /** Inscreve na conversa: mensagens, "digitando…" e recibo de leitura. */
  private observarTempoReal(id: number): void {
    this.cancelarTudo();
    this.cancelamentos = [
      this.realtime.observarMensagens(id, (m) => this.aoReceberMensagem(m)),
      this.realtime.observarDigitando(id, (e) => this.aoDigitandoRecebido(e)),
      this.realtime.observarEntrega(id, () => this.aoEntregaConfirmada()),
    ];
  }

  /** O app do paciente recebeu as mensagens: marca as da unidade como entregues (2º check). */
  private aoEntregaConfirmada(): void {
    this.detalhe.update((d) => {
      if (!d) return d;
      return {
        ...d,
        mensagens: d.mensagens.map((m) =>
          m.remetente === 'UNIDADE' && !m.entregue ? { ...m, entregue: true } : m,
        ),
      };
    });
  }

  private cancelarTudo(): void {
    this.cancelamentos.forEach((c) => c());
    this.cancelamentos = [];
    if (this.timerDigitando) clearTimeout(this.timerDigitando);
    this.digitando.set(false);
  }

  private aoReceberMensagem(mensagem: Mensagem): void {
    if (this.idAtual() == null) return;
    this.digitando.set(false); // chegou mensagem: parou de digitar
    this.detalhe.update((d) => {
      if (!d) return d;
      if (d.mensagens.some((x) => x.id === mensagem.id)) return d; // evita duplicar
      // Reconcilia o eco da própria mensagem (substitui a otimista temporária, id < 0)
      // pelo clienteId (1-para-1; cai no texto só se o eco não trouxer clienteId),
      // preservando o "entregue" que já possa ter sido confirmado enquanto era otimista.
      const ehTemp = (x: Mensagem) =>
        x.id < 0 &&
        (mensagem.clienteId
          ? x.clienteId === mensagem.clienteId
          : x.remetente === mensagem.remetente && x.texto === mensagem.texto);
      const jaEntregue = d.mensagens.some((x) => ehTemp(x) && x.entregue);
      const base = d.mensagens.filter((x) => !ehTemp(x));
      return { ...d, mensagens: [...base, { ...mensagem, entregue: mensagem.entregue || jaEntregue }] };
    });
    this.rolarParaFim();
  }

  private aoDigitandoRecebido(evento: DigitandoEvento): void {
    if (evento.de !== 'PACIENTE') return; // só mostra quando o outro lado digita
    this.digitando.set(true);
    if (this.timerDigitando) clearTimeout(this.timerDigitando);
    this.timerDigitando = setTimeout(() => this.digitando.set(false), 3000);
  }

  protected enviar(): void {
    const conteudo = this.texto().trim();
    const id = this.idAtual();
    if (!conteudo || id == null) return;

    // Otimista: mostra a mensagem com o "relógio" (pendente) até o back confirmar.
    const tempId = -Date.now();
    const clienteId = novoClienteId();
    const otimista: Mensagem = {
      id: tempId,
      remetente: 'UNIDADE',
      texto: conteudo,
      enviadaEm: new Date().toISOString(),
      lida: true,
      entregue: false,
      pendente: true,
      clienteId,
    };
    this.detalhe.update((d) => (d ? { ...d, mensagens: [...d.mensagens, otimista] } : d));
    this.texto.set('');
    this.rolarParaFim();
    this.tentarEnviar(tempId, clienteId, conteudo);
  }

  protected reenviar(m: Mensagem): void {
    if (m.clienteId) this.tentarEnviar(m.id, m.clienteId, m.texto);
  }

  /** Envia com retentativas (conexão ruim); após esgotar, marca "falha" (reenviar). */
  private tentarEnviar(tempId: number, clienteId: string, conteudo: string): void {
    const id = this.idAtual();
    if (id == null) return;
    this.marcarMensagem(tempId, { pendente: true, falha: false });

    this.service
      .enviar(id, conteudo, clienteId)
      .pipe(
        retry({ count: this.ATRASOS.length, delay: (_e, n) => timer(this.ATRASOS[n - 1] ?? 16000) }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (d) => {
          // Ignora a resposta se o operador já trocou de conversa (não contamina o status).
          if (this.idAtual() !== id) return;
          // Chegou no back (1 check). NÃO substitui a lista inteira (a resposta é um
          // retrato do envio, com entregue=false, e reverteria o 2º check que pode já
          // ter chegado). Só marca a otimista como enviada e atualiza o status.
          this.detalhe.update((atual) =>
            atual
              ? {
                  ...atual,
                  status: d.status,
                  statusDescricao: d.statusDescricao,
                  mensagens: atual.mensagens.map((m) =>
                    m.id === tempId ? { ...m, pendente: false, falha: false } : m,
                  ),
                }
              : atual,
          );
          this.rolarParaFim();
        },
        // Esgotou as tentativas: marca falha (mostra "reenviar"), se ainda na mesma conversa.
        error: () => {
          if (this.idAtual() === id) this.marcarMensagem(tempId, { pendente: false, falha: true });
        },
      });
  }

  private marcarMensagem(id: number, patch: Partial<Mensagem>): void {
    this.detalhe.update((d) =>
      d ? { ...d, mensagens: d.mensagens.map((m) => (m.id === id ? { ...m, ...patch } : m)) } : d,
    );
  }

  /**
   * Aplica o retrato do servidor preservando mensagens otimistas em voo (pendentes/falha)
   * da MESMA conversa que ainda não estão no servidor — evita que a bolha "suma" numa recarga.
   */
  private aplicarDetalhe(d: ChatDetalhe): void {
    this.detalhe.update((prev) => {
      if (!prev || prev.id !== d.id) return d; // conversa diferente: substitui
      const noServidor = new Set(d.mensagens.map((m) => m.clienteId).filter(Boolean));
      const otimistas = prev.mensagens.filter(
        (m) => m.id < 0 && (m.pendente || m.falha) && (!m.clienteId || !noServidor.has(m.clienteId)),
      );
      return { ...d, mensagens: [...d.mensagens, ...otimistas] };
    });
  }

  protected resolver(): void {
    const id = this.idAtual();
    if (id == null) return;
    this.service.resolver(id).subscribe({
      next: (d) => {
        this.aplicarDetalhe(d);
      },
    });
  }

  protected reabrir(): void {
    const id = this.idAtual();
    if (id == null) return;
    this.service.reabrir(id).subscribe({
      next: (d) => {
        this.aplicarDetalhe(d);
      },
    });
  }

  protected voltar(): void {
    this.router.navigate(['/chats']);
  }

  protected aoDigitar(event: Event): void {
    this.texto.set((event.target as HTMLTextAreaElement).value);
    const id = this.idAtual();
    if (id == null) return;
    const agora = Date.now();
    if (agora - this.ultimoSinalDigitando > 1500) {
      this.ultimoSinalDigitando = agora;
      this.realtime.sinalizarDigitando(id, 'UNIDADE');
    }
  }

  protected aoTeclar(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.enviar();
    }
  }

  protected mostraSeparador(indice: number): boolean {
    const msgs = this.detalhe()?.mensagens ?? [];
    if (indice === 0) return true;
    return msgs[indice].enviadaEm.slice(0, 10) !== msgs[indice - 1].enviadaEm.slice(0, 10);
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const primeira = partes[0]?.charAt(0) ?? '';
    const ultima = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (primeira + ultima).toUpperCase();
  }

  /**
   * Rola para a última mensagem. O elemento é lido DENTRO do rAF: ao abrir a
   * conversa, o container `#mensagens` só existe depois do Angular renderizar o
   * `@if (detalhe())`, então lê-lo de imediato devolveria undefined (ficava no
   * topo). Dois frames: o 1º deixa as bolhas renderizarem; o 2º garante que o
   * layout já tem a altura final antes de irmos ao fundo.
   */
  private rolarParaFim(): void {
    if (typeof requestAnimationFrame === 'undefined') return; // SSR: sem DOM
    const irAoFundo = () => {
      const el = this.mensagensRef()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    };
    requestAnimationFrame(() => requestAnimationFrame(irAoFundo));
    // Cache frio: a fonte (Inter, display=swap) só chega depois do 2º frame e o
    // reflow aumenta a altura das bolhas; com scroll anchoring a view ficaria
    // acima do fundo real. Re-rola quando as fontes terminarem de carregar.
    if (typeof document !== 'undefined' && document.fonts?.ready) {
      document.fonts.ready.then(() => requestAnimationFrame(irAoFundo));
    }
  }
}
