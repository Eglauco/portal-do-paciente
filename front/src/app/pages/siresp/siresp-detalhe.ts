import { DatePipe } from '@angular/common';
import { Component, DestroyRef, afterNextRender, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ToastrService } from 'ngx-toastr';
import { StorageService } from '../prontuarios/storage.service';
import { SirespDetalhe, SirespStatusEnvio, SirespTipoMovimento } from './siresp.model';
import { SirespService } from './siresp.service';

interface CampoDef {
  rotulo: string;
  chave: keyof SirespDetalhe;
  /** Descrição do campo (da documentação do CROSS) — exibida no tooltip do label. */
  descricao: string;
}
interface GrupoDef {
  titulo: string;
  campos: CampoDef[];
}

/** Uma linha do log de integração + a rota do cadastro relacionado (null = sem link). */
interface LinhaLog {
  texto: string;
  rota: string | null;
}

/**
 * Prefixo de cada linha do diagnóstico → rota do cadastro onde se resolve/consulta aquela entidade (atalho pra abrir
 * em nova aba, sem procurar no menu). "Procedimento:" (consulta) aponta para Especialidade, pois o procedimento é
 * vinculado lá; "Exame/Procedimento:" (exame) aponta para Exames.
 */
const ROTA_POR_PREFIXO: { prefixo: string; rota: string }[] = [
  { prefixo: 'Especialidade:', rota: '/especialidades' },
  { prefixo: 'Unidade de saúde:', rota: '/unidades' },
  { prefixo: 'Profissional:', rota: '/profissionais' },
  { prefixo: 'Paciente:', rota: '/pacientes' },
  { prefixo: 'Exame/Procedimento:', rota: '/exames' },
  { prefixo: 'Procedimento:', rota: '/especialidades' },
];

