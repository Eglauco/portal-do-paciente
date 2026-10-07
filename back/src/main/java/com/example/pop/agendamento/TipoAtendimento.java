package com.example.pop.agendamento;

/**
 * Tipo de atendimento de uma {@link Agenda}/{@link Horario}: {@link #CONSULTA} ou {@link #EXAME}. Serve para
 * DIFERENCIAR o código de integração do CROSS no dedup/agrupamento — os id-spaces de consulta e de exame do CROSS
 * (ID_AGE_CONSULTA* × ID_AGE_EXAME*) podem colidir numericamente, então a identidade é (tipo + código). Nulo nas
 * agendas/horários manuais (sem código de integração).
 */
public enum TipoAtendimento {
    CONSULTA,
    EXAME;
}
