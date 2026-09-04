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
 * Bloqueia o acesso direto (deep-link) a uma tela que o perfil do usuário não
 * libera — os menus já ficam escondidos, isto é a defesa da rota. Barrado → cai
 * na primeira tela liberada. No servidor não bloqueia (sem token no SSR).
 */
export function telaGuard(chave: string): CanActivateFn {
  return () => {
    const platformId = inject(PLATFORM_ID);
    if (!isPlatformBrowser(platformId)) return true;

    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.token()) return router.createUrlTree(['/login']);
    if (auth.unidadeId() == null) return router.createUrlTree(['/selecionar-unidade']);
    // Sem acesso à tela: mostra a mensagem de permissão negada (não redireciona escondido).
    if (!auth.temTela(chave)) return router.createUrlTree(['/sem-permissao']);
    return true;
  };
}

/** Rota "início": redireciona para a primeira tela liberada do usuário. */
export const inicioGuard: CanActivateFn = () => {
  const platformId = inject(PLATFORM_ID);
  if (!isPlatformBrowser(platformId)) return true;

  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.token()) return router.createUrlTree(['/login']);
  return router.createUrlTree([auth.rotaInicial()]);
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
