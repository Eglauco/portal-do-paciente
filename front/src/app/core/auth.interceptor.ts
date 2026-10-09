import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

/**
 * Injeta o token JWT (Bearer) nas chamadas à API e, ao receber 401, encerra a
 * sessão e leva o usuário de volta ao login.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const ehApi = req.url.startsWith(environment.apiUrl);
  const ehLogin = req.url.includes('/auth/login');
  // Console do super-admin: fica FORA da sessão do admin — não leva o Bearer e um 401 (chave errada)
  // é tratado na própria tela, nunca derruba a sessão do admin para o /login.
  const ehSuperadmin = req.url.includes('/superadmin/');
  const token = auth.token();

  const requisicao =
    token && ehApi && !ehLogin && !ehSuperadmin
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(requisicao).pipe(
    catchError((erro: HttpErrorResponse) => {
      if (erro.status === 401 && ehApi && !ehLogin && !ehSuperadmin) {
        auth.logout();
      }
      return throwError(() => erro);
    }),
  );
};
