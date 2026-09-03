/**
 * Barramento de eventos simples para "atualizar a tela em foco".
 *
 * Quando chega uma notificação push com o app aberto — ou quando o app volta
 * ao primeiro plano — disparamos `notificarAtualizacao()`. Apenas a tela que
 * está em foco assina (via hook use-atualizar-com-push) e recarrega os seus
 * dados silenciosamente, sem exigir um "puxar para atualizar" manual.
 */
const ouvintes = new Set<() => void>();

/** Assina o barramento; devolve a função de cancelamento. */
export function assinarAtualizacao(callback: () => void): () => void {
  ouvintes.add(callback);
  return () => {
    ouvintes.delete(callback);
  };
}

/** Notifica todos os assinantes (normalmente só a tela em foco está assinada). */
export function notificarAtualizacao(): void {
  ouvintes.forEach((cb) => cb());
}
