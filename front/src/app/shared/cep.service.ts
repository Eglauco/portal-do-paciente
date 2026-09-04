import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';

/** Endereço retornado pelo ViaCEP (campos que usamos). */
export interface EnderecoCep {
  logradouro: string;
  bairro: string;
  municipio: string;
  uf: string;
}

interface ViaCepResposta {
  logradouro?: string;
  bairro?: string;
  localidade?: string;
  uf?: string;
  erro?: boolean;
}

/** Consulta de CEP via ViaCEP (API pública). Devolve null quando não encontra ou falha. */
@Injectable({ providedIn: 'root' })
export class CepService {
  private readonly http = inject(HttpClient);

  buscar(cep: string): Observable<EnderecoCep | null> {
    const digitos = (cep ?? '').replace(/\D/g, '');
    if (digitos.length !== 8) return of(null);
    return this.http.get<ViaCepResposta>(`https://viacep.com.br/ws/${digitos}/json/`).pipe(
      map((r) =>
        r.erro
          ? null
          : {
              logradouro: r.logradouro ?? '',
              bairro: r.bairro ?? '',
              municipio: r.localidade ?? '',
              uf: r.uf ?? '',
            },
      ),
      catchError(() => of(null)),
    );
  }
}
