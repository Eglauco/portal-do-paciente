import { Injectable, effect, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { MarcaService } from './marca.service';

/**
 * Título das abas do navegador com o nome da plataforma configurável (white-label). Cada rota
 * define só o nome da PÁGINA (ex.: "Agendamentos"); esta estratégia monta
 * "{página} — {nome} · Admin" usando o nome vindo da MarcaService. Um effect reaplica o título
 * da página atual quando o nome carrega/muda (o GET /marca resolve depois do 1º render).
 */
@Injectable()
export class MarcaTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly marca = inject(MarcaService);
  private ultimo: RouterStateSnapshot | null = null;

  constructor() {
    super();
    effect(() => {
      this.marca.nomePlataforma(); // dependência reativa
      if (this.ultimo) this.aplicar(this.ultimo);
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.ultimo = snapshot;
    this.aplicar(snapshot);
  }

  private aplicar(snapshot: RouterStateSnapshot): void {
    const pagina = this.buildTitle(snapshot);
    const nome = this.marca.nomePlataforma();
    this.title.setTitle(pagina ? `${pagina} — ${nome} · Admin` : `${nome} · Admin`);
  }
}
