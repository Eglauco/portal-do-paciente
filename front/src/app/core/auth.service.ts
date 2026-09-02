import { isPlatformBrowser } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../environments/environment';

interface LoginResponse {
  token: string;
  nome: string;
  email: string;
  unidadeSaudeId: number | null;
  unidadeSaudeNome: string | null;
  expiraEm: string;
}

interface UsuarioLogadoResponse {
  nome: string;
  email: string;
  unidadeSaudeId: number | null;
  unidadeSaudeNome: string | null;
}

export interface UsuarioLogado {
  nome: string;
  email: string;
  unidadeSaudeId: number | null;
  unidadeSaudeNome: string | null;
}

const CHAVE_TOKEN = 'pop.token';
const CHAVE_USUARIO = 'pop.usuario';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly ehNavegador = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly base = `${environment.apiUrl}/auth`;

  private readonly _usuario = signal<UsuarioLogado | null>(this.lerUsuarioArmazenado());
  readonly usuario = this._usuario.asReadonly();
  readonly logado = computed(() => this._usuario() !== null);
  /** Unidade de saúde ativa do usuário (usada como filtro/valor fixo nos CRUDs). */
  readonly unidadeId = computed(() => this._usuario()?.unidadeSaudeId ?? null);
  readonly unidadeNome = computed(() => this._usuario()?.unidadeSaudeNome ?? null);

  /** Id do usuário logado (claim "uid" do token) — usado no chat para saber quem é o responsável. */
  usuarioId(): number | null {
    const token = this.token();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      const uid = payload.uid;
      return typeof uid === 'number' ? uid : uid != null ? Number(uid) : null;
    } catch {
      return null;
    }
  }

  login(email: string, senha: string, lembrar: boolean): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${this.base}/login`, { email, senha })
      .pipe(tap((r) => this.armazenarSessao(r, lembrar)));
  }

  /** Troca a unidade ativa (persiste no backend) e atualiza a sessão local. */
  trocarUnidade(unidadeSaudeId: number): Observable<UsuarioLogadoResponse> {
    return this.http.put<UsuarioLogadoResponse>(`${this.base}/unidade`, { unidadeSaudeId }).pipe(
      tap((u) => {
        const atualizado: UsuarioLogado = {
          nome: u.nome,
          email: u.email,
          unidadeSaudeId: u.unidadeSaudeId,
          unidadeSaudeNome: u.unidadeSaudeNome,
        };
        this._usuario.set(atualizado);
        this.gravarUsuario(atualizado);
      }),
    );
  }

  logout(): void {
    this.limparSessao();
    this.router.navigate(['/login']);
  }

  /** Token válido (não expirado) ou null. Limpa a sessão se estiver expirado. */
  token(): string | null {
    if (!this.ehNavegador) return null;
    const bruto = this.ler(CHAVE_TOKEN);
    if (!bruto) return null;
    if (this.expirado(bruto)) {
      this.limparSessao();
      return null;
    }
    return bruto;
  }

  private armazenarSessao(r: LoginResponse, lembrar: boolean): void {
    const usuario: UsuarioLogado = {
      nome: r.nome,
      email: r.email,
      unidadeSaudeId: r.unidadeSaudeId,
      unidadeSaudeNome: r.unidadeSaudeNome,
    };
    this._usuario.set(usuario);
    if (!this.ehNavegador) return;
    const usar = lembrar ? localStorage : sessionStorage;
    const outro = lembrar ? sessionStorage : localStorage;
    try {
      outro.removeItem(CHAVE_TOKEN);
      outro.removeItem(CHAVE_USUARIO);
      usar.setItem(CHAVE_TOKEN, r.token);
      usar.setItem(CHAVE_USUARIO, JSON.stringify(usuario));
    } catch {
      /* storage indisponível — mantém a sessão em memória */
    }
  }

  /** Regrava o usuário no mesmo storage onde está o token. */
  private gravarUsuario(usuario: UsuarioLogado): void {
    if (!this.ehNavegador) return;
    try {
      const store = localStorage.getItem(CHAVE_TOKEN)
        ? localStorage
        : sessionStorage.getItem(CHAVE_TOKEN)
          ? sessionStorage
          : null;
      store?.setItem(CHAVE_USUARIO, JSON.stringify(usuario));
    } catch {
      /* ignore */
    }
  }

  private limparSessao(): void {
    this._usuario.set(null);
    if (!this.ehNavegador) return;
    try {
      localStorage.removeItem(CHAVE_TOKEN);
      localStorage.removeItem(CHAVE_USUARIO);
      sessionStorage.removeItem(CHAVE_TOKEN);
      sessionStorage.removeItem(CHAVE_USUARIO);
    } catch {
      /* ignore */
    }
  }

  private lerUsuarioArmazenado(): UsuarioLogado | null {
    if (!this.ehNavegador) return null;
    try {
      const bruto = this.ler(CHAVE_TOKEN);
      if (!bruto || this.expirado(bruto)) return null;
      const u = this.ler(CHAVE_USUARIO);
      return u ? (JSON.parse(u) as UsuarioLogado) : null;
    } catch {
      return null;
    }
  }

  private ler(chave: string): string | null {
    try {
      return localStorage.getItem(chave) ?? sessionStorage.getItem(chave);
    } catch {
      return null;
    }
  }

  /** Lê o claim exp (segundos) do JWT e verifica se já passou. */
  private expirado(token: string): boolean {
    try {
      const payload = token.split('.')[1];
      const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
      return typeof json.exp === 'number' && json.exp * 1000 <= Date.now();
    } catch {
      return true; // token malformado → tratar como inválido
    }
  }
}
