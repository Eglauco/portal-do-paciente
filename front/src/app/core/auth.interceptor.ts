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
  const token = auth.token();

  const requisicao =
    token && ehApi && !ehLogin
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(requisicao).pipe(
    catchError((erro: HttpErrorResponse) => {
      if (erro.status === 401 && ehApi && !ehLogin) {
        auth.logout();
      }
      return throwError(() => erro);
    }),
  );
};
