import { afterNextRender, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '../../core/auth.service';
import { Unidade } from '../unidades/unidade.model';
import { UnidadeService } from '../unidades/unidade.service';

@Component({
  selector: 'app-selecionar-unidade',
  templateUrl: './selecionar-unidade.html',
})
export class SelecionarUnidade {
  private readonly auth = inject(AuthService);
  private readonly unidadeService = inject(UnidadeService);
  private readonly router = inject(Router);
  private readonly toastr = inject(ToastrService);

  protected readonly usuario = this.auth.usuario;
  protected readonly unidades = signal<Unidade[]>([]);
  protected readonly carregando = signal(true);
  protected readonly erro = signal(false);
  protected readonly busca = signal('');
  /** Id da unidade cujo "entrar" está em andamento (bloqueia cliques). */
  protected readonly selecionadaId = signal<number | null>(null);

  protected readonly filtradas = computed(() => {
    const q = this.busca().trim().toLowerCase();
    const lista = this.unidades();
    return q ? lista.filter((u) => u.nome.toLowerCase().includes(q)) : lista;
  });

  constructor() {
    afterNextRender(() => this.carregar());
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.unidadeService.listar({}, 0, 100).subscribe({
      next: (p) => {
        this.unidades.set(p.content);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  protected atualizarBusca(event: Event): void {
    this.busca.set((event.target as HTMLInputElement).value);
  }

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const a = partes[0]?.charAt(0) ?? '';
    const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (a + b).toUpperCase() || 'US';
  }

  protected selecionar(unidade: Unidade): void {
    if (this.selecionadaId() != null || unidade.id == null) return;
    this.selecionadaId.set(unidade.id);
    this.auth.trocarUnidade(unidade.id).subscribe({
      next: () => this.router.navigate(['/inicio']),
      error: () => {
        this.selecionadaId.set(null);
        this.toastr.error('Não foi possível selecionar a unidade. Tente novamente.');
      },
    });
  }

  protected sair(): void {
    this.auth.logout();
  }
}
