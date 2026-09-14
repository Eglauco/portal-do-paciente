import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { MotivoFaltaService } from './motivo-falta.service';

/**
 * Mantém o último estado da pesquisa de motivos de falta (filtros, ordenação, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class MotivoFaltaBuscaStore {
  codigo = '';
  motivo = '';
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = MotivoFaltaService.TAMANHO_PADRAO;

  /** Limpa apenas os filtros (a ordenação tem o próprio "Limpar ordenação"). */
  limpar(): void {
    this.codigo = '';
    this.motivo = '';
    this.page = 0;
    this.size = MotivoFaltaService.TAMANHO_PADRAO;
  }
}
