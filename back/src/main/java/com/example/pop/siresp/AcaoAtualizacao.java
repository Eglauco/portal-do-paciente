package com.example.pop.siresp;

/** Política de atualização de um campo do paciente a partir do XML do SIRESP. */
public enum AcaoAtualizacao {
    /** Sempre sobrescreve o campo do paciente com o valor do XML (quando o XML traz valor). */
    SEMPRE,
    /** Só preenche se o campo do paciente estiver vazio (não sobrescreve dado existente). */
    SE_VAZIO,
    /** Nunca mexe no campo (padrão). */
    NUNCA
}
