import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CriarInquilino, Inquilino } from './inquilino.model';

/**
 * Console de super-admin (acessado "por fora" do sistema dos inquilinos). Não usa o token do admin:
 * cada chamada leva o segredo fixo no header {@code X-SuperAdmin-Secret} (o mesmo do backend). O
 * segredo fica só na memória do componente — nunca persistido.
 */
@Injectable({ providedIn: 'root' })
export class SuperadminService {
  private readonly http = inject(HttpClient);

  readonly base = `${environment.apiUrl}/superadmin/inquilinos`;

  listar(segredo: string): Observable<Inquilino[]> {
    return this.http.get<Inquilino[]>(this.base, { headers: this.headers(segredo) });
  }

  criar(segredo: string, inquilino: CriarInquilino): Observable<Inquilino> {
    return this.http.post<Inquilino>(this.base, inquilino, { headers: this.headers(segredo) });
  }

  private headers(segredo: string): HttpHeaders {
    return new HttpHeaders({ 'X-SuperAdmin-Secret': segredo });
  }
}