/** Detalhe (somente leitura) de um registro do SIRESP: todos os campos do XML, agrupados. */
@Component({
  selector: 'app-siresp-detalhe',
  imports: [RouterLink, DatePipe],
  templateUrl: './siresp-detalhe.html',
  host: {
    '(document:click)': 'fecharPopover()',
    '(document:keydown.escape)': 'fecharPopover()',
  },
  styles: [
    `
      .meta { display: flex; flex-wrap: wrap; gap: 0.4rem 1.5rem; color: var(--muted); font-size: 0.88rem; margin-bottom: 1rem; }
      .meta b { color: var(--ink); font-weight: 600; }
      .meta-acoes { margin: 0 0 1rem; }
      .meta-status { display: flex; align-items: center; flex-wrap: wrap; gap: 0.6rem; margin: 0 0 1rem; }
      /* Badge de status (agendado = verde / revisão = âmbar). */
      .status-badge { display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.25rem 0.7rem; border-radius: 999px; font-size: 0.82rem; font-weight: 600; white-space: nowrap; }
      .status-badge::before { content: ''; width: 0.5rem; height: 0.5rem; border-radius: 50%; background: currentColor; flex: none; }
      .status-badge--ok { color: #0a7a4b; background: color-mix(in srgb, #12b76a 15%, transparent); }
      .status-badge--rev { color: #9a6a00; background: color-mix(in srgb, #e6a700 20%, transparent); }
      /* Badge do ENVIO: azul = enviado / vermelho = falha / cinza = não enviado (distinto do agendamento). */
      .status-badge--env-ok { color: #0a5bd3; background: color-mix(in srgb, #2e7bf6 15%, transparent); }
      .status-badge--env-fail { color: #b42318; background: color-mix(in srgb, #f04438 15%, transparent); }
      .status-badge--env-none { color: var(--muted); background: color-mix(in srgb, var(--muted) 16%, transparent); }
      /* Log: âmbar (pendências) por padrão; verde quando "Tudo OK"/enviado; vermelho quando falha; cinza quando neutro. */
      .log { margin: 0 0 1.1rem; padding: 0.7rem 0.9rem; border-radius: 0.55rem; border: 1px solid #e6c200; background: color-mix(in srgb, #ffcc00 12%, transparent); }
      .log--ok { border-color: #bfe6cf; background: color-mix(in srgb, #12b76a 12%, transparent); }
      .log--fail { border-color: #f1b0aa; background: color-mix(in srgb, #f04438 12%, transparent); }
      .log--neutral { border-color: var(--line); background: color-mix(in srgb, var(--muted) 7%, transparent); }
      .log__head { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; margin-bottom: 0.25rem; flex-wrap: wrap; }
      .log__tit { margin: 0; font-size: 0.74rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.02em; color: var(--muted); }
      .log__txt { margin: 0; font-size: 0.9rem; line-height: 1.45; white-space: pre-line; color: var(--ink); }
      .log__meta { margin: 0.4rem 0 0; font-size: 0.78rem; color: var(--muted); }
      /* Linhas do log com atalho para o cadastro (abre em nova aba). */
      .log__linha { display: block; }
      .log__link { color: var(--brand-deep, var(--brand)); text-decoration: underline; text-underline-offset: 2px; cursor: pointer; }
      .log__link:hover { text-decoration: none; }
      /* Aviso: dados preenchidos pelo sistema (não vieram do XML). */
      .aviso-sistema { display: flex; align-items: flex-start; gap: 0.5rem; margin: 0 0 1rem; padding: 0.65rem 0.85rem; border: 1px solid color-mix(in srgb, #2e7bf6 35%, transparent); background: color-mix(in srgb, #2e7bf6 8%, transparent); border-radius: 0.55rem; font-size: 0.86rem; line-height: 1.4; color: var(--ink); }
      .aviso-sistema svg { width: 1.05rem; height: 1.05rem; flex: none; color: #0a5bd3; margin-top: 0.1rem; }
      .grupo { margin-bottom: 1rem; }
      .grupo__titulo { font-size: 0.95rem; font-weight: 700; color: var(--ink); margin: 0 0 0.6rem; }
      .kv { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 0.6rem 1.25rem; }
      .kv__item { display: flex; flex-direction: column; gap: 0.1rem; min-width: 0; }
      .kv__rot { display: inline-flex; align-items: center; gap: 0.3rem; font-size: 0.74rem; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.02em; }
      /* Botão ⓘ: clica e mostra a descrição do campo abaixo (sem delay do tooltip nativo). */
      .kv__info { display: inline-flex; align-items: center; justify-content: center; width: 1.05rem; height: 1.05rem; padding: 0; border: 0; border-radius: 50%; background: transparent; color: var(--muted); cursor: pointer; flex: none; }
      .kv__info svg { width: 0.95rem; height: 0.95rem; }
      .kv__info:hover { color: var(--brand); }
      .kv__info[aria-expanded='true'] { color: var(--brand); background: color-mix(in srgb, var(--brand) 12%, transparent); }
      .kv__info:focus-visible { outline: 2px solid var(--brand); outline-offset: 1px; }
      .kv__val { color: var(--ink); word-break: break-word; }

      /* Popover flutuante (position: fixed → coords de viewport; fica acima de tudo, sem recorte do grid). */
      .popover { position: fixed; z-index: 1000; width: 260px; max-width: calc(100vw - 16px); background: var(--surface, #fff); color: var(--ink); border: 1px solid var(--line); border-radius: 0.6rem; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.14); padding: 0.6rem 0.75rem; }
      .popover__tit { font-size: 0.72rem; font-weight: 700; text-transform: uppercase; letter-spacing: 0.02em; color: var(--muted); margin: 0 0 0.2rem; }
      .popover__txt { font-size: 0.86rem; line-height: 1.4; margin: 0; }
      /* Setinha apontando para o ícone. */
      .popover__caret { position: absolute; top: -6px; width: 11px; height: 11px; background: var(--surface, #fff); border-left: 1px solid var(--line); border-top: 1px solid var(--line); transform: rotate(45deg); }
    `,
  ],
})
export class SirespDetalheComponent {
  private readonly service = inject(SirespService);
  private readonly storage = inject(StorageService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toastr = inject(ToastrService);

  /** Elemento do ícone ⓘ que abriu o popover (para reposicionar seguindo-o no scroll). */
  private ancora: HTMLElement | null = null;
  private idAtual: number | null = null;

  protected readonly detalhe = signal<SirespDetalhe | null>(null);
  protected readonly carregando = signal(false);
  protected readonly erro = signal(false);
  /** Reprocessando o processamento interno (recalcula o diagnóstico + cria o agendamento). */
  protected readonly processando = signal(false);
  /** Enviando o XML ao Sistema de Gestão (Post XML). */
  protected readonly enviando = signal(false);
  /** Gerando o link e abrindo o arquivo XML original (S3). */
  protected readonly baixando = signal(false);
  /** Se há URL do Sistema de Gestão configurada (nas Configurações do SIRESP) — habilita o botão "Enviar". */
  protected readonly urlConfigurada = signal(false);

  /** Popover aberto (um por vez): campo, textos e posição na viewport. */
  protected readonly popover = signal<{
    chave: string;
    titulo: string;
    texto: string;
    top: number;
    left: number;
    caret: number;
  } | null>(null);

  protected aberto(chave: keyof SirespDetalhe): boolean {
    return this.popover()?.chave === (chave as string);
  }

  /** Abre/fecha o popover ancorado no ícone clicado. */
  protected alternar(c: CampoDef, ev: MouseEvent): void {
    ev.stopPropagation(); // não deixa o clique fechar o popover que estamos abrindo
    if (this.aberto(c.chave)) {
      this.fecharPopover();
      return;
    }
    this.ancora = ev.currentTarget as HTMLElement;
    this.mostrar(c.chave as string, c.rotulo, c.descricao);
  }

  /** (Re)calcula a posição a partir da âncora atual (clamp nas bordas) e abre/atualiza o popover. */
  private mostrar(chave: string, titulo: string, texto: string): void {
    if (!this.ancora) return;
    const alvo = this.ancora.getBoundingClientRect();
    const largura = 260;
    const centro = alvo.left + alvo.width / 2;
    const left = Math.max(8, Math.min(centro - largura / 2, window.innerWidth - largura - 8));
    const caret = Math.max(12, Math.min(centro - left - 5.5, largura - 23));
    this.popover.set({ chave, titulo, texto, top: alvo.bottom + 8, left, caret });
  }

  /** Mantém o popover colado no ícone quando a página/um container rola; fecha se o ícone sai da área visível. */
  private reposicionar(): void {
    const p = this.popover();
    if (!p || !this.ancora) return;
    const alvo = this.ancora.getBoundingClientRect();
    if (alvo.bottom < 64 || alvo.top > window.innerHeight - 8) {
      this.fecharPopover();
      return;
    }
    this.mostrar(p.chave, p.titulo, p.texto);
  }

  protected fecharPopover(): void {
    if (this.popover()) this.popover.set(null);
    this.ancora = null;
  }

  // --- Grupos comuns aos dois tipos ---
  private readonly gProfissional: GrupoDef = {
    titulo: 'Profissional',
    campos: [
      { rotulo: 'ID profissional', chave: 'idProfissional', descricao: 'Código CROSS do Profissional.' },
      { rotulo: 'Documento', chave: 'docProfissional', descricao: 'Número do registro do profissional em seu conselho regional.' },
      { rotulo: 'Nome', chave: 'nomeProfissional', descricao: 'Nome do Profissional.' },
    ],
  };
  private readonly gUnidadeSolic: GrupoDef = {
    titulo: 'Unidade solicitante',
    campos: [
      { rotulo: 'Cód. unidade', chave: 'codUnidadeSolicitante', descricao: 'Código CROSS da unidade que agendou.' },
      { rotulo: 'Nome', chave: 'nomeUnidadeSolicitante', descricao: 'Nome da unidade que agendou.' },
      { rotulo: 'CNES', chave: 'cnesUnidadeSolicitante', descricao: 'CNES da unidade que agendou.' },
      { rotulo: 'Usuário solicitante', chave: 'nomeUsuarioSolicitante', descricao: 'Nome do usuário que agendou.' },
      { rotulo: 'Última atualização', chave: 'dtUltimaAtualiz', descricao: 'Data e hora do agendamento.' },
    ],
  };
  private readonly gPaciente: GrupoDef = {
    titulo: 'Paciente',
    campos: [
      { rotulo: 'Cód. paciente', chave: 'codPaciente', descricao: 'Código CROSS do Paciente.' },
      { rotulo: 'Nome', chave: 'nomePaciente', descricao: 'Nome do paciente agendado.' },
      { rotulo: 'Sexo', chave: 'sexo', descricao: 'Sexo. M (masculino), F (feminino), I (indeterminado).' },
      { rotulo: 'Nascimento', chave: 'dtNascimento', descricao: 'Data de nascimento (0001-01-01 = data não informada).' },
      { rotulo: 'RG', chave: 'rg', descricao: 'RG do Paciente.' },
      { rotulo: 'CPF', chave: 'cpf', descricao: 'CPF do Paciente.' },
      { rotulo: 'Nome da mãe', chave: 'nomeMae', descricao: 'Nome da mãe do paciente.' },
      { rotulo: 'Nome do pai', chave: 'nomePai', descricao: 'Nome do pai do paciente.' },
      { rotulo: 'CNS', chave: 'numCns', descricao: 'Cartão Nacional de Saúde.' },
      { rotulo: 'Nº prontuário', chave: 'numProntuario', descricao: 'Prontuário do paciente na unidade executante.' },
    ],
  };
  private readonly gEndereco: GrupoDef = {
    titulo: 'Endereço',
    campos: [
      { rotulo: 'Logradouro', chave: 'endereco', descricao: 'Endereço do paciente sem número.' },
      { rotulo: 'Número', chave: 'enderecoNumero', descricao: 'Número do endereço do paciente.' },
      { rotulo: 'Bairro', chave: 'bairro', descricao: 'Bairro do paciente.' },
      { rotulo: 'Município', chave: 'municipio', descricao: 'Município do Paciente.' },
      { rotulo: 'UF', chave: 'uf', descricao: 'Estado do Paciente.' },
      { rotulo: 'CEP', chave: 'cep', descricao: 'CEP do Paciente.' },
    ],
  };
  private readonly gContato: GrupoDef = {
    titulo: 'Contato',
    campos: [
      { rotulo: 'DDD residencial', chave: 'telResDdd', descricao: 'DDD do telefone residencial.' },
      { rotulo: 'Telefone residencial', chave: 'telRes', descricao: 'Número do telefone residencial.' },
      { rotulo: 'DDD celular', chave: 'telCelularDdd', descricao: 'DDD do celular.' },
      { rotulo: 'Celular', chave: 'telCelular', descricao: 'Número do celular.' },
      { rotulo: 'DDD comercial', chave: 'telComDdd', descricao: 'DDD do telefone comercial.' },
      { rotulo: 'Telefone comercial', chave: 'telCom', descricao: 'Número do telefone comercial.' },
      { rotulo: 'Ramal', chave: 'telComRamal', descricao: 'Ramal do telefone comercial.' },
      { rotulo: 'E-mail', chave: 'email', descricao: 'E-mail do paciente.' },
      { rotulo: 'Contato (nome)', chave: 'contatoNome', descricao: 'Nome do contato do telefone para recados.' },
      { rotulo: 'DDD contato', chave: 'contatoTelDdd', descricao: 'DDD do telefone do contato.' },
      { rotulo: 'Telefone contato', chave: 'contatoTel', descricao: 'Número do telefone do contato.' },
    ],
  };

  // --- Grupos específicos da CONSULTA ---
  private readonly gAgendamentoConsulta: GrupoDef = {
    titulo: 'Agendamento',
    campos: [
      { rotulo: 'Tipo de consulta', chave: 'tipoConsulta', descricao: 'Tipo da consulta / movimentação da mensagem (agendamento, cancelamento ou transferência).' },
      { rotulo: 'Cód. unidade executante', chave: 'codUnidadeExecutante', descricao: 'Código CROSS da Unidade Executante.' },
      { rotulo: 'ID agenda/horário', chave: 'idAgeConsultaHor', descricao: 'Código do agendamento / horário.' },
      { rotulo: 'ID agendamento', chave: 'idAgeConsulta', descricao: 'Código da Agenda.' },
      { rotulo: 'ID horário origem', chave: 'idAgeConsultaHorOrigem', descricao: 'Horário de origem na transferência (ID_AGE_CONSULTA_HOR_ORIGEM) — o horário antigo cancelado.' },
      { rotulo: 'Nome da agenda', chave: 'ageConsultaNome', descricao: 'Nome da Agenda.' },
      { rotulo: 'ID especialidade', chave: 'idEspecialidade', descricao: 'Código da Especialidade.' },
      { rotulo: 'Especialidade', chave: 'nomeEspecialidade', descricao: 'Nome da Especialidade.' },
      { rotulo: 'Cód. dia', chave: 'codDia', descricao: 'Código do dia da semana, onde 0 é Domingo e 6 é Sábado.' },
      { rotulo: 'Data', chave: 'dataAgenda', descricao: 'Dia da consulta.' },
      { rotulo: 'Hora início', chave: 'horIni', descricao: 'Hora de início da consulta.' },
      { rotulo: 'Hora fim', chave: 'horFim', descricao: 'Hora final da consulta.' },
      { rotulo: 'Tipo', chave: 'tipo', descricao: 'Tipo do agendamento (P - 1ª consulta / R - Retorno).' },
      { rotulo: 'ID motivo', chave: 'idMotivo', descricao: 'Motivo do agendamento (Normal / Extra).' },
      { rotulo: 'Origem', chave: 'origem', descricao: 'Sigla do Conselho regional do profissional (ex.: CRM).' },
    ],
  };
  private readonly gProtocolo: GrupoDef = {
    titulo: 'Protocolo',
    campos: [
      { rotulo: 'ID protocolo', chave: 'idProtocolo', descricao: 'Código CROSS do protocolo escolhido.' },
      { rotulo: 'Subcategoria', chave: 'subcateg', descricao: 'Código CID 10 do protocolo escolhido.' },
      { rotulo: 'Nome do protocolo', chave: 'nomeProtocolo', descricao: 'Nome do Protocolo escolhido.' },
    ],
  };

  // --- Grupos específicos do EXAME ---
  private readonly gAgendamentoExame: GrupoDef = {
    titulo: 'Agendamento',
    campos: [
      { rotulo: 'Tipo de exame', chave: 'tipoExame', descricao: 'Tipo do exame / movimentação da mensagem.' },
      { rotulo: 'Cód. unidade executante', chave: 'codUnidadeExecutante', descricao: 'Código CROSS da Unidade Executante.' },
      { rotulo: 'ID agenda/horário', chave: 'idAgeExameHor', descricao: 'Código do agendamento / horário do exame.' },
      { rotulo: 'ID agendamento', chave: 'idAgeExame', descricao: 'Código da Agenda do exame.' },
      { rotulo: 'ID horário origem', chave: 'idAgeExameHorOrigem', descricao: 'Horário de origem na transferência (ID_AGE_EXAME_HOR_ORIGEM) — o horário antigo cancelado.' },
      { rotulo: 'Nome da agenda', chave: 'ageExameNome', descricao: 'Nome da Agenda do exame.' },
      { rotulo: 'ID associação', chave: 'idAssociacao', descricao: 'Código da associação de exames.' },
      { rotulo: 'Nome associação', chave: 'nomeAssociacao', descricao: 'Nome da associação de exames.' },
      { rotulo: 'ID especialidade', chave: 'idEspecialidade', descricao: 'Código da Especialidade.' },
      { rotulo: 'Especialidade', chave: 'nomeEspecialidade', descricao: 'Nome da Especialidade.' },
      { rotulo: 'Cód. dia', chave: 'codDia', descricao: 'Código do dia da semana, onde 0 é Domingo e 6 é Sábado.' },
      { rotulo: 'Data', chave: 'dataAgenda', descricao: 'Dia do exame.' },
      { rotulo: 'Hora início', chave: 'horIni', descricao: 'Hora de início do exame.' },
      { rotulo: 'Hora fim', chave: 'horFim', descricao: 'Hora final do exame.' },
      { rotulo: 'ID motivo', chave: 'idMotivo', descricao: 'Motivo do agendamento (Normal / Extra).' },
      { rotulo: 'Origem', chave: 'origem', descricao: 'Sigla do Conselho regional do profissional (ex.: CRM).' },
    ],
  };
  private readonly gExame: GrupoDef = {
    titulo: 'Exame',
    campos: [
      { rotulo: 'ID exame', chave: 'idExame', descricao: 'Código CROSS do exame.' },
      { rotulo: 'Cód. exame', chave: 'codExame', descricao: 'Código SUS/SIGTAP do exame.' },
      { rotulo: 'Nome do exame', chave: 'nomeExame', descricao: 'Nome do exame.' },
      { rotulo: 'Tipo de tabela', chave: 'tipoTabela', descricao: 'Tipo de tabela do exame (ex.: SUS).' },
    ],
  };

  /** Seções e campos exibidos conforme o tipo do registro (consulta ou exame). */
  protected readonly grupos = computed<GrupoDef[]>(() => {
    const exame = this.detalhe()?.tipoRegistro === 'EXAME';
    return exame
      ? [this.gAgendamentoExame, this.gProfissional, this.gExame, this.gUnidadeSolic, this.gPaciente, this.gEndereco, this.gContato]
      : [this.gAgendamentoConsulta, this.gProfissional, this.gProtocolo, this.gUnidadeSolic, this.gPaciente, this.gEndereco, this.gContato];
  });

  constructor() {
    const id = this.route.snapshot.paramMap.get('id');
    this.idAtual = id ? Number(id) : null;
    afterNextRender(() => {
      if (this.idAtual != null) this.carregar(this.idAtual);
      // Saber se há URL configurada (para habilitar o botão "Enviar" ao Sistema de Gestão).
      this.service.lerConfig().subscribe({
        next: (c) => this.urlConfigurada.set(!!c.postUrl && c.postUrl.trim().length > 0),
        error: () => this.urlConfigurada.set(false),
      });
      // Segue o ícone quando rola/redimensiona. 'capture: true' pega o scroll de QUALQUER container
      // (a tela rola num container interno, não na window) — por isso o window:scroll não bastava.
      const aoMover = () => this.reposicionar();
      document.addEventListener('scroll', aoMover, { capture: true, passive: true });
      window.addEventListener('resize', aoMover);
      this.destroyRef.onDestroy(() => {
        document.removeEventListener('scroll', aoMover, { capture: true });
        window.removeEventListener('resize', aoMover);
      });
    });
  }

  protected valor(chave: keyof SirespDetalhe): string {
    const v = this.detalhe()?.[chave];
    return v === null || v === undefined || v === '' ? '—' : String(v);
  }

  /** Rótulo amigável do status de envio ao Sistema de Gestão. */
  protected enviaStatusLabel(s?: SirespStatusEnvio): string {
    return s === 'ENVIADO' ? 'Enviado com sucesso' : s === 'FALHA' ? 'Falha no envio' : 'Não enviado';
  }

  /** Rótulo da movimentação (agendamento/cancelamento/transferência). */
  protected movimentoLabel(m?: SirespTipoMovimento): string {
    return m === 'CANCELAMENTO' ? 'Cancelamento' : m === 'TRANSFERENCIA' ? 'Transferência' : 'Agendamento';
  }

  /** Rótulo da situação, considerando a movimentação (concluído = Agendado/Cancelado/Transferido com sucesso). */
  protected situacaoLabel(d: SirespDetalhe): string {
    if (!d.agendamentoId) return 'Precisa de revisão';
    return d.tipoMovimento === 'CANCELAMENTO' ? 'Cancelado com sucesso'
      : d.tipoMovimento === 'TRANSFERENCIA' ? 'Transferido com sucesso'
      : 'Agendado com sucesso';
  }

  /**
   * Log de integração (processamento interno) quebrado em linhas, cada uma com a rota do cadastro relacionado quando
   * houver — assim as linhas de entidade (Especialidade/Unidade/Profissional/Paciente/Procedimento/Exame) viram links
   * que abrem a tela em nova aba.
   */
  protected readonly logInternoLinhas = computed<LinhaLog[]>(() => {
    const log = this.detalhe()?.logIntegracao;
    if (!log) return [];
    return log.split('\n').map((texto) => ({ texto, rota: this.rotaDaLinha(texto) }));
  });

  /** Rota do cadastro para uma linha do diagnóstico (pelo prefixo), ou null quando a linha não é de entidade. */
  private rotaDaLinha(linha: string): string | null {
    const achado = ROTA_POR_PREFIXO.find(({ prefixo }) => linha.startsWith(prefixo));
    return achado ? achado.rota : null;
  }

  /**
   * Reprocessa o PROCESSAMENTO INTERNO: recalcula o diagnóstico e cria o agendamento quando tudo está resolvido.
   * Não envia nada ao Sistema de Gestão. Registro já agendado está concluído — não reprocessa (botão desabilitado).
   */
  protected reprocessar(): void {
    const id = this.idAtual;
    if (id == null || this.processando() || this.detalhe()?.agendamentoId) {
      return;
    }
    this.processando.set(true);
    this.service.reprocessar(id).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.processando.set(false);
        this.toastr.success('Registro reprocessado.');
      },
      error: (e) => {
        this.processando.set(false);
        this.toastr.error(e?.error?.message ?? 'Não foi possível reprocessar.');
      },
    });
  }

  /**
   * Reenvia o XML ao Sistema de Gestão (Post XML). Travado quando já foi enviado com sucesso (statusEnvio = ENVIADO)
   * ou quando não há URL configurada. O resultado atualiza o log/status de envio exibidos.
   */
  protected enviar(): void {
    const id = this.idAtual;
    const d = this.detalhe();
    if (id == null || this.enviando() || !this.urlConfigurada() || d?.statusEnvio === 'ENVIADO') {
      return;
    }
    this.enviando.set(true);
    this.service.enviar(id).subscribe({
      next: (r) => {
        this.detalhe.set(r.registro);
        this.enviando.set(false);
        if (r.sucesso) {
          this.toastr.success('XML enviado ao Sistema de Gestão — ' + r.mensagem);
        } else {
          this.toastr.error('O Sistema de Gestão não processou — ' + r.mensagem);
        }
      },
      error: (e) => {
        this.enviando.set(false);
        this.toastr.error(e?.error?.message ?? 'Não foi possível enviar.');
      },
    });
  }

  /** Baixa o arquivo XML original armazenado no S3 (URL assinada com Content-Disposition: attachment). */
  protected async baixarOriginal(): Promise<void> {
    const d = this.detalhe();
    const url = d?.arquivoUrl;
    if (!url || this.baixando()) {
      return;
    }
    const nome = d?.arquivo || 'siresp.xml';
    this.baixando.set(true);
    try {
      // URL assinada já vem como "attachment": o clique no link baixa o arquivo (não abre nova aba).
      const link = await this.storage.urlDownloadAnexo(url, nome);
      const a = document.createElement('a');
      a.href = link;
      a.download = nome;
      document.body.appendChild(a);
      a.click();
      a.remove();
    } catch {
      this.toastr.error('Não foi possível baixar o arquivo original.');
    } finally {
      this.baixando.set(false);
    }
  }

  private carregar(id: number): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.service.detalhe(id).subscribe({
      next: (d) => {
        this.detalhe.set(d);
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }
}
