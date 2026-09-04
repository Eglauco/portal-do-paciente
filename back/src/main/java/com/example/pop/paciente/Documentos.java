package com.example.pop.paciente;

/**
 * Normalização e validação (dígito verificador) de documentos do paciente:
 * CPF e CNS (Cartão Nacional de Saúde). Aceita valores com máscara.
 */
public final class Documentos {

    private Documentos() {
    }

    /** Mantém só os dígitos; devolve null quando fica vazio (campo opcional em branco). */
    public static String somenteDigitos(String valor) {
        if (valor == null) {
            return null;
        }
        String digitos = valor.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /** Valida CPF pelos dois dígitos verificadores (rejeita sequências repetidas). */
    public static boolean cpfValido(String cpf) {
        String d = somenteDigitos(cpf);
        if (d == null || d.length() != 11 || d.chars().distinct().count() == 1) {
            return false;
        }
        return digitoCpf(d, 9, 10) == (d.charAt(9) - '0')
                && digitoCpf(d, 10, 11) == (d.charAt(10) - '0');
    }

    private static int digitoCpf(String d, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (d.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /** Valida CNS (15 dígitos) pelo algoritmo do DATASUS (definitivo 1/2, provisório 7/8/9). */
    public static boolean cnsValido(String cns) {
        String d = somenteDigitos(cns);
        if (d == null || d.length() != 15) {
            return false;
        }
        char inicio = d.charAt(0);
        if (inicio == '1' || inicio == '2') {
            return cnsDefinitivo(d);
        }
        if (inicio == '7' || inicio == '8' || inicio == '9') {
            return somaPonderada(d, 15) % 11 == 0;
        }
        return false;
    }

    private static boolean cnsDefinitivo(String d) {
        String pis = d.substring(0, 11);
        int soma = somaPonderada(pis, 11);
        int resto = soma % 11;
        int dv = 11 - resto;
        if (dv == 11) {
            dv = 0;
        }
        if (dv == 10) {
            soma += 2;
            dv = 11 - (soma % 11);
            return d.equals(pis + "001" + dv);
        }
        return d.equals(pis + "000" + dv);
    }

    /** Soma dos {@code n} primeiros dígitos com pesos decrescentes de 15. */
    private static int somaPonderada(String d, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) {
            soma += (d.charAt(i) - '0') * (15 - i);
        }
        return soma;
    }
}
