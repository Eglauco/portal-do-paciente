import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { ConfiguracaoAgendaService } from './configuracao-agenda.service';

/**
 * Mantém o último estado da pesquisa de configuracaoAgendas (filtros, ordenação, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class ConfiguracaoAgendaBuscaStore {
  codigo = '';
  nome = '';
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = ConfiguracaoAgendaService.TAMANHO_PADRAO;

  /** Limpa apenas os filtros (a ordenação tem o próprio "Limpar ordenação"). */
  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.page = 0;
    this.size = ConfiguracaoAgendaService.TAMANHO_PADRAO;
  }
}
