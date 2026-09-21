/**
 * Catálogo central das funcionalidades (itens de menu) da plataforma. É a fonte
 * usada pela busca "Buscar Funcionalidades" (Alt+1). Cada item aponta para a
 * chave da tela (enum Tela) — a busca só mostra as que o usuário tem acesso
 * (AuthService.temTela), refletindo os perfis liberados.
 *
 * Mantém paridade com o menu lateral (shell.html): mesmos rótulos, rotas e ícones.
 */
export interface Funcionalidade {
  /** Chave da tela (enum Tela) — usada para checar acesso. */
  readonly tela: string;
  /** Rótulo exibido (igual ao item de menu). */
  readonly rotulo: string;
  /** Rota de navegação. */
  readonly rota: string;
  /** Categoria (rótulo discreto + reforço de busca). */
  readonly grupo: string;
  /** Ícone inline (SVG confiável, estático). */
  readonly icone: string;
  /** Sinônimos/termos extras para a busca encontrar por significado. */
  readonly palavras?: readonly string[];
}

/** Envolve o miolo do ícone no <svg> padrão do menu. */
const svg = (inner: string): string =>
  `<svg viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${inner}</svg>`;

const ICONE = {
  dashboard: svg('<rect x="3" y="3" width="8" height="9" rx="1.5"/><rect x="13" y="3" width="8" height="5" rx="1.5"/><rect x="13" y="10" width="8" height="11" rx="1.5"/><rect x="3" y="14" width="8" height="7" rx="1.5"/>'),
  agenda: svg('<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 9h18"/><path d="M8 3v4M16 3v4"/><path d="M8 14l2.5 2.5L15 12"/>'),
  chat: svg('<path d="M4 5h16a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H9l-4 3v-3H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"/><path d="M8 10v0M12 10v0M16 10v0"/>'),
  sau: svg('<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/><path d="M12 7v4"/><path d="M12 14h.01"/>'),
  estrela: svg('<path d="M12 3l2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8L3.5 9.2l5.9-.9z"/>'),
  usuarios: svg('<circle cx="9" cy="8" r="3.2"/><path d="M3.5 20a5.5 5.5 0 0 1 11 0"/><path d="M16 6.2a3 3 0 0 1 0 5.6"/><path d="M17.5 20a5 5 0 0 0-3-4.6"/>'),
  predio: svg('<path d="M4 21V6a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v15"/><path d="M14 10h4a2 2 0 0 1 2 2v9"/><path d="M3 21h18"/><path d="M9 7v0M9 11v0M9 15v0"/><path d="M8 4.5h2M9 3.5v2"/>'),
  tag: svg('<path d="M20.6 13.4 12 22l-9-9V4a1 1 0 0 1 1-1h9z"/><circle cx="7.5" cy="7.5" r="1.2"/>'),
  lista: svg('<path d="M4 6h16M4 12h16M4 18h10"/><path d="M18 15l1.5 1.5L22 14"/>'),
  paciente: svg('<circle cx="12" cy="8" r="3.2"/><path d="M6 20a6 6 0 0 1 12 0"/><path d="M12 11.5c1.2 1.4 3 1.2 3-.2"/>'),
  especialidade: svg('<path d="M8 3v4a4 4 0 0 0 8 0V3"/><path d="M6 7a6 6 0 0 0 12 0"/><path d="M12 11v4a5 5 0 0 0 5 5 3 3 0 1 0 0-6"/><circle cx="6" cy="18" r="2"/>'),
  profissional: svg('<circle cx="12" cy="7" r="3.2"/><path d="M6 21a6 6 0 0 1 12 0"/><path d="M12 13v3M10.5 14.5h3"/>'),
  procedimento: svg('<path d="M9 3h6v4H9z"/><rect x="4" y="7" width="16" height="14" rx="2"/><path d="M12 11v6M9 14h6"/>'),
  alerta: svg('<circle cx="12" cy="12" r="9"/><path d="M12 8v5M12 16h.01"/>'),
  prontuario: svg('<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 3h6v3H9z"/><path d="M12 10v5M9.5 12.5h5"/>'),
  prontuarioMedico: svg('<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M9 3h6v3H9z"/><path d="M8 13h2l1.2-2.5L13 15l1-2h2"/>'),
  tipoDoc: svg('<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M9 13h6M9 17h4"/>'),
  social: svg('<rect x="3" y="3" width="18" height="18" rx="4"/><circle cx="12" cy="12" r="3.2"/><circle cx="17" cy="7" r="1"/>'),
  engrenagem: svg('<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.6 1.6 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.6 1.6 0 0 0-2.7 1.1V21a2 2 0 0 1-4 0v-.1A1.6 1.6 0 0 0 7 19.4a1.6 1.6 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1A1.6 1.6 0 0 0 2.6 15H2.5a2 2 0 0 1 0-4h.1a1.6 1.6 0 0 0 1.1-2.7 1.6 1.6 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1A1.6 1.6 0 0 0 9 4.6h.1A2 2 0 0 1 11 2.5"/>'),
  perfis: svg('<rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="8.5" cy="11" r="2"/><path d="M5.5 16a3.2 3.2 0 0 1 6 0"/><path d="M14 10h4M14 13h3"/>'),
  ia: svg('<path d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M18 6l-2.5 2.5M8.5 15.5 6 18"/><circle cx="12" cy="12" r="3"/>'),
} as const;

