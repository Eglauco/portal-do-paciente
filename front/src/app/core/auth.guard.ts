import { isPlatformBrowser } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Protege as rotas administrativas. No servidor (SSR/prerender) não bloqueia —
 * a checagem real acontece no navegador, onde o token está disponível.
 */
export const authGuard: CanActivateFn = () => {
  const platformId = inject(PLATFORM_ID);
  if (!isPlatformBrowser(platformId)) return true;

  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.token() ? true : router.createUrlTree(['/login']);
};
