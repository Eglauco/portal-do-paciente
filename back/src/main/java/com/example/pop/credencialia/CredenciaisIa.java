package com.example.pop.credencialia;

/**
 * Credencial de IA resolvida (já decifrada) de um escopo. {@code apiKey} está presente quando o escopo
 * tem credencial ativa; {@code provedor} diz qual LLM usar; {@code baseUrl} é opcional (Azure/compatível).
 * Os modelos são do {@code provedor} escolhido no escopo.
 */
public record CredenciaisIa(
        TipoProvedorIa provedor,
        String apiKey,
        String baseUrl,
        String modeloModeracao,
        String modeloChat,
        String modeloProntuario) {

    /** Modelo configurado para a frente informada. */
    public String modeloDa(FrenteIa frente) {
        return switch (frente) {
            case MODERACAO -> modeloModeracao;
            case CHAT -> modeloChat;
            case PRONTUARIO -> modeloProntuario;
        };
    }
}
