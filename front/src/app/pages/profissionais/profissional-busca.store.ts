import { Injectable } from '@angular/core';
import { ProfissionalSaudeService } from './profissional.service';

/**
 * Mantém o último estado da pesquisa de profissionais (filtros, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class ProfissionalSaudeBuscaStore {
  codigo = '';
  nome = '';
  page = 0;
  size = ProfissionalSaudeService.TAMANHO_PADRAO;

  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.page = 0;
    this.size = ProfissionalSaudeService.TAMANHO_PADRAO;
  }
}
