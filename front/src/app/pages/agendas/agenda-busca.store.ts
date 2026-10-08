import { Injectable } from '@angular/core';
import { PacienteSelecao } from '../pacientes/paciente.model';
import { AgendaService } from './agenda.service';

/**
 * Mantém o último estado da tela de Agendamentos (modo + filtros da lista de agendas + paciente/página da
 * visão "por paciente") para que, ao sair (abrir um horário) e voltar, a tela reabra onde estava — igual ao
 * {@code PacienteBuscaStore} da lista de pacientes. É singleton (providedIn root), então sobrevive à navegação.
 */
@Injectable({ providedIn: 'root' })
export class AgendaBuscaStore {
  /** Modo ativo da tela. */
  modo: 'agenda' | 'paciente' = 'agenda';

  // --- Modo "por agenda" (lista de slots) ---
  data = '';
  profissionalNome = '';
  especialidadeNome = '';
  page = 0;
  size = AgendaService.TAMANHO_PADRAO;

  // --- Modo "por paciente" (marcações) ---
  paciente: PacienteSelecao | null = null;
  pacientePage = 0;
}
