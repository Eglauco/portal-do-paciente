package com.example.pop.paciente;

import java.time.LocalDate;
import java.util.List;

/**
 * Dados do paciente logado para a tela "Meu perfil" do app (somente leitura).
 * Não expõe campos internos (hash do código, aparelho ativo). {@code fotoUrl} já
 * vem como link temporário de visualização (GET pré-assinado), ou null.
 */
public record MeuPerfilResponse(
        Long id,
        String nome,
        String telefone,
        List<String> telefonesAdicionais,
        String email,
        String cpf,
        String rg,
        String cns,
        LocalDate dataNascimento,
        Sexo sexo,
        String nomeMae,
        String nomePai,
        String codigoIntegracao,
        String prontuario,
        String rua,
        String numero,
        String complemento,
        String bairro,
        String municipio,
        String uf,
        String cep,
        String fotoUrl) {

    public static MeuPerfilResponse from(Paciente p, String fotoUrl) {
        return new MeuPerfilResponse(
                p.getId(), p.getNome(), p.getTelefone(), List.copyOf(p.getTelefonesAdicionais()),
                p.getEmail(), p.getCpf(), p.getRg(), p.getCns(), p.getDataNascimento(), p.getSexo(),
                p.getNomeMae(), p.getNomePai(), p.getCodigoIntegracao(), p.getProntuario(),
                p.getRua(), p.getNumero(), p.getComplemento(), p.getBairro(), p.getMunicipio(),
                p.getUf(), p.getCep(), fotoUrl);
    }
}
