package com.example.pop.siresp;

import java.util.List;

/**
 * Mapeamento de um campo do paciente que pode ser atualizado/preenchido a partir do XML do SIRESP: a chave
 * (usada na config e no switch), o rótulo (modal), o elemento do XML de origem e o tamanho da coluna no Paciente
 * (para truncar com segurança). Telefones ficam de fora deste mapa escalar (são lista — tratados à parte).
 */
public record CampoMapeado(String chave, String rotulo, String xml, int tam) {

    /** Ordem amigável para o modal de configuração. */
    public static final List<CampoMapeado> CAMPOS = List.of(
            new CampoMapeado("nome", "Nome", "NOME_PACIENTE", 120),
            new CampoMapeado("sexo", "Sexo", "SEXO", 20),
            new CampoMapeado("dataNascimento", "Data de nascimento", "DT_NASCIMENTO", 10),
            new CampoMapeado("rg", "RG", "RG", 20),
            new CampoMapeado("cpf", "CPF", "CPF", 11),
            new CampoMapeado("nomeMae", "Nome da mãe", "NOME_MAE", 120),
            new CampoMapeado("nomePai", "Nome do pai", "NOME_PAI", 120),
            new CampoMapeado("rua", "Endereço (logradouro)", "ENDERECO", 160),
            new CampoMapeado("numero", "Número", "ENDERECO_NUMERO", 20),
            new CampoMapeado("bairro", "Bairro", "BAIRRO", 120),
            new CampoMapeado("municipio", "Município", "MUNICIPIO", 120),
            new CampoMapeado("uf", "UF", "UF", 2),
            new CampoMapeado("cep", "CEP", "CEP", 8),
            new CampoMapeado("email", "E-mail", "EMAIL", 160),
            new CampoMapeado("cns", "CNS", "NUM_CNS", 15),
            new CampoMapeado("prontuario", "Nº prontuário", "NUM_PRONTUARIO", 60));
}
