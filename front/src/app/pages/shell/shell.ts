import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Unidade } from '../unidades/unidade.model';
import { UnidadeService } from '../unidades/unidade.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly unidadeService = inject(UnidadeService);

  protected readonly usuario = this.auth.usuario;
  protected readonly unidadeNome = this.auth.unidadeNome;
  protected readonly unidadeAtualId = this.auth.unidadeId;

  protected readonly menuUnidadeAberto = signal(false);
  protected readonly carregandoUnidades = signal(false);
  protected readonly trocandoUnidade = signal(false);
  protected readonly unidades = signal<Unidade[]>([]);
  private unidadesCarregadas = false;

  protected iniciais(nome: string): string {
    const partes = nome.trim().split(/\s+/);
    const a = partes[0]?.charAt(0) ?? '';
    const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
    return (a + b).toUpperCase() || 'US';
  }

  protected abrirMenuUnidade(): void {
    const abrir = !this.menuUnidadeAberto();
    this.menuUnidadeAberto.set(abrir);
    if (abrir && !this.unidadesCarregadas) {
      this.carregandoUnidades.set(true);
      this.unidadeService.listar({}, 0, 100).subscribe({
        next: (p) => {
          this.unidades.set(p.content);
          this.unidadesCarregadas = true;
          this.carregandoUnidades.set(false);
        },
        error: () => this.carregandoUnidades.set(false),
      });
    }
  }

  protected fecharMenuUnidade(): void {
    this.menuUnidadeAberto.set(false);
  }

  protected selecionarUnidade(unidade: Unidade): void {
    if (this.trocandoUnidade() || unidade.id == null) return;
    if (unidade.id === this.unidadeAtualId()) {
      this.fecharMenuUnidade();
      return;
    }
    this.trocandoUnidade.set(true);
    this.auth.trocarUnidade(unidade.id).subscribe({
      // Recarrega a tela por completo para refletir a nova unidade em todos os filtros.
      next: () => window.location.reload(),
      error: () => this.trocandoUnidade.set(false),
    });
  }

  protected logout(): void {
    this.auth.logout();
  }
}
