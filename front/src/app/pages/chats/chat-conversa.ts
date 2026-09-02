import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { retry, throwError, timer } from 'rxjs';
import { AuthService } from '../../core/auth.service';
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
  private readonly auth = inject(AuthService);

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
  protected readonly assumindo = signal(false);
  /** Pergunta ao abrir: assumir (nova) ou transferir (já tem responsável). Null = sem pergunta. */
  protected readonly promptAssumir = signal<{ transferir: boolean; nome: string | null } | null>(null);

  /** Sou o atendente responsável por esta conversa (só o responsável envia). */
  protected readonly souResponsavel = computed(() => {
    const d = this.detalhe();
    const uid = this.auth.usuarioId();
    return !!d && d.responsavelId != null && d.responsavelId === uid;
  });

  private readonly mensagensRef = viewChild<ElementRef<HTMLElement>>('mensagens');
  private readonly bloqueioRef = viewChild<ElementRef<HTMLElement>>('bloqueio');
  private readonly assumirRef = viewChild<ElementRef<HTMLElement>>('assumirBanner');
  private readonly promptDialogRef = viewChild<ElementRef<HTMLElement>>('promptDialog');

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
        this.avaliarPrompt();
        this.rolarParaFim();
      },
      error: () => this.carregandoDetalhe.set(false),
    });
    this.observarTempoReal(id);
  }

  /** Ao abrir, se não sou o responsável, pergunta se quero assumir/transferir. */
  private avaliarPrompt(): void {
    const d = this.detalhe();
    if (!d) return;
    // Conversa resolvida exige "Reabrir" explícito (cabeçalho): não oferece
    // assumir/transferir aqui — senão assumir reabriria a conversa em silêncio.
    if (this.souResponsavel() || d.status === 'RESOLVIDO') {
      this.promptAssumir.set(null);
      return;
    }
    this.abrirPrompt({ transferir: d.responsavelId != null, nome: d.responsavelNome ?? null });
  }

  /** Abre o modal de assumir/transferir e move o foco para ele (a11y). */
  private abrirPrompt(valor: { transferir: boolean; nome: string | null }): void {
    this.promptAssumir.set(valor);
    this.focarRef(this.promptDialogRef);
  }

  /** Assume (ou transfere para si) a conversa: passo a ser o responsável. */
  protected assumir(): void {
    const id = this.idAtual();
    if (id == null || this.assumindo()) return;
    this.assumindo.set(true);
    this.promptAssumir.set(null);
    this.service.assumir(id).subscribe({
      next: (d) => {
        this.aplicarDetalhe(d);
        this.assumindo.set(false);
      },
      error: () => this.assumindo.set(false),
    });
  }

  /** Recusou assumir: fica só em modo leitura. */
  protected recusarPrompt(): void {
    this.promptAssumir.set(null);
  }

  /** Troca de responsável em tempo real (bloqueia na hora quem deixou de ser o dono). */
  private aoResponsavelMudou(e: { responsavelId: number | null; responsavelNome: string | null }): void {
    const eraResponsavel = this.souResponsavel();
    this.detalhe.update((d) =>
      d ? { ...d, responsavelId: e.responsavelId, responsavelNome: e.responsavelNome } : d,
    );
    const souResponsavelAgora = this.souResponsavel();
    // Perdi a posse ao vivo (outro atendente assumiu): o campo de envio some e dá
    // lugar ao aviso — move o foco para ele para anunciar a mudança (WCAG 2.4.3).
    if (eraResponsavel && !souResponsavelAgora) this.focarRef(this.assumirRef);
    // Mantém o texto do modal aberto coerente com o responsável atual (evita
    // mostrar "Deseja assumir?" depois que outro atendente já assumiu).
    if (this.promptAssumir() !== null) {
      if (souResponsavelAgora) {
        this.promptAssumir.set(null);
      } else {
        const atual = this.detalhe();
        this.promptAssumir.set({
          transferir: atual?.responsavelId != null,
          nome: atual?.responsavelNome ?? null,
        });
      }
    }
  }

  /** Move o foco para um elemento renderizado condicionalmente (após 2 frames). */
  private focarRef(ref: () => ElementRef<HTMLElement> | undefined): void {
    if (typeof requestAnimationFrame === 'undefined') return;
    requestAnimationFrame(() => requestAnimationFrame(() => ref()?.nativeElement?.focus()));
  }

  /** Inscreve na conversa: mensagens, "digitando…" e recibo de leitura. */
  private observarTempoReal(id: number): void {
    this.cancelarTudo();
    this.cancelamentos = [
      this.realtime.observarMensagens(id, (m) => this.aoReceberMensagem(m)),
      this.realtime.observarDigitando(id, (e) => this.aoDigitandoRecebido(e)),
      this.realtime.observarEntrega(id, () => this.aoEntregaConfirmada()),
      this.realtime.observarResponsavel(id, (e) => this.aoResponsavelMudou(e)),
      // Ao (re)conectar, recarrega o retrato: recupera o "entregue" da 1ª
      // mensagem, cujo evento pode ter ocorrido antes de a inscrição ficar ativa.
      this.realtime.observarConexao(() => this.ressincronizar(id)),
    ];
  }

  /**
   * Re-sincronização PASSIVA (reconexão / evento de entrega): concilia apenas as
   * MENSAGENS (recupera o 2º check e ecos perdidos). NÃO toca em status nem em
   * pacienteUsandoApp — este retrato pode chegar atrasado e regredi-los (ex.:
   * re-bloquear o envio de um paciente que já voltou ao app). Esses campos têm
   * seus próprios fluxos (mensagem do paciente, 422, abrir/resolver/reabrir).
   */
  private ressincronizar(id: number): void {
    if (this.idAtual() !== id) return;
    this.service.detalhe(id).subscribe({
      next: (d) => {
        if (this.idAtual() !== id) return;
        this.detalhe.update((prev) =>
          !prev || prev.id !== d.id ? prev : { ...prev, mensagens: this.mesclarMensagens(prev.mensagens, d.mensagens) },
        );
      },
    });
  }

  /**
   * O app do paciente confirmou entrega. O evento é por CONVERSA (não diz QUAIS
   * mensagens), então marcar todas as bolhas localmente marcaria como entregue
   * até uma mensagem que o app ainda não recebeu — que voltaria para 1 check ao
   * recarregar (o banco é a verdade). Em vez disso, relê o retrato do servidor,
   * que traz o "entregue" correto por mensagem.
   */
  private aoEntregaConfirmada(): void {
    const id = this.idAtual();
    if (id != null) this.ressincronizar(id);
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
      // Uma mensagem do paciente prova que ele voltou a usar o app (o endpoint
      // dele exige sessão válida): re-libera o envio se estava bloqueado.
      const usandoApp = mensagem.remetente === 'PACIENTE' ? true : d.pacienteUsandoApp;
      return {
        ...d,
        pacienteUsandoApp: usandoApp,
        mensagens: [...base, { ...mensagem, entregue: mensagem.entregue || jaEntregue }],
      };
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
    // Bloqueia só quando o back confirma que o paciente não usa mais o app
    // (pacienteUsandoApp === false). Se o campo vier ausente/indefinido, não
    // bloqueia na UI — o back ainda é a autoridade (responde 422 se preciso).
    if (this.detalhe()?.pacienteUsandoApp === false) return;

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
        retry({
          count: this.ATRASOS.length,
          // 422 (paciente não usa app) e 409 (outro atendente assumiu) são
          // permanentes: não adianta retentar.
          delay: (e, n) =>
            e?.status === 422 || e?.status === 409
              ? throwError(() => e)
              : timer(this.ATRASOS[n - 1] ?? 16000),
        }),
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
        error: (e) => {
          if (this.idAtual() !== id) return;
          // Outro atendente assumiu a conversa: perdi a posse. Re-sincroniza o
          // responsável (o campo de envio some) e marca a bolha como não enviada.
          if (e?.status === 409) {
            this.service.detalhe(id).subscribe({ next: (d) => this.aplicarDetalhe(d) });
            this.marcarMensagem(tempId, { pendente: false, falha: true });
            return;
          }
          // Paciente deixou de usar o app durante a conversa: bloqueia o envio e
          // mostra o aviso fixo. Mantém a bolha marcada como "não enviada" (o
          // texto do operador fica visível), mas sem "reenviar" — o envio está
          // bloqueado (o botão é ocultado enquanto pacienteUsandoApp é false).
          if (e?.status === 422) {
            this.detalhe.update((d) =>
              d
                ? {
                    ...d,
                    pacienteUsandoApp: false,
                    mensagens: d.mensagens.map((m) =>
                      m.id === tempId ? { ...m, pendente: false, falha: true } : m,
                    ),
                  }
                : d,
            );
            this.focarBloqueio();
            return;
          }
          // Esgotou as tentativas (conexão): marca falha (mostra "reenviar").
          this.marcarMensagem(tempId, { pendente: false, falha: true });
        },
      });
  }

  private marcarMensagem(id: number, patch: Partial<Mensagem>): void {
    this.detalhe.update((d) =>
      d ? { ...d, mensagens: d.mensagens.map((m) => (m.id === id ? { ...m, ...patch } : m)) } : d,
    );
  }

  /**
   * Mescla as mensagens do retrato do servidor com o estado local PRESERVANDO A
   * ORDEM local. Um retrato pode chegar ATRASADO (um GET disparado antes de a
   * última mensagem ser gravada, mas aplicado depois): por isso percorremos a
   * lista local na posição atual, casando cada uma com a do servidor (por
   * clienteId, senão id) — nunca removemos uma local que o retrato ainda não tem
   * (a bolha não "some" nem "pula") e o "entregue" só sobe (2º check não regride).
   */
  private mesclarMensagens(atuais: Mensagem[], servidor: Mensagem[]): Mensagem[] {
    const servPorId = new Map(servidor.map((s) => [s.id, s]));
    const servPorCliente = new Map(servidor.filter((s) => s.clienteId).map((s) => [s.clienteId!, s]));
    const usados = new Set<Mensagem>();
    const mescladas = atuais.map((m) => {
      const s = (m.clienteId ? servPorCliente.get(m.clienteId) : undefined) ?? servPorId.get(m.id);
      if (!s) return m; // ainda não refletida no servidor: mantém na posição
      usados.add(s);
      return { ...s, entregue: s.entregue || m.entregue };
    });
    const novas = servidor.filter((s) => !usados.has(s)); // ex.: eco perdido, recuperado no retrato
    return [...mescladas, ...novas];
  }

  /** Aplica um retrato FRESCO (abrir/resolver/reabrir): status/flags do servidor + mensagens mescladas. */
  private aplicarDetalhe(d: ChatDetalhe): void {
    this.detalhe.update((prev) =>
      !prev || prev.id !== d.id ? d : { ...d, mensagens: this.mesclarMensagens(prev.mensagens, d.mensagens) },
    );
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
  /**
   * Move o foco para o aviso de bloqueio quando ele aparece (o campo de envio
   * foi removido do DOM): mantém o teclado/leitor de tela no contexto certo e
   * anuncia o aviso (role="alert" + foco). Ver WCAG 2.4.3.
   */
  private focarBloqueio(): void {
    if (typeof requestAnimationFrame === 'undefined') return;
    requestAnimationFrame(() => this.bloqueioRef()?.nativeElement?.focus());
  }

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
