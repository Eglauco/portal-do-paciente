import { isPlatformBrowser } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Protege as rotas administrativas. No servidor (SSR/prerender) não bloqueia —
 * a checagem real acontece no navegador, onde o token está disponível.
 * Sem token → login; logado sem unidade ativa → tela de seleção de unidade.
 */
export const authGuard: CanActivateFn = () => {
  const platformId = inject(PLATFORM_ID);
  if (!isPlatformBrowser(platformId)) return true;

  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.token()) return router.createUrlTree(['/login']);
  if (auth.unidadeId() == null) return router.createUrlTree(['/selecionar-unidade']);
  return true;
};

/**
 * Guarda a tela de seleção de unidade: só faz sentido para quem está logado e
 * ainda não tem unidade ativa (senão manda para o início / login).
 */
export const selecionarUnidadeGuard: CanActivateFn = () => {
  const platformId = inject(PLATFORM_ID);
  if (!isPlatformBrowser(platformId)) return true;

  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.token()) return router.createUrlTree(['/login']);
  if (auth.unidadeId() != null) return router.createUrlTree(['/inicio']);
  return true;
};
