import { Routes } from '@angular/router';
import { pendingChangesGuard } from './core/pending-changes.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
    title: 'Entrar — Portal do Paciente · Admin',
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: '',
    loadComponent: () => import('./pages/shell/shell').then((m) => m.Shell),
    children: [
      {
        path: 'inicio',
        loadComponent: () => import('./pages/home/home').then((m) => m.Home),
        title: 'Início — Portal do Paciente · Admin',
      },
      {
        path: 'usuarios',
        loadComponent: () => import('./pages/usuarios/usuarios-list').then((m) => m.UsuariosList),
        title: 'Usuários — Portal do Paciente · Admin',
      },
      {
        path: 'usuarios/novo',
        loadComponent: () => import('./pages/usuarios/usuario-form').then((m) => m.UsuarioForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Novo usuário — Portal do Paciente · Admin',
      },
      {
        path: 'usuarios/:id',
        loadComponent: () => import('./pages/usuarios/usuario-form').then((m) => m.UsuarioForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Editar usuário — Portal do Paciente · Admin',
      },
      { path: 'unidades', ...secao('Unidades de Saúde', 'Cadastre e organize as unidades da rede.') },
      { path: 'agendamentos', ...secao('Agendamentos', 'Acompanhe e organize a agenda de atendimentos.') },
      { path: 'chats', ...secao('Chats ao vivo', 'Atenda pacientes em tempo real.') },
      { path: 'nps', ...secao('NPS', 'Acompanhe a satisfação dos pacientes.') },
      { path: 'pacientes', ...secao('Pacientes', 'Consulte e mantenha o cadastro dos pacientes.') },
      { path: 'prontuarios', ...secao('Prontuários', 'Acesse o histórico clínico dos pacientes.') },
      { path: 'configuracoes', ...secao('Configurações', 'Ajuste as preferências do sistema.') },
      { path: 'perfis', ...secao('Perfis', 'Defina perfis de acesso e níveis de permissão.') },
      { path: '', redirectTo: 'inicio', pathMatch: 'full' },
    ],
  },
  { path: '**', redirectTo: 'login' },
];

function secao(title: string, description: string) {
  return {
    loadComponent: () => import('./pages/placeholder/placeholder').then((m) => m.Placeholder),
    title: `${title} — Portal do Paciente · Admin`,
    data: { title, description },
  };
}
