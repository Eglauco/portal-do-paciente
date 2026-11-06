import { Linking, Platform } from 'react-native';

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

/** App de mapa oferecido no seletor "Como chegar". */
export interface OpcaoMapa {
  chave: 'google' | 'waze' | 'apple';
  nome: string;
  /** Nome do ícone Ionicons exibido na linha do seletor. */
  icone: string;
  /** URL universal: abre o app instalado (App Link/Universal Link); se não houver, cai no navegador. */
  url: string;
}

/** Alvo do deep link: coordenada "lat,lng" (preferida, mais precisa) ou a string do endereço. */
function alvoMapa(endereco: string, latitude?: number | null, longitude?: number | null): string {
  if (typeof latitude === 'number' && typeof longitude === 'number') return `${latitude},${longitude}`;
  return endereco;
}

/**
 * Opções de "Como chegar" conforme a plataforma. Usa URLs UNIVERSAIS (App Links/Universal Links):
 * abrem o app instalado e, se não houver, caem no navegador — sem precisar de `canOpenURL` (logo sem
 * declarar schemes no Info.plist/AndroidManifest). Prefere coordenadas quando a unidade foi
 * geocodificada; senão usa o endereço. Apple Maps só entra no iOS.
 */
export function opcoesComoChegar(
  endereco: string,
  latitude?: number | null,
  longitude?: number | null,
): OpcaoMapa[] {
  const alvo = encodeURIComponent(alvoMapa(endereco, latitude, longitude));
  const temCoord = typeof latitude === 'number' && typeof longitude === 'number';
  const wazeAlvo = temCoord
    ? `ll=${encodeURIComponent(`${latitude},${longitude}`)}`
    : `q=${encodeURIComponent(endereco)}`;

  const opcoes: OpcaoMapa[] = [
    {
      chave: 'google',
      nome: 'Google Maps',
      icone: 'navigate',
      url: `https://www.google.com/maps/dir/?api=1&destination=${alvo}`,
    },
    {
      chave: 'waze',
      nome: 'Waze',
      icone: 'car-sport',
      url: `https://waze.com/ul?${wazeAlvo}&navigate=yes`,
    },
  ];
  // Apple Maps só existe no iOS; no Android as opções são Google Maps e Waze.
  if (Platform.OS === 'ios') {
    opcoes.push({
      chave: 'apple',
      nome: 'Mapas (Apple)',
      icone: 'map',
      url: `https://maps.apple.com/?daddr=${alvo}`,
    });
  }
  return opcoes;
}

/** Abre a rota no app escolhido (handoff externo pro SO; o `.catch` cobre falha de abertura). */
export const abrirEmApp = (opcao: OpcaoMapa) => Linking.openURL(opcao.url).catch(() => {});
