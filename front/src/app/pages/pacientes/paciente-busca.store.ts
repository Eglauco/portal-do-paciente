import { Injectable } from '@angular/core';
import { PacienteService } from './paciente.service';

/**
 * Mantém o último estado da pesquisa de pacientes (filtros, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class PacienteBuscaStore {
  codigo = '';
  nome = '';
  cpf = '';
  prontuario = '';
  page = 0;
  size = PacienteService.TAMANHO_PADRAO;

  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.cpf = '';
    this.prontuario = '';
    this.page = 0;
    this.size = PacienteService.TAMANHO_PADRAO;
  }
}
