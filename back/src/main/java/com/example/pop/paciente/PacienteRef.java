package com.example.pop.paciente;

/**
 * Referência a um Paciente para EXIBIÇÃO no back-office. Leva, de uma vez, o nome que a tela mostra e
 * o que o admin precisa saber por baixo:
 * <ul>
 *   <li>{@code nome} — nome de EXIBIÇÃO (nome social quando o paciente usa; senão, o civil);</li>
 *   <li>{@code nomeRegistro} — nome CIVIL (de registro), revelado pelo admin ao passar o mouse/focar
 *       o marcador de "usa nome social";</li>
 *   <li>{@code temNomeSocial} — {@code true} quando o paciente usa nome social (a tela mostra o marcador).</li>
 * </ul>
 * O app do paciente usa apenas {@code nome} (ver {@link Paciente#nomeExibicao()}); o nome de registro
 * trafega para o admin, não é exibido no app.
 */
public record PacienteRef(Long id, String nome, String nomeRegistro, boolean temNomeSocial) {

    /** Monta a referência a partir do paciente (null-safe). */
    public static PacienteRef de(Paciente p) {
        if (p == null) {
            return null;
        }
        boolean usa = p.isUtilizaNomeSocial() && p.getNomeSocial() != null && !p.getNomeSocial().isBlank();
        return new PacienteRef(p.getId(), p.nomeExibicao(), p.getNome(), usa);
    }

    /** Forma para documento/relatório (sem hover): "Nome Social (Nome Civil)" quando usa social; senão, só o nome. */
    public String nomeDocumento() {
        return temNomeSocial ? nome + " (" + nomeRegistro + ")" : nome;
    }
}
