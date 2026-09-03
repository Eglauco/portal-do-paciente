import { DatePipe } from '@angular/common';
import { Component, ElementRef, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { ManifestacaoDetalhe } from './sau.model';
import { SauService } from './sau.service';

@Component({
  selector: 'app-sau-detalhe',
  imports: [DatePipe],
  templateUrl: './sau-detalhe.html',
})
export class SauDetalhe {
  private readonly service = inject(SauService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastr = inject(ToastrService);

  protected readonly detalhe = signal<ManifestacaoDetalhe | null>(null);
  protected readonly idAtual = signal<number | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);
  protected readonly enviando = signal(false);
  protected readonly fechando = signal(false);
  protected readonly texto = signal('');
  protected readonly estrelas = [1, 2, 3, 4, 5];

  /** É a vez do SAU responder (fluxo alternado de 1 mensagem por vez). */
  protected readonly podeResponder = computed(() => this.detalhe()?.status === 'AGUARDANDO_SAU');

  /** Aviso de estado (quando não é a vez do SAU) — alvo de foco após enviar/fechar. */
  private readonly estadoRef = viewChild<ElementRef<HTMLElement>>('estadoVez');

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) this.idAtual.set(Number(idParam));
    // Só no cliente (evita um fetch sem token durante o SSR desta rota).
    afterNextRender(() => this.recarregar());
  }

  protected recarregar(): void {
    const id = this.idAtual();
    if (id != null) this.carregar(id);
  }

  private carregar(id: number): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.service.detalhe(id).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  protected enviar(): void {
    const conteudo = this.texto().trim();
    const id = this.idAtual();
    if (!conteudo || id == null || this.enviando()) return;
    this.enviando.set(true);
    this.service.responder(id, conteudo).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.texto.set('');
        this.enviando.set(false);
        this.toastr.success('Resposta enviada.');
        this.moverFocoParaEstado();
      },
      error: (e) => {
        this.enviando.set(false);
        // 409 = fora de vez (estado defasado): recarrega para sincronizar o status.
        if (e?.status === 409) {
          this.toastr.warning('A manifestação foi atualizada. Recarregando…');
          this.carregar(id);
        } else {
          this.toastr.error('Não foi possível enviar a resposta.');
        }
      },
    });
  }

  protected fechar(): void {
    const id = this.idAtual();
    if (id == null || this.fechando()) return;
    this.fechando.set(true);
    this.service.fechar(id).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.fechando.set(false);
        this.toastr.success('Manifestação fechada.');
        this.moverFocoParaEstado();
      },
      error: (e) => {
        this.fechando.set(false);
        // 409 = já fechada / estado defasado: recarrega para sincronizar (espelha o enviar()).
        if (e?.status === 409) {
          this.toastr.warning('A manifestação foi atualizada. Recarregando…');
          this.carregar(id);
        } else {
          this.toastr.error('Não foi possível fechar a manifestação.');
        }
      },
    });
  }

  protected voltar(): void {
    this.router.navigate(['/sau']);
  }

  protected aoDigitar(event: Event): void {
    this.texto.set((event.target as HTMLTextAreaElement).value);
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const primeira = partes[0]?.charAt(0) ?? '';
    const ultima = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (primeira + ultima).toUpperCase();
  }

  /**
   * Após enviar/fechar, o bloco de resposta (que tinha o foco) é destruído. Move o
   * foco para o aviso de estado (role="status") para não jogar o foco no <body> —
   * WCAG 2.4.3 (foco) + 4.1.3 (mensagem de status é anunciada pelo aria-live).
   */
  private moverFocoParaEstado(): void {
    if (typeof requestAnimationFrame === 'undefined') return;
    requestAnimationFrame(() => this.estadoRef()?.nativeElement?.focus());
  }
}
