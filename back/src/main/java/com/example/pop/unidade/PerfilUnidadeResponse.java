package com.example.pop.unidade;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import com.example.pop.storage.StorageService;

/**
 * Perfil público da unidade para o app do paciente (estilo "perfil de empresa" do WhatsApp):
 * logo/capa (URL assinada), nome, sobre, telefone, endereço e horários (segunda→domingo).
 */
public record PerfilUnidadeResponse(
        Long id,
        String nome,
        String logoUrl,
        String capaUrl,
        String sobre,
        String telefone,
        String endereco,
        Double latitude,
        Double longitude,
        List<Horario> horarios) {

    private static final Duration VALIDADE_IMAGEM = Duration.ofHours(6);

    public record Horario(DayOfWeek diaSemana, boolean fechado, boolean vinteQuatroHoras,
            LocalTime abertura, LocalTime fechamento) {
    }

    public static PerfilUnidadeResponse from(Unidade u, StorageService storage) {
        List<Horario> horarios = u.getHorarios().stream()
                .sorted(Comparator.comparingInt(h -> h.getDiaSemana().getValue()))
                .map(h -> new Horario(h.getDiaSemana(), h.isFechado(), h.isVinteQuatroHoras(),
                        h.getAbertura(), h.getFechamento()))
                .toList();
        return new PerfilUnidadeResponse(u.getId(), u.getNome(),
                assinar(u.getLogoUrl(), storage), assinar(u.getCapaUrl(), storage),
                u.getSobre(), u.getTelefone(), u.getEndereco(),
                u.getLatitude(), u.getLongitude(), horarios);
    }

    private static String assinar(String url, StorageService storage) {
        return (url == null || url.isBlank()) ? null : storage.urlVisualizacao(url, VALIDADE_IMAGEM);
    }
}
