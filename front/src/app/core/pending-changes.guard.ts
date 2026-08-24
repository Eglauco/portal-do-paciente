import { CanDeactivateFn } from '@angular/router';

/** Componentes que podem interceptar a saída da rota implementam esta interface. */
export interface PodeSair {
  podeSair(): boolean | Promise<boolean>;
}

/**
 * Guard que consulta o componente antes de deixar a rota — usado para confirmar
 * a saída quando há dados preenchidos e não salvos.
 */
export const pendingChangesGuard: CanDeactivateFn<PodeSair> = (component) => {
  return component?.podeSair ? component.podeSair() : true;
};
