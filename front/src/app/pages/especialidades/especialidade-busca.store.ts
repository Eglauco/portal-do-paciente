import { Injectable } from '@angular/core';
import { EspecialidadeService } from './especialidade.service';

/**
 * Mantém o último estado da pesquisa de especialidades (filtros, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class EspecialidadeBuscaStore {
  codigo = '';
  nome = '';
  page = 0;
  size = EspecialidadeService.TAMANHO_PADRAO;

  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.page = 0;
    this.size = EspecialidadeService.TAMANHO_PADRAO;
  }
}
