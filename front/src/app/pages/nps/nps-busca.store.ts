import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { StatusNps } from './nps.model';
import { NpsService } from './nps.service';

/** Mantém o estado da pesquisa de NPS ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class NpsBuscaStore {
  status: StatusNps | null = null;
  pacienteId: number | null = null;
  unidadeId: number | null = null;
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = NpsService.TAMANHO_PADRAO;

  /** Limpa apenas os filtros (a ordenação tem o próprio "Limpar ordenação"). */
  limpar(): void {
    this.status = null;
    this.pacienteId = null;
    this.unidadeId = null;
    this.page = 0;
    this.size = NpsService.TAMANHO_PADRAO;
  }
}
