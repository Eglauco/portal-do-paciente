package com.example.pop.procedimento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.pop.agendamento.Agendamento;
import com.example.pop.paciente.Paciente;
import com.example.pop.zapsign.ZapSignClient.CampoModelo;

/**
 * Resolve as variáveis dinâmicas ({@link VariavelTermo}) para os valores reais do paciente/atendimento,
 * montando os pares {@code de → para} que a ZapSign usa para substituir os {@code {{...}}} no Word.
 * É a contraparte do catálogo: tudo que a tela oferece para copiar é resolvido aqui.
 */
@Service
public class TermoVariavelResolver {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] MESES = {"janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"};

    /** Monta a lista {de, para} de todas as variáveis para o agendamento (paciente é o signatário). */
    public List<CampoModelo> resolver(Agendamento ag) {
        LocalDateTime agora = LocalDateTime.now();
        List<CampoModelo> campos = new ArrayList<>();
        for (VariavelTermo v : VariavelTermo.values()) {
            campos.add(new CampoModelo(v.token(), valor(v, ag, agora)));
        }
        return campos;
    }

    private String valor(VariavelTermo v, Agendamento ag, LocalDateTime agora) {
        Paciente p = ag.getPaciente();
        switch (v) {
            case NOME_PACIENTE: return nn(p.getNome());
            case CPF_PACIENTE: return mascararCpf(p.getCpf());
            case RG_PACIENTE: return nn(p.getRg());
            case DATA_NASCIMENTO: return p.getDataNascimento() == null ? "" : p.getDataNascimento().format(DATA);
            case IDADE_PACIENTE: return idade(p.getDataNascimento());
            case SEXO_PACIENTE: return p.getSexo() == null ? "" : sexo(p.getSexo().name());
            case NOME_MAE: return nn(p.getNomeMae());
            case NOME_PAI: return nn(p.getNomePai());
            case CNS_PACIENTE: return nn(p.getCns());
            case EMAIL_PACIENTE: return nn(p.getEmail());
            case TELEFONE_PACIENTE: return telefone(primeiroTelefone(p));
            case PRONTUARIO_PACIENTE: return nn(p.getProntuario());
            case ENDERECO_PACIENTE: return endereco(p);
            case BAIRRO_PACIENTE: return nn(p.getBairro());
            case CIDADE_PACIENTE: return nn(p.getMunicipio());
            case UF_PACIENTE: return nn(p.getUf());
            case CEP_PACIENTE: return mascararCep(p.getCep());
            case DATA_AGENDAMENTO: return ag.getDataHora() == null ? "" : ag.getDataHora().format(DATA);
            case HORA_AGENDAMENTO: return ag.getDataHora() == null ? "" : ag.getDataHora().format(HORA);
            case ESPECIALIDADE: return ag.getEspecialidade() == null ? "" : nn(ag.getEspecialidade().getNome());
            case PROFISSIONAL: return ag.getProfissionalSaude() == null ? "" : nn(ag.getProfissionalSaude().getNome());
            case REGISTRO_PROFISSIONAL: return ag.getProfissionalSaude() == null ? "" : nn(ag.getProfissionalSaude().getNumeroConselho());
            case NOME_PROCEDIMENTO: return ag.getProcedimento() == null ? "" : nn(ag.getProcedimento().getNome());
            case PREPARO_PROCEDIMENTO: return ag.getProcedimento() == null ? "" : nn(ag.getProcedimento().getPreparo());
            case UNIDADE: return ag.getUnidadeSaude() == null ? "" : nn(ag.getUnidadeSaude().getNome());
            case NOME_RESPONSAVEL: return ""; // signatário é o próprio paciente por ora
            case CPF_RESPONSAVEL: return "";
            case DATA_ATUAL: return agora.format(DATA);
            case DATA_EXTENSO: return porExtenso(agora.toLocalDate());
            case HORA_ATUAL: return agora.format(HORA);
            default: return "";
        }
    }

    // ---- formatação ----

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private static String primeiroTelefone(Paciente p) {
        List<String> tels = p.getTelefonesAdicionais();
        return tels == null || tels.isEmpty() ? null : tels.get(0);
    }

    private static String endereco(Paciente p) {
        StringBuilder sb = new StringBuilder();
        if (p.getRua() != null && !p.getRua().isBlank()) {
            sb.append(p.getRua().trim());
        }
        if (p.getNumero() != null && !p.getNumero().isBlank()) {
            sb.append(sb.length() > 0 ? ", " : "").append(p.getNumero().trim());
        }
        if (p.getComplemento() != null && !p.getComplemento().isBlank()) {
            sb.append(sb.length() > 0 ? " — " : "").append(p.getComplemento().trim());
        }
        return sb.toString();
    }

    private static String idade(LocalDate nascimento) {
        if (nascimento == null) {
            return "";
        }
        int anos = Period.between(nascimento, LocalDate.now()).getYears();
        return anos < 0 ? "" : anos + " anos";
    }

    private static String sexo(String name) {
        return switch (name) {
            case "MASCULINO" -> "Masculino";
            case "FEMININO" -> "Feminino";
            case "OUTRO" -> "Outro";
            default -> "Não informado";
        };
    }

    private static String porExtenso(LocalDate d) {
        return d.getDayOfMonth() + " de " + MESES[d.getMonthValue() - 1] + " de " + d.getYear();
    }

    private static String mascararCpf(String cpf) {
        String d = digitos(cpf);
        return d.length() == 11 ? d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9) : nn(cpf);
    }

    private static String mascararCep(String cep) {
        String d = digitos(cep);
        return d.length() == 8 ? d.substring(0, 5) + "-" + d.substring(5) : nn(cep);
    }

    private static String telefone(String tel) {
        String d = digitos(tel);
        if (d.length() == 11) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7);
        }
        if (d.length() == 10) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6);
        }
        return nn(tel);
    }

    private static String digitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }
}
