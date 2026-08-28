import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  // Rotas com parâmetro: renderizadas sob demanda no servidor (não pré-renderizadas).
  {
    path: 'usuarios/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'unidades/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'pacientes/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'agendamentos/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'chats/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'nps/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'categorias-nps/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'especialidades/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'profissionais/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'procedimentos/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'motivos-falta/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'prontuarios/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: 'postagens/:id',
    renderMode: RenderMode.Server,
  },
  {
    path: '**',
    renderMode: RenderMode.Prerender,
  },
];
