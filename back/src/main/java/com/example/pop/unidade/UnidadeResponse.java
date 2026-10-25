package com.example.pop.unidade;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

import com.example.pop.storage.StorageService;

/**
 * Retorno da unidade para a tela de edição do admin: dados + perfil (logo/capa com URL assinada para
 * preview, além da URL crua para re-salvar) + FAQ + horários (ordenados segunda→domingo).
 */
public record UnidadeResponse(
        Long id,
        String nome,
        String codigoIntegracao,
        String logoUrl,
        String logoUrlView,
        String capaUrl,
        String capaUrlView,
        String sobre,
        String telefone,
        String endereco,
        List<FaqItem> faq,
        List<HorarioItem> horarios) {

    /** Validade da URL assinada do preview — longa p/ o cache do front não servir link morto. */
    private static final Duration VALIDADE_IMAGEM = Duration.ofHours(24);

    public record FaqItem(Long id, String pergunta, String resposta) {
    }

    public record HorarioItem(DayOfWeek diaSemana, boolean fechado, boolean vinteQuatroHoras,
            LocalTime abertura, LocalTime fechamento) {
    }

    public static UnidadeResponse from(Unidade u, StorageService storage) {
        List<FaqItem> faq = u.getFaq().stream()
                .map(f -> new FaqItem(f.getId(), f.getPergunta(), f.getResposta()))
                .toList();
        List<HorarioItem> horarios = u.getHorarios().stream()
                .sorted(Comparator.comparingInt(h -> h.getDiaSemana().getValue()))
                .map(h -> new HorarioItem(h.getDiaSemana(), h.isFechado(), h.isVinteQuatroHoras(),
                        h.getAbertura(), h.getFechamento()))
                .toList();
        return new UnidadeResponse(u.getId(), u.getNome(), u.getCodigoIntegracao(),
                u.getLogoUrl(), assinar(u.getLogoUrl(), storage),
                u.getCapaUrl(), assinar(u.getCapaUrl(), storage),
                u.getSobre(), u.getTelefone(), u.getEndereco(), faq, horarios);
    }

    private static String assinar(String url, StorageService storage) {
        return (url == null || url.isBlank()) ? null : storage.urlVisualizacao(url, VALIDADE_IMAGEM);
    }
}
