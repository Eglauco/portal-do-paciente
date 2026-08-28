import { Injectable } from '@angular/core';
import { MotivoFaltaService } from './motivo-falta.service';

/**
 * Mantém o último estado da pesquisa de motivos de falta (filtros, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class MotivoFaltaBuscaStore {
  codigo = '';
  motivo = '';
  page = 0;
  size = MotivoFaltaService.TAMANHO_PADRAO;

  limpar(): void {
    this.codigo = '';
    this.motivo = '';
    this.page = 0;
    this.size = MotivoFaltaService.TAMANHO_PADRAO;
  }
}
