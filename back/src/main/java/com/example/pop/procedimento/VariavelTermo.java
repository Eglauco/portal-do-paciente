package com.example.pop.procedimento;

/**
 * Catálogo ÚNICO das variáveis dinâmicas que o backend substitui no documento Word do TCLE ao
 * enviar para a assinatura (ZapSign). O admin copia o token (ex.: {@code {{nome_paciente}}}) para
 * dentro do Word; no momento da assinatura, cada token é trocado pelo valor do paciente/atendimento.
 *
 * <p>É a fonte da verdade: a tela (botão "Variáveis dinâmicas") lista exatamente estes tokens, e a
 * futura substituição no envio ao ZapSign usa este mesmo enum — não há como divergir.
 */
public enum VariavelTermo {

    // Paciente
    NOME_PACIENTE("nome_paciente", "Nome completo do paciente", "Maria de Souza", Grupo.PACIENTE),
    CPF_PACIENTE("cpf_paciente", "CPF do paciente", "123.456.789-00", Grupo.PACIENTE),
    RG_PACIENTE("rg_paciente", "RG do paciente", "12.345.678-9", Grupo.PACIENTE),
    DATA_NASCIMENTO("data_nascimento", "Data de nascimento do paciente", "05/03/1985", Grupo.PACIENTE),
    IDADE_PACIENTE("idade_paciente", "Idade do paciente", "40 anos", Grupo.PACIENTE),
    SEXO_PACIENTE("sexo_paciente", "Sexo do paciente", "Feminino", Grupo.PACIENTE),
    NOME_MAE("nome_mae", "Nome da mãe do paciente", "Ana de Souza", Grupo.PACIENTE),
    NOME_PAI("nome_pai", "Nome do pai do paciente", "José de Souza", Grupo.PACIENTE),
    CNS_PACIENTE("cns_paciente", "Cartão SUS (CNS) do paciente", "700 0000 0000 0000", Grupo.PACIENTE),
    EMAIL_PACIENTE("email_paciente", "E-mail do paciente", "maria@email.com", Grupo.PACIENTE),
    TELEFONE_PACIENTE("telefone_paciente", "Telefone do paciente", "(18) 99999-9999", Grupo.PACIENTE),
    PRONTUARIO_PACIENTE("prontuario_paciente", "Nº do prontuário do paciente", "000123", Grupo.PACIENTE),
    ENDERECO_PACIENTE("endereco_paciente", "Endereço completo do paciente", "Rua das Flores, 123 — Centro", Grupo.PACIENTE),
    BAIRRO_PACIENTE("bairro_paciente", "Bairro do paciente", "Centro", Grupo.PACIENTE),
    CIDADE_PACIENTE("cidade_paciente", "Cidade do paciente", "Presidente Prudente", Grupo.PACIENTE),
    UF_PACIENTE("uf_paciente", "UF do paciente", "SP", Grupo.PACIENTE),
    CEP_PACIENTE("cep_paciente", "CEP do paciente", "19000-000", Grupo.PACIENTE),

    // Atendimento (agendamento)
    DATA_AGENDAMENTO("data_agendamento", "Data do agendamento", "24/09/2026", Grupo.ATENDIMENTO),
    HORA_AGENDAMENTO("hora_agendamento", "Hora do agendamento", "14:30", Grupo.ATENDIMENTO),
    ESPECIALIDADE("especialidade", "Especialidade do atendimento", "Gastroenterologia", Grupo.ATENDIMENTO),
    PROFISSIONAL("profissional", "Nome do profissional de saúde", "Dr. Carlos Lima", Grupo.ATENDIMENTO),
    REGISTRO_PROFISSIONAL("registro_profissional", "Registro do profissional (conselho + número)", "CRM 123456", Grupo.ATENDIMENTO),

    // Procedimento
    NOME_PROCEDIMENTO("nome_procedimento", "Nome do procedimento", "Endoscopia digestiva alta", Grupo.PROCEDIMENTO),
    PREPARO_PROCEDIMENTO("preparo_procedimento", "Instruções de preparo do procedimento", "Jejum de 8 horas...", Grupo.PROCEDIMENTO),

    // Unidade de saúde
    UNIDADE("unidade", "Nome da unidade de saúde", "UBS Central", Grupo.UNIDADE),

    // Assinante / responsável (quando o responsável assina por um dependente)
    NOME_RESPONSAVEL("nome_responsavel", "Nome do responsável (quando aplicável)", "Ana de Souza", Grupo.ASSINANTE),
    CPF_RESPONSAVEL("cpf_responsavel", "CPF do responsável (quando aplicável)", "987.654.321-00", Grupo.ASSINANTE),

    // Data / emissão
    DATA_ATUAL("data_atual", "Data de hoje (emissão/assinatura)", "24/09/2026", Grupo.DATA),
    DATA_EXTENSO("data_extenso", "Data de hoje por extenso", "24 de setembro de 2026", Grupo.DATA),
    HORA_ATUAL("hora_atual", "Hora atual", "14:35", Grupo.DATA);

    /** Agrupamento para exibição na tela. */
    public enum Grupo {
        PACIENTE("Paciente"),
        ATENDIMENTO("Atendimento"),
        PROCEDIMENTO("Procedimento"),
        UNIDADE("Unidade de saúde"),
        ASSINANTE("Assinante / Responsável"),
        DATA("Data");

        private final String rotulo;

        Grupo(String rotulo) {
            this.rotulo = rotulo;
        }

        public String rotulo() {
            return rotulo;
        }
    }

    private final String nome;
    private final String descricao;
    private final String exemplo;
    private final Grupo grupo;

    VariavelTermo(String nome, String descricao, String exemplo, Grupo grupo) {
        this.nome = nome;
        this.descricao = descricao;
        this.exemplo = exemplo;
        this.grupo = grupo;
    }

    /** Token para copiar no Word: {@code {{nome}}}. */
    public String token() {
        return "{{" + nome + "}}";
    }

    public String nome() {
        return nome;
    }

    public String descricao() {
        return descricao;
    }

    public String exemplo() {
        return exemplo;
    }

    public Grupo grupo() {
        return grupo;
    }
}
