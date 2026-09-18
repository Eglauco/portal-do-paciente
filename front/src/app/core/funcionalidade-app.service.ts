import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, map, tap } from 'rxjs';
import { environment } from '../../environments/environment';

/** Resposta do kill-switch global de telas do app (GET /funcionalidades, público). */
interface FuncionalidadesResponse {
  /** Mapa tela → habilitada globalmente. false = tela desligada (some pra todos). */
  telas: Record<string, boolean>;
}

/**
 * Kill-switch global das telas do app. Lê GET /funcionalidades (público, sem token) e
 * devolve o mapa tela→habilitada: cada boolean true = tela ligada; false = desligada
 * (escondida pra todos). Espelha o back. É lido sob demanda (ex.: no form do paciente); o
 * default de quem consome deve ser "habilitado", para nada ser escondido por engano antes
 * de carregar / em caso de falha.
 */
@Injectable({ providedIn: 'root' })
export class FuncionalidadeAppService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/funcionalidades`;

  /** Mapa tela→habilitada da última carga; {} enquanto não carregou (tratar tudo como ligado). */
  readonly telas = signal<Record<string, boolean>>({});

  /** Busca as telas habilitadas globalmente; atualiza o signal `telas` e emite o mapa. */
  carregar(): Observable<Record<string, boolean>> {
    return this.http.get<FuncionalidadesResponse>(this.base).pipe(
      map((r) => r?.telas ?? {}),
      tap((telas) => this.telas.set(telas)),
    );
  }
}
