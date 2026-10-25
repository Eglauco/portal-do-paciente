import { fetchMeu } from '@/services/sessao';

/** Dia da semana como o backend serializa (java.time.DayOfWeek). */
export type DiaSemana =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

/**
 * Horário de um dia. `fechado`: não abre. `vinteQuatroHoras`: aberta o dia inteiro. Nos dois casos
 * abertura/fechamento ficam nulos. Hora no formato "HH:mm:ss".
 */
export interface HorarioUnidade {
  diaSemana: DiaSemana;
  fechado: boolean;
  vinteQuatroHoras: boolean;
  abertura?: string | null;
  fechamento?: string | null;
}

/** Perfil da unidade (estilo perfil de empresa do WhatsApp). logo/capa já vêm com URL assinada. */
export interface PerfilUnidade {
  id: number;
  nome: string;
  logoUrl?: string | null;
  capaUrl?: string | null;
  sobre?: string | null;
  telefone?: string | null;
  endereco?: string | null;
  /** Coordenadas do endereço (geocodificadas no backend); nulas quando não há mapa a exibir. */
  latitude?: number | null;
  longitude?: number | null;
  horarios: HorarioUnidade[];
}

/** Busca o perfil da unidade para exibir ao tocar no nome dela na conversa. */
export async function buscarPerfilUnidade(id: number | string): Promise<PerfilUnidade> {
  const resposta = await fetchMeu(`/meu/unidades/${id}`);
  if (!resposta.ok) {
    throw new Error(`Falha ao carregar a unidade (${resposta.status})`);
  }
  return resposta.json() as Promise<PerfilUnidade>;
}
