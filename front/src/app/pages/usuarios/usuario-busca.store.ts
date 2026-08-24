import { Injectable } from '@angular/core';
import { UsuarioService } from './usuario.service';

/**
 * Mantém o último estado da pesquisa de usuários (filtros, página e tamanho)
 * para que ele seja preservado ao sair da listagem e voltar.
 */
@Injectable({ providedIn: 'root' })
export class UsuarioBuscaStore {
  codigo = '';
  nome = '';
  email = '';
  page = 0;
  size = UsuarioService.TAMANHO_PADRAO;

  /** Restaura os valores padrão (usado pelo botão "Limpar"). */
  limpar(): void {
    this.codigo = '';
    this.nome = '';
    this.email = '';
    this.page = 0;
    this.size = UsuarioService.TAMANHO_PADRAO;
  }
}
