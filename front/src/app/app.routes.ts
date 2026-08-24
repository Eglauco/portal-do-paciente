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
      {
        path: 'unidades',
        loadComponent: () => import('./pages/unidades/unidades-list').then((m) => m.UnidadesList),
        title: 'Unidades de Saúde — Portal do Paciente · Admin',
      },
      {
        path: 'unidades/novo',
        loadComponent: () => import('./pages/unidades/unidade-form').then((m) => m.UnidadeForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Nova unidade — Portal do Paciente · Admin',
      },
      {
        path: 'unidades/:id',
        loadComponent: () => import('./pages/unidades/unidade-form').then((m) => m.UnidadeForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Editar unidade — Portal do Paciente · Admin',
      },
      {
        path: 'agendamentos',
        loadComponent: () => import('./pages/agendamentos/agendamentos-list').then((m) => m.AgendamentosList),
        title: 'Agendamentos — Portal do Paciente · Admin',
      },
      {
        path: 'agendamentos/novo',
        loadComponent: () => import('./pages/agendamentos/agendamento-form').then((m) => m.AgendamentoForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Novo agendamento — Portal do Paciente · Admin',
      },
      {
        path: 'agendamentos/:id',
        loadComponent: () => import('./pages/agendamentos/agendamento-form').then((m) => m.AgendamentoForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Editar agendamento — Portal do Paciente · Admin',
      },
      {
        path: 'chats',
        loadComponent: () => import('./pages/chats/chats-list').then((m) => m.ChatsList),
        title: 'Chats ao vivo — Portal do Paciente · Admin',
      },
      {
        path: 'chats/:id',
        loadComponent: () => import('./pages/chats/chat-conversa').then((m) => m.ChatConversa),
        title: 'Conversa — Portal do Paciente · Admin',
      },
      {
        path: 'nps',
        loadComponent: () => import('./pages/nps/nps-list').then((m) => m.NpsList),
        title: 'NPS — Portal do Paciente · Admin',
      },
      {
        path: 'nps/:id',
        loadComponent: () => import('./pages/nps/nps-detalhe').then((m) => m.NpsDetalheComponent),
        title: 'Avaliação — Portal do Paciente · Admin',
      },
      {
        path: 'pacientes',
        loadComponent: () => import('./pages/pacientes/pacientes-list').then((m) => m.PacientesList),
        title: 'Pacientes — Portal do Paciente · Admin',
      },
      {
        path: 'pacientes/novo',
        loadComponent: () => import('./pages/pacientes/paciente-form').then((m) => m.PacienteForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Novo paciente — Portal do Paciente · Admin',
      },
      {
        path: 'pacientes/:id',
        loadComponent: () => import('./pages/pacientes/paciente-form').then((m) => m.PacienteForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Editar paciente — Portal do Paciente · Admin',
      },
      {
        path: 'especialidades',
        loadComponent: () => import('./pages/especialidades/especialidades-list').then((m) => m.EspecialidadesList),
        title: 'Especialidades — Portal do Paciente · Admin',
      },
      {
        path: 'especialidades/novo',
        loadComponent: () => import('./pages/especialidades/especialidade-form').then((m) => m.EspecialidadeForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Nova especialidade — Portal do Paciente · Admin',
      },
      {
        path: 'especialidades/:id',
        loadComponent: () => import('./pages/especialidades/especialidade-form').then((m) => m.EspecialidadeForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Editar especialidade — Portal do Paciente · Admin',
      },
      {
        path: 'profissionais',
        loadComponent: () => import('./pages/profissionais/profissionais-list').then((m) => m.ProfissionalSaudesList),
        title: 'Profissionais — Portal do Paciente · Admin',
      },
      {
        path: 'profissionais/novo',
        loadComponent: () => import('./pages/profissionais/profissional-form').then((m) => m.ProfissionalSaudeForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Novo profissional — Portal do Paciente · Admin',
      },
      {
        path: 'profissionais/:id',
        loadComponent: () => import('./pages/profissionais/profissional-form').then((m) => m.ProfissionalSaudeForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Editar profissional — Portal do Paciente · Admin',
      },
      {
        path: 'procedimentos',
        loadComponent: () => import('./pages/procedimentos/procedimentos-list').then((m) => m.ProcedimentosList),
        title: 'Procedimentos — Portal do Paciente · Admin',
      },
      {
        path: 'procedimentos/novo',
        loadComponent: () => import('./pages/procedimentos/procedimento-form').then((m) => m.ProcedimentoForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Novo procedimento — Portal do Paciente · Admin',
      },
      {
        path: 'procedimentos/:id',
        loadComponent: () => import('./pages/procedimentos/procedimento-form').then((m) => m.ProcedimentoForm),
        canDeactivate: [pendingChangesGuard],
        title: 'Editar procedimento — Portal do Paciente · Admin',
      },
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
