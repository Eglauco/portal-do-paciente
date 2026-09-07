package com.example.pop.paciente;

import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * Campos ESCALARES do cadastro do paciente que entram na auditoria. Cada campo sabe
 * extrair o seu valor de EXIBIÇÃO (string canônica) de um {@link Paciente}; o serviço
 * de log compara esses valores antes/depois para detectar o que mudou. Valor
 * ausente/vazio é representado por {@code null}.
 *
 * <p>Coleções (telefones adicionais e responsáveis) NÃO entram aqui: têm diff granular
 * próprio no {@link PacienteLogService} (uma linha por item/atributo alterado).
 *
 * <p>O nome da constante ({@link #name()}) é o código estável gravado no banco; a
 * {@link #getDescricao()} é o rótulo humano exibido na linha do tempo.
 */
public enum CampoPaciente {

    NOME("Nome", Paciente::getNome),
    TELEFONE("Telefone", Paciente::getTelefone),
    CODIGO_INTEGRACAO("Código de integração", Paciente::getCodigoIntegracao),
    PRONTUARIO("Prontuário", Paciente::getProntuario),
    SEXO("Sexo", p -> sexoLabel(p.getSexo())),
    DATA_NASCIMENTO("Data de nascimento", CampoPaciente::dataNascimento),
    RG("RG", Paciente::getRg),
    CPF("CPF", Paciente::getCpf),
    NOME_MAE("Nome da mãe", Paciente::getNomeMae),
    NOME_PAI("Nome do pai", Paciente::getNomePai),
    RUA("Rua", Paciente::getRua),
    NUMERO("Número", Paciente::getNumero),
    BAIRRO("Bairro", Paciente::getBairro),
    MUNICIPIO("Município", Paciente::getMunicipio),
    UF("UF", Paciente::getUf),
    CEP("CEP", Paciente::getCep),
    COMPLEMENTO("Complemento", Paciente::getComplemento),
    EMAIL("E-mail", Paciente::getEmail),
    CNS("CNS", Paciente::getCns);

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final String descricao;
    private final Function<Paciente, String> extrator;

    CampoPaciente(String descricao, Function<Paciente, String> extrator) {
        this.descricao = descricao;
        this.extrator = extrator;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Valor de exibição do campo neste paciente; {@code null} quando ausente/vazio. */
    public String valor(Paciente p) {
        String v = extrator.apply(p);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String dataNascimento(Paciente p) {
        return p.getDataNascimento() == null ? null : p.getDataNascimento().format(DATA);
    }

    private static String sexoLabel(Sexo s) {
        if (s == null) {
            return null;
        }
        return switch (s) {
            case MASCULINO -> "Masculino";
            case FEMININO -> "Feminino";
            case OUTRO -> "Outro";
            case NAO_INFORMADO -> "Não informado";
        };
    }
}
