import { Injectable } from '@angular/core';
import { Ordenacao } from '../../shared/ordenacao/ordenacao.model';
import { StatusAlertaProntuario } from './prontuario.model';
import { ProntuarioService } from './prontuario.service';

/** Mantém o estado da pesquisa de prontuários ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class ProntuarioBuscaStore {
  numero = '';
  pacienteId: number | null = null;
  especialidade = '';
  status: StatusAlertaProntuario | null = null;
  /** Ordenação multi-coluna escolhida nos cabeçalhos (vazia = padrão do backend). */
  ordenacoes: Ordenacao[] = [];
  page = 0;
  size = ProntuarioService.TAMANHO_PADRAO;

  /** Limpa apenas os filtros (a ordenação tem o próprio "Limpar ordenação"). */
  limpar(): void {
    this.numero = '';
    this.pacienteId = null;
    this.especialidade = '';
    this.status = null;
    this.page = 0;
    this.size = ProntuarioService.TAMANHO_PADRAO;
  }
}
