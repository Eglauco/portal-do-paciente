package com.example.pop.siresp;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registro importado do SIRESP/CROSS (Módulo de Regulação Ambulatorial): UMA linha por {@code <Mensagem>} do XML
 * (layout "agendamento de consulta, sob demanda"). Guarda CADA campo do XML em sua coluna, como texto cru (datas
 * e horas ficam como vieram) — SEM gerar agendamento e SEM regras de negócio ainda; é só a população da tabela.
 *
 * <p>Criado apenas por upload de XML (não há cadastro manual) e é somente-leitura depois (sem edição/exclusão na
 * tela). Cada registro é vinculado à unidade de saúde ativa de quem importou + metadados do arquivo/importação.
 */
@Entity
@Table(name = "siresp")
@Getter
@Setter
@NoArgsConstructor
public class Siresp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---------- Metadados da importação (não vêm do XML) ----------

    /** Unidade de saúde ativa de quem importou (escopo da tela). */
    @Column(name = "unidade_saude_id")
    private Long unidadeSaudeId;

    /** Nome do arquivo XML enviado. */
    @Column(name = "arquivo", length = 255)
    private String arquivo;

    /** Quando foi importado. */
    @Column(name = "importado_em", nullable = false)
    private LocalDateTime importadoEm;

    /** Usuário (admin) que fez o upload. */
    @Column(name = "importado_por_usuario_id")
    private Long importadoPorUsuarioId;

    /** Nome do usuário que importou (denormalizado, para exibir sem join). */
    @Column(name = "importado_por_nome", length = 150)
    private String importadoPorNome;

    /**
     * Log do PROCESSAMENTO INTERNO (diagnóstico da integração: encontrou/não encontrou
     * especialidade/unidade/profissional/paciente + criação do agendamento). O resultado do envio ao Sistema de
     * Gestão fica separado em {@link #logEnvio}.
     */
    @Column(name = "log_integracao", columnDefinition = "TEXT")
    private String logIntegracao;

    /** Log do ENVIO do XML ao Sistema de Gestão (resultado do último "Post XML"). Separado do processamento interno. */
    @Column(name = "log_envio", columnDefinition = "TEXT")
    private String logEnvio;

    /** Status do envio do XML ao Sistema de Gestão (independente do agendamento). Padrão: nunca enviado. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_envio", nullable = false, length = 20)
    private StatusEnvio statusEnvio = StatusEnvio.NAO_ENVIADO;

    /** Data/hora da última tentativa de envio ao Sistema de Gestão (null = nunca tentado). */
    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;

    /**
     * URL (S3, pasta {@code siresp/}) do ARQUIVO XML ORIGINAL deste import — os bytes exatos recebidos no upload.
     * É o que reenviamos ao Sistema de Gestão no "Post XML" (sem divergência) e o que o botão de download baixa. A
     * mesma URL fica em TODAS as linhas do mesmo upload. Registros antigos (sem S3) ficam nulos — o envio então
     * reconstrói o XML a partir das colunas abaixo (fallback).
     */
    @Column(name = "arquivo_url", length = 512)
    private String arquivoUrl;

    /**
     * Id do agendamento gerado a partir deste registro (null = ainda não gerado). Guardado para não recriar
     * (idempotência) e para deduplicar por {@code ID_AGE_CONSULTA_HOR} entre reimportações.
     */
    @Column(name = "agendamento_id")
    private Long agendamentoId;

    /** Tipo do registro: CONSULTA (padrão) ou EXAME. Detectado na importação pela estrutura do XML. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_registro", nullable = false, length = 20)
    private TipoRegistroSiresp tipoRegistro = TipoRegistroSiresp.CONSULTA;

    /** Movimentação (TIPO_CONSULTA/TIPO_EXAME): AGENDAMENTO (padrão) / CANCELAMENTO / TRANSFERENCIA. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimento", nullable = false, length = 20)
    private TipoMovimento tipoMovimento = TipoMovimento.AGENDAMENTO;

    /**
     * true quando o sistema PREENCHEU campos de exibição (paciente/CPF/data/hora/especialidade/profissional) a partir
     * do horário encontrado no processamento — esses dados NÃO vieram do XML (típico de cancelamento/transferência,
     * cujo XML é enxuto). Serve para avisar o usuário no detalhe e para tornar o registro pesquisável.
     */
    @Column(name = "dados_resolvidos", nullable = false)
    private boolean dadosResolvidos = false;

    // ---------- Campos do XML (NewDataSet > Mensagem) — texto cru ----------
    // Consulta e exame compartilham a tabela; os campos específicos de cada tipo ficam nulos no outro.

    @Column(name = "tipo_consulta", columnDefinition = "TEXT")
    private String tipoConsulta;
    @Column(name = "cod_unidade_executante", columnDefinition = "TEXT")
    private String codUnidadeExecutante;
    @Column(name = "id_age_consulta_hor", columnDefinition = "TEXT")
    private String idAgeConsultaHor;
    @Column(name = "id_age_consulta", columnDefinition = "TEXT")
    private String idAgeConsulta;
    @Column(name = "age_consulta_nome", columnDefinition = "TEXT")
    private String ageConsultaNome;
    @Column(name = "id_especialidade", columnDefinition = "TEXT")
    private String idEspecialidade;
    @Column(name = "nome_especialidade", columnDefinition = "TEXT")
    private String nomeEspecialidade;
    @Column(name = "cod_dia", columnDefinition = "TEXT")
    private String codDia;
    @Column(name = "data_agenda", columnDefinition = "TEXT")
    private String dataAgenda;
    @Column(name = "hor_ini", columnDefinition = "TEXT")
    private String horIni;
    @Column(name = "hor_fim", columnDefinition = "TEXT")
    private String horFim;
    @Column(name = "tipo", columnDefinition = "TEXT")
    private String tipo;
    @Column(name = "id_motivo", columnDefinition = "TEXT")
    private String idMotivo;
    @Column(name = "id_profissional", columnDefinition = "TEXT")
    private String idProfissional;
    @Column(name = "doc_profissional", columnDefinition = "TEXT")
    private String docProfissional;
    @Column(name = "origem", columnDefinition = "TEXT")
    private String origem;
    @Column(name = "nome_profissional", columnDefinition = "TEXT")
    private String nomeProfissional;
    @Column(name = "id_protocolo", columnDefinition = "TEXT")
    private String idProtocolo;
    @Column(name = "subcateg", columnDefinition = "TEXT")
    private String subcateg;
    @Column(name = "nome_protocolo", columnDefinition = "TEXT")
    private String nomeProtocolo;
    @Column(name = "cod_unidade_solicitante", columnDefinition = "TEXT")
    private String codUnidadeSolicitante;
    @Column(name = "nome_unidade_solicitante", columnDefinition = "TEXT")
    private String nomeUnidadeSolicitante;
    @Column(name = "cnes_unidade_solicitante", columnDefinition = "TEXT")
    private String cnesUnidadeSolicitante;
    @Column(name = "nome_usuario_solicitante", columnDefinition = "TEXT")
    private String nomeUsuarioSolicitante;
    @Column(name = "dt_ultima_atualiz", columnDefinition = "TEXT")
    private String dtUltimaAtualiz;
    @Column(name = "cod_paciente", columnDefinition = "TEXT")
    private String codPaciente;
    @Column(name = "nome_paciente", columnDefinition = "TEXT")
    private String nomePaciente;
    @Column(name = "sexo", columnDefinition = "TEXT")
    private String sexo;
    @Column(name = "dt_nascimento", columnDefinition = "TEXT")
    private String dtNascimento;
    @Column(name = "rg", columnDefinition = "TEXT")
    private String rg;
    @Column(name = "cpf", columnDefinition = "TEXT")
    private String cpf;
    @Column(name = "nome_mae", columnDefinition = "TEXT")
    private String nomeMae;
    @Column(name = "nome_pai", columnDefinition = "TEXT")
    private String nomePai;
    @Column(name = "endereco", columnDefinition = "TEXT")
    private String endereco;
    @Column(name = "endereco_numero", columnDefinition = "TEXT")
    private String enderecoNumero;
    @Column(name = "bairro", columnDefinition = "TEXT")
    private String bairro;
    @Column(name = "municipio", columnDefinition = "TEXT")
    private String municipio;
    @Column(name = "uf", columnDefinition = "TEXT")
    private String uf;
    @Column(name = "cep", columnDefinition = "TEXT")
    private String cep;
    @Column(name = "tel_res_ddd", columnDefinition = "TEXT")
    private String telResDdd;
    @Column(name = "tel_res", columnDefinition = "TEXT")
    private String telRes;
    @Column(name = "tel_celular_ddd", columnDefinition = "TEXT")
    private String telCelularDdd;
    @Column(name = "tel_celular", columnDefinition = "TEXT")
    private String telCelular;
    @Column(name = "tel_com_ddd", columnDefinition = "TEXT")
    private String telComDdd;
    @Column(name = "tel_com", columnDefinition = "TEXT")
    private String telCom;
    @Column(name = "tel_com_ramal", columnDefinition = "TEXT")
    private String telComRamal;
    @Column(name = "email", columnDefinition = "TEXT")
    private String email;
    @Column(name = "contato_nome", columnDefinition = "TEXT")
    private String contatoNome;
    @Column(name = "contato_tel_ddd", columnDefinition = "TEXT")
    private String contatoTelDdd;
    @Column(name = "contato_tel", columnDefinition = "TEXT")
    private String contatoTel;
    @Column(name = "num_cns", columnDefinition = "TEXT")
    private String numCns;
    @Column(name = "num_prontuario", columnDefinition = "TEXT")
    private String numProntuario;

    // ---------- Campos específicos do EXAME (nulos quando o registro é CONSULTA) ----------
    @Column(name = "tipo_exame", columnDefinition = "TEXT")
    private String tipoExame;
    /** Código do horário do exame no CROSS (análogo ao ID_AGE_CONSULTA_HOR da consulta). */
    @Column(name = "id_age_exame_hor", columnDefinition = "TEXT")
    private String idAgeExameHor;
    /** Horário de ORIGEM na TRANSFERÊNCIA de consulta (ID_AGE_CONSULTA_HOR_ORIGEM) — o horário antigo a cancelar. */
    @Column(name = "id_age_consulta_hor_origem", columnDefinition = "TEXT")
    private String idAgeConsultaHorOrigem;
    /** Horário de ORIGEM na TRANSFERÊNCIA de exame (ID_AGE_EXAME_HOR_ORIGEM) — o horário antigo a cancelar. */
    @Column(name = "id_age_exame_hor_origem", columnDefinition = "TEXT")
    private String idAgeExameHorOrigem;
    /** Código da agenda do exame no CROSS (análogo ao ID_AGE_CONSULTA da consulta). */
    @Column(name = "id_age_exame", columnDefinition = "TEXT")
    private String idAgeExame;
    /** Nome da agenda do exame (análogo ao AGE_CONSULTA_NOME da consulta). */
    @Column(name = "age_exame_nome", columnDefinition = "TEXT")
    private String ageExameNome;
    @Column(name = "id_associacao", columnDefinition = "TEXT")
    private String idAssociacao;
    @Column(name = "nome_associacao", columnDefinition = "TEXT")
    private String nomeAssociacao;
    /** Código do exame no CROSS — casa com o cadastro de Exame (codigoIntegracao) para resolver o configuracaoAgenda. */
    @Column(name = "id_exame", columnDefinition = "TEXT")
    private String idExame;
    @Column(name = "cod_exame", columnDefinition = "TEXT")
    private String codExame;
    @Column(name = "nome_exame", columnDefinition = "TEXT")
    private String nomeExame;
    @Column(name = "tipo_tabela", columnDefinition = "TEXT")
    private String tipoTabela;
}
