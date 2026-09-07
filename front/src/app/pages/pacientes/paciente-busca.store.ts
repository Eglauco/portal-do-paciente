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
  /** Situação do cadastro: Ativos (padrão), Inativos ou Todos. */
  situacao: 'ATIVO' | 'INATIVO' | 'TODOS' = 'ATIVO';
  page = 0;
  size = PacienteService.TAMANHO_PADRAO;

  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.cpf = '';
    this.prontuario = '';
    this.situacao = 'ATIVO';
    this.page = 0;
    this.size = PacienteService.TAMANHO_PADRAO;
  }
}
