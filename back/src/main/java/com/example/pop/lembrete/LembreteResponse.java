package com.example.pop.lembrete;

/** Item do cadastro de lembretes de um procedimento (admin). */
public record LembreteResponse(Long id, String texto, Integer horasAntecedencia) {

    public static LembreteResponse from(Lembrete l) {
        return new LembreteResponse(l.getId(), l.getTexto(), l.getHorasAntecedencia());
    }
}
