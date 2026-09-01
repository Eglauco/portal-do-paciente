import { Injectable } from '@angular/core';
import { StatusManifestacao } from './sau.model';
import { SauService } from './sau.service';

/** Mantém o estado da pesquisa do SAU ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class SauBuscaStore {
  tipoId: number | null = null;
  status: StatusManifestacao | null = null;
  page = 0;
  size = SauService.TAMANHO_PADRAO;

  limpar(): void {
    this.tipoId = null;
    this.status = null;
    this.page = 0;
    this.size = SauService.TAMANHO_PADRAO;
  }
}
