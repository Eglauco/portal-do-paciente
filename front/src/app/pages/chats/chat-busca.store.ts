import { Injectable } from '@angular/core';
import { StatusChat } from './chat.model';
import { ChatService } from './chat.service';

/** Mantém o estado da pesquisa de chats ao sair da listagem e voltar. */
@Injectable({ providedIn: 'root' })
export class ChatBuscaStore {
  pacienteId: number | null = null;
  unidadeId: number | null = null;
  status: StatusChat | null = null;
  naoResolvidas = false;
  page = 0;
  size = ChatService.TAMANHO_PADRAO;

  limpar(): void {
    this.pacienteId = null;
    this.unidadeId = null;
    this.status = null;
    this.naoResolvidas = false;
    this.page = 0;
    this.size = ChatService.TAMANHO_PADRAO;
  }
}
