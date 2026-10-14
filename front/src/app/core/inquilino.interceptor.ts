import { HttpInterceptorFn } from '@angular/common/http';
import { isPlatformBrowser } from '@angular/common';
import { inject, PLATFORM_ID } from '@angular/core';
import { environment } from '../../environments/environment';

/**
 * Domínio dedicado dos fronts das clínicas. Cada clínica é um subdomínio de 1º nível
 * (ex.: nascente.trilharsaude.app.br). Em dev (localhost) não casa → sem X-Inquilino → branding da
 * plataforma. Se o domínio mudar, ajuste aqui.
 */
const DOMINIO_BASE = 'trilharsaude.app.br';

/** Extrai o rótulo do subdomínio da clínica do hostname; null no apex, em 'www', fora do domínio ou no SSR. */
function rotuloInquilino(hostname: string): string | null {
  const sufixo = '.' + DOMINIO_BASE;
  if (!hostname.endsWith(sufixo)) return null;
  const rotulo = hostname.slice(0, -sufixo.length).toLowerCase();
  if (!rotulo || rotulo === 'www' || rotulo.includes('.')) return null; // só 1º nível
  return rotulo;
}

/**
 * Injeta o header {@code X-Inquilino} (rótulo do subdomínio) nas chamadas à API, para o backend
 * resolver a clínica ANTES do login (branding da tela de login do back-office). Só roda no navegador
 * (o SSR/prerender não tem o hostname da clínica). Pós-login o backend IGNORA o header: o claim "inq"
 * do JWT sempre manda.
 */
export const inquilinoInterceptor: HttpInterceptorFn = (req, next) => {
  if (!isPlatformBrowser(inject(PLATFORM_ID))) return next(req);
  if (!req.url.startsWith(environment.apiUrl)) return next(req);
  const rotulo = rotuloInquilino(window.location.hostname);
  if (!rotulo) return next(req);
  return next(req.clone({ setHeaders: { 'X-Inquilino': rotulo } }));
};
