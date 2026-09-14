import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, TitleStrategy } from '@angular/router';
import { routes } from './app.routes';
import { provideClientHydration } from '@angular/platform-browser';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideToastr } from 'ngx-toastr';
import { provideEnvironmentNgxMask } from 'ngx-mask';
import { authInterceptor } from './core/auth.interceptor';
import { TemaService } from './core/tema.service';
import { MarcaService } from './core/marca.service';
import { MarcaTitleStrategy } from './core/marca-title.strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideClientHydration(),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    provideAnimations(),
    provideEnvironmentNgxMask(),
    // Aplica a cor da plataforma (GET /tema) no boot — não bloqueia (cache pinta na hora).
    provideAppInitializer(() => inject(TemaService).aplicar()),
    // Carrega a marca (nome + textos do login) via GET /marca no boot — mesma estratégia do tema.
    provideAppInitializer(() => inject(MarcaService).carregar()),
    // Título das abas usa o nome configurável (white-label).
    { provide: TitleStrategy, useClass: MarcaTitleStrategy },
    provideToastr({
      positionClass: 'toast-top-right',
      timeOut: 3000,
      progressBar: true,
      closeButton: true,
      preventDuplicates: true,
    }),
  ],
};
