import { useFocusEffect } from 'expo-router';
import { useCallback, useRef } from 'react';

import { assinarAtualizacao } from '@/services/atualizacao';

/**
 * Recarrega a tela EM FOCO quando chega uma notificação push (app aberto) ou
 * quando o app volta ao primeiro plano. Só a tela focada fica assinada, então
 * apenas ela se atualiza; as demais se recompõem sozinhas ao ganhar foco
 * (useFocusEffect).
 *
 * O `recarregar` pode mudar de identidade a cada render — guardamos numa ref e
 * assinamos um wrapper estável, para não refazer a assinatura à toa.
 */
export function useAtualizarComPush(recarregar: () => void): void {
  const recarregarRef = useRef(recarregar);
  recarregarRef.current = recarregar;

  useFocusEffect(
    useCallback(() => {
      const off = assinarAtualizacao(() => recarregarRef.current());
      return off;
    }, []),
  );
}
