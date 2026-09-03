import { router } from 'expo-router';

/**
 * Leva o paciente à tela de origem de uma notificação. Compartilhado pelo toque
 * na notificação push (_layout) e pelo toque na lista de "Notificações". O `id` é
 * a referência quando faz sentido navegar direto (conversa, manifestação SAU,
 * postagem); nos demais tipos abrimos apenas a aba correspondente.
 */
export function navegarNotificacao(tipo: string | undefined, id: number | null | undefined): void {
  switch (tipo) {
    case 'AGENDAMENTO':
    case 'FALTA':
      // Falta registrada: leva à lista de agendamentos para justificar.
      router.navigate('/(tabs)/agendamentos');
      break;
    case 'CHAT':
      if (id != null) router.navigate({ pathname: '/conversa/[id]', params: { id: String(id) } });
      else router.navigate('/(tabs)/chat');
      break;
    case 'NPS':
      router.navigate('/(tabs)/nps');
      break;
    case 'SAU':
      if (id != null) router.navigate({ pathname: '/sau/[id]', params: { id: String(id) } });
      else router.navigate('/(tabs)/sau');
      break;
    case 'PRONTUARIO':
      router.navigate('/(tabs)/prontuario');
      break;
    case 'POSTAGEM':
      if (id != null) router.navigate({ pathname: '/postagem/[id]', params: { id: String(id) } });
      else router.navigate('/(tabs)/novidades');
      break;
  }
}
