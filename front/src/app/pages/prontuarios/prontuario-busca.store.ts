import { Injectable } from '@angular/core';
import { ProntuarioService } from './prontuario.service';

/** Mantém o estado da pesquisa de prontuários ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class ProntuarioBuscaStore {
  numero = '';
  pacienteId: number | null = null;
  page = 0;
  size = ProntuarioService.TAMANHO_PADRAO;

  limpar(): void {
    this.numero = '';
    this.pacienteId = null;
    this.page = 0;
    this.size = ProntuarioService.TAMANHO_PADRAO;
  }
}