export const FUNCIONALIDADES: readonly Funcionalidade[] = [
  // Dashboards (uma tela por dashboard).
  { tela: 'DASHBOARD_GERAL', rotulo: 'Dashboard · Visão geral', rota: '/dashboards/geral', grupo: 'Dashboards', icone: ICONE.dashboard, palavras: ['painel', 'indicadores', 'gráficos', 'resumo'] },
  { tela: 'DASHBOARD_AGENDAMENTOS', rotulo: 'Dashboard · Agendamentos', rota: '/dashboards/agendamentos', grupo: 'Dashboards', icone: ICONE.agenda, palavras: ['painel', 'indicadores', 'consultas'] },
  { tela: 'DASHBOARD_CHATS', rotulo: 'Dashboard · Chats ao vivo', rota: '/dashboards/chats', grupo: 'Dashboards', icone: ICONE.chat, palavras: ['painel', 'atendimento', 'conversas'] },
  { tela: 'DASHBOARD_SAU', rotulo: 'Dashboard · SAU', rota: '/dashboards/sau', grupo: 'Dashboards', icone: ICONE.sau, palavras: ['painel', 'ouvidoria', 'manifestações'] },
  { tela: 'DASHBOARD_NPS', rotulo: 'Dashboard · NPS', rota: '/dashboards/nps', grupo: 'Dashboards', icone: ICONE.estrela, palavras: ['painel', 'satisfação', 'nota'] },

  // Atendimento.
  { tela: 'AGENDAMENTOS', rotulo: 'Agendamentos', rota: '/agendamentos', grupo: 'Atendimento', icone: ICONE.agenda, palavras: ['consulta', 'agenda', 'marcação', 'horário'] },
  { tela: 'CHATS', rotulo: 'Chats ao vivo', rota: '/chats', grupo: 'Atendimento', icone: ICONE.chat, palavras: ['conversa', 'mensagem', 'atendimento'] },
  { tela: 'SAU', rotulo: 'SAU', rota: '/sau', grupo: 'Atendimento', icone: ICONE.sau, palavras: ['ouvidoria', 'manifestação', 'reclamação', 'elogio', 'sugestão'] },
  { tela: 'TIPOS_MANIFESTACAO', rotulo: 'Tipos de Manifestação', rota: '/tipos-manifestacao', grupo: 'Atendimento', icone: ICONE.tag, palavras: ['sau', 'categorias', 'ouvidoria'] },

  // Satisfação.
  { tela: 'NPS', rotulo: 'NPS', rota: '/nps', grupo: 'Satisfação', icone: ICONE.estrela, palavras: ['pesquisa', 'satisfação', 'nota', 'avaliação'] },
  { tela: 'CATEGORIAS_NPS', rotulo: 'Categorias de NPS', rota: '/categorias-nps', grupo: 'Satisfação', icone: ICONE.lista, palavras: ['pesquisa', 'satisfação', 'grupos'] },

  // Pacientes.
  { tela: 'PACIENTES', rotulo: 'Pacientes', rota: '/pacientes', grupo: 'Pacientes', icone: ICONE.paciente, palavras: ['cadastro', 'pessoas', 'cpf', 'prontuário'] },
  { tela: 'PRONTUARIOS', rotulo: 'Prontuários', rota: '/prontuarios', grupo: 'Pacientes', icone: ICONE.prontuario, palavras: ['histórico', 'atendimentos', 'evolução'] },
  { tela: 'PRONTUARIO_MEDICO', rotulo: 'Prontuário Médico', rota: '/prontuario-medico', grupo: 'Pacientes', icone: ICONE.prontuarioMedico, palavras: ['histórico', 'linha do tempo', 'médico', 'resumo', 'ia', 'timeline', 'atendimentos'] },
  { tela: 'PRONTUARIOS', rotulo: 'Tipos de Documento', rota: '/tipos-documento-prontuario', grupo: 'Pacientes', icone: ICONE.tipoDoc, palavras: ['prontuário', 'documento', 'ia', 'análise', 'resumo', 'prompt'] },

  // Cadastros.
  { tela: 'ESPECIALIDADES', rotulo: 'Especialidades', rota: '/especialidades', grupo: 'Cadastros', icone: ICONE.especialidade, palavras: ['cadastro', 'áreas', 'cardiologia'] },
  { tela: 'PROFISSIONAIS', rotulo: 'Profissionais', rota: '/profissionais', grupo: 'Cadastros', icone: ICONE.profissional, palavras: ['médico', 'profissional de saúde', 'cadastro'] },
  { tela: 'CONSELHOS', rotulo: 'Conselhos', rota: '/conselhos', grupo: 'Cadastros', icone: ICONE.estrela, palavras: ['crm', 'coren', 'classe', 'conselho'] },
  { tela: 'PROCEDIMENTOS', rotulo: 'Procedimentos', rota: '/procedimentos', grupo: 'Cadastros', icone: ICONE.procedimento, palavras: ['exame', 'serviço', 'cadastro'] },
  { tela: 'MOTIVOS_FALTA', rotulo: 'Motivos de falta', rota: '/motivos-falta', grupo: 'Cadastros', icone: ICONE.alerta, palavras: ['ausência', 'falta', 'justificativa'] },

  // Conteúdo.
  { tela: 'POSTAGENS', rotulo: 'Rede Social', rota: '/postagens', grupo: 'Conteúdo', icone: ICONE.social, palavras: ['postagens', 'feed', 'publicação', 'comentários'] },

  // Administração.
  { tela: 'USUARIOS', rotulo: 'Usuários', rota: '/usuarios', grupo: 'Administração', icone: ICONE.usuarios, palavras: ['admin', 'contas', 'acesso', 'senha'] },
  { tela: 'UNIDADES', rotulo: 'Unidades de Saúde', rota: '/unidades', grupo: 'Administração', icone: ICONE.predio, palavras: ['unidade', 'clínica', 'posto', 'local'] },
  { tela: 'PERFIS', rotulo: 'Perfis', rota: '/perfis', grupo: 'Administração', icone: ICONE.perfis, palavras: ['permissões', 'acesso', 'rbac', 'papéis'] },
  { tela: 'CONFIGURACOES', rotulo: 'Configurações', rota: '/configuracoes', grupo: 'Administração', icone: ICONE.engrenagem, palavras: ['ajustes', 'settings', 'white-label', 'tema', 'cor'] },
  { tela: 'USO_IA', rotulo: 'Uso de IA', rota: '/uso-ia', grupo: 'Administração', icone: ICONE.ia, palavras: ['custo', 'tokens', 'auditoria', 'ia', 'gasto', 'fatura', 'claude'] },
];
