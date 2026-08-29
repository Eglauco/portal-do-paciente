import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterNextRender, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Chat, ChatDetalhe, Mensagem } from './chat.model';
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

  protected readonly digitando = signal(false);
  private cancelamentos: (() => void)[] = [];
  private timerDigitando: ReturnType<typeof setTimeout> | null = null;
  private ultimoSinalDigitando = 0;

  protected readonly chats = signal<Chat[]>([]);
  protected readonly detalhe = signal<ChatDetalhe | null>(null);
  protected readonly idAtual = signal<number | null>(null);
  protected readonly carregandoDetalhe = signal(false);
  protected readonly enviando = signal(false);
  protected readonly texto = signal('');

  private readonly mensagensRef = viewChild<ElementRef<HTMLElement>>('mensagens');

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) this.idAtual.set(Number(idParam));

    afterNextRender(() => {
      this.carregarLista();
      if (this.idAtual()) this.abrir(this.idAtual()!);
    });

    inject(DestroyRef).onDestroy(() => this.cancelarTudo());
  }

  protected abrir(id: number): void {
    this.idAtual.set(id);
    this.carregandoDetalhe.set(true);
    // visualizar marca as mensagens do paciente como lidas ao abrir.
    this.service.visualizar(id).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.carregandoDetalhe.set(false);
        this.carregarLista();
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
      // Reconcilia o eco da própria mensagem otimista (mesmo texto, ainda pendente).
      const base =
        mensagem.remetente === 'UNIDADE'
          ? d.mensagens.filter((x) => !(x.pendente && x.texto === mensagem.texto))
          : d.mensagens;
      return { ...d, mensagens: [...base, mensagem] };
    });
    this.rolarParaFim();
    this.carregarLista();
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
    if (!conteudo || id == null || this.enviando()) return;
    this.enviando.set(true);

    // Otimista: mostra a mensagem com o "relógio" (pendente) até o back confirmar.
    const otimista: Mensagem = {
      id: -Date.now(),
      remetente: 'UNIDADE',
      texto: conteudo,
      enviadaEm: new Date().toISOString(),
      lida: true,
      entregue: false,
      pendente: true,
    };
    this.detalhe.update((d) => (d ? { ...d, mensagens: [...d.mensagens, otimista] } : d));
    this.texto.set('');
    this.rolarParaFim();

    this.service.enviar(id, conteudo).subscribe({
      next: (d) => {
        // Back confirmou (1 check); o 2º check chega pelo evento de entrega.
        this.detalhe.set(d);
        this.enviando.set(false);
        this.carregarLista();
        this.rolarParaFim();
      },
      // Falha: mantém a mensagem otimista com o relógio.
      error: () => this.enviando.set(false),
    });
  }

  protected resolver(): void {
    const id = this.idAtual();
    if (id == null) return;
    this.service.resolver(id).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.carregarLista();
      },
    });
  }

  protected reabrir(): void {
    const id = this.idAtual();
    if (id == null) return;
    this.service.reabrir(id).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.carregarLista();
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

  private carregarLista(): void {
    this.service.listar({}, 0, 100).subscribe({ next: (p) => this.chats.set(p.content) });
  }

  private rolarParaFim(): void {
    const el = this.mensagensRef()?.nativeElement;
    if (!el || typeof requestAnimationFrame === 'undefined') return;
    requestAnimationFrame(() => (el.scrollTop = el.scrollHeight));
  }
}
