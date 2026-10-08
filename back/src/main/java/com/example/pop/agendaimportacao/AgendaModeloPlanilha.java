package com.example.pop.agendaimportacao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Gera a planilha-modelo (.xlsx) da importação de agenda, para o cliente baixar, preencher e reenviar.
 *
 * <p>Layout — <b>um arquivo = uma agenda</b> e <b>tudo por código/id em colunas separadas por tipo</b>, onde o
 * cliente preenche <b>apenas um</b> identificador por cadastro (evita colisão id × código). A aba "Agenda" tem o
 * bloco "DADOS DA AGENDA" (rótulo na coluna A, valor na coluna B) com data + profissional (id ou código) +
 * especialidade (id ou código) + Configuração da Agenda (id); a <b>unidade executante NÃO vai na planilha</b> —
 * é a do usuário logado. Abaixo, a tabela "HORÁRIOS": paciente (id, prontuário OU código) + hora início + hora
 * fim (sem status — toda marcação entra como "aguardando confirmação"). A aba "Instruções" explica os formatos.
 */
@Service
public class AgendaModeloPlanilha {

    public byte[] gerar() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Estilos e = new Estilos(wb);
            abaAgenda(wb, e);
            abaInstrucoes(wb, e);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Falha ao gerar a planilha de exemplo", ex);
        }
    }

    // ---------------- Aba "Agenda" ----------------

    private void abaAgenda(XSSFWorkbook wb, Estilos e) {
        Sheet s = wb.createSheet("Agenda");
        s.setColumnWidth(0, 36 * 256);
        s.setColumnWidth(1, 24 * 256);
        s.setColumnWidth(2, 30 * 256);
        s.setColumnWidth(3, 38 * 256);
        s.setColumnWidth(4, 12 * 256);

        // Título
        criar(s, 0, 0, "Modelo de importação de Agenda", e.titulo);
        mesclar(s, 0, 0, 4);
        criar(s, 1, 0, "Um arquivo = uma agenda. Tudo por CÓDIGO/ID. Profissional e Especialidade têm as duas colunas "
                + "(id interno e código de integração) — preencha APENAS UMA. Campos com * são obrigatórios. "
                + "Veja a aba \"Instruções\".", e.sub);
        mesclar(s, 1, 0, 4);

        // Bloco DADOS DA AGENDA
        criar(s, 3, 0, "DADOS DA AGENDA (preencha uma vez)", e.secao);
        mesclar(s, 3, 0, 4);

        // Data (valor na coluna B)
        criar(s, 4, 0, "Data*", e.rotulo);
        criar(s, 4, 1, "25/03/2026", e.exemplo);
        criar(s, 4, 3, "dd/mm/aaaa", e.dica);

        // Cabeçalho das DUAS colunas (id interno | código de integração), para Profissional e Especialidade.
        criar(s, 5, 0, "", e.rotulo);
        criar(s, 5, 1, "id interno", e.cabecalho);
        criar(s, 5, 2, "código de integração", e.cabecalho);
        criar(s, 5, 3, "← preencha APENAS UMA das duas", e.dica);

        // Profissional (exemplo preenchido por código) e Especialidade (exemplo por id).
        criar(s, 6, 0, "Profissional*", e.rotulo);
        criar(s, 6, 1, "", e.exemplo);
        criar(s, 6, 2, "1023", e.exemplo);

        criar(s, 7, 0, "Especialidade*", e.rotulo);
        criar(s, 7, 1, "15", e.exemplo);
        criar(s, 7, 2, "", e.exemplo);

        // Configuração da Agenda (só id interno) e Nome (opcional) — valor na coluna B.
        criar(s, 8, 0, "Configuração da Agenda (id interno)*", e.rotulo);
        criar(s, 8, 1, "7", e.exemplo);
        criar(s, 8, 3, "só id interno (não tem código de integração)", e.dica);

        criar(s, 9, 0, "Nome da agenda", e.rotulo);
        criar(s, 9, 1, "Manhã — Cardiologia", e.exemplo);
        criar(s, 9, 3, "opcional: um rótulo livre para a agenda", e.dica);

        criar(s, 10, 0, "A unidade executante NÃO vai aqui — é a unidade do usuário logado.", e.nota);
        mesclar(s, 10, 0, 4);

        // Bloco HORÁRIOS. As 2 primeiras linhas são exemplos (fictícios): o aviso fica no rótulo da seção,
        // NUNCA em uma linha abaixo da tabela — texto abaixo dos horários seria lido pelo parser como marcação.
        criar(s, 12, 0, "HORÁRIOS (um paciente por linha — preencha UMA coluna de paciente; as 2 primeiras são exemplos)",
                e.secao);
        mesclar(s, 12, 0, 4);
        Row cab = s.createRow(13);
        cabecalho(cab, e, 0, "Paciente (id interno)");
        cabecalho(cab, e, 1, "Paciente (prontuário)");
        cabecalho(cab, e, 2, "Paciente (código de integração)");
        cabecalho(cab, e, 3, "Hora início*");
        cabecalho(cab, e, 4, "Hora fim");

        exemploHorario(s, e, 14, "", "1001", "", "08:00", "08:30");
        exemploHorario(s, e, 15, "", "", "P-2002", "08:30", "09:00");
    }

    private void exemploHorario(Sheet s, Estilos e, int linha, String id, String prontuario, String codigo,
            String ini, String fim) {
        Row r = s.createRow(linha);
        criar(r, 0, id, e.exemplo);
        criar(r, 1, prontuario, e.exemplo);
        criar(r, 2, codigo, e.exemplo);
        criar(r, 3, ini, e.exemplo);
        criar(r, 4, fim, e.exemplo);
    }

    // ---------------- Aba "Instruções" ----------------

    private void abaInstrucoes(XSSFWorkbook wb, Estilos e) {
        Sheet s = wb.createSheet("Instruções");
        s.setColumnWidth(0, 100 * 256);

        int[] linha = {0};
        texto(s, linha, "Como preencher a importação de Agenda", e.titulo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "• Um arquivo importa UMA agenda (um slot). Não junte várias agendas no mesmo arquivo.", e.corpo);
        texto(s, linha, "• TUDO por código/id — nunca por nome. Cada cadastro tem colunas separadas por tipo de "
                + "identificador e você preenche APENAS UMA delas (se preencher mais de uma, o preview acusa erro).", e.corpo);
        texto(s, linha, "• Preencha o bloco DADOS DA AGENDA uma única vez (coluna B).", e.corpo);
        texto(s, linha, "• Liste um paciente por linha na tabela HORÁRIOS.", e.corpo);
        texto(s, linha, "• Campos marcados com * são obrigatórios.", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Profissional e Especialidade", e.secao);
        texto(s, linha, "Preencha o id interno OU o código de integração — apenas um dos dois.", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Configuração da Agenda", e.secao);
        texto(s, linha, "Preencha o id interno. (Esta entidade não possui código de integração.)", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Unidade executante", e.secao);
        texto(s, linha, "Não vai na planilha: é sempre a unidade do usuário logado que estiver importando.", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Paciente", e.secao);
        texto(s, linha, "Preencha o id interno OU o prontuário OU o código de integração — apenas um. O preview "
                + "mostra o nome encontrado para você conferir.", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Formatos", e.secao);
        texto(s, linha, "• Data: dd/mm/aaaa (ex.: 25/03/2026).", e.corpo);
        texto(s, linha, "• Hora início / Hora fim: HH:mm em 24h (ex.: 08:00, 14:30). Hora fim é opcional.", e.corpo);
        texto(s, linha, "• Códigos/prontuários com zero à esquerda: formate a célula como Texto para não perder o zero.",
                e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Status", e.secao);
        texto(s, linha, "Não existe coluna de status: toda marcação entra como \"Aguardando confirmação do paciente\".",
                e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Depois de preencher, volte à tela de Agendamentos, clique em \"Importar agenda (Excel)\" e "
                + "envie este arquivo. Você verá um preview com os erros destacados antes de confirmar.", e.corpo);
    }

    // ---------------- Helpers de célula ----------------

    private static void texto(Sheet s, int[] linha, String valor, CellStyle estilo) {
        criar(s, linha[0]++, 0, valor, estilo);
    }

    private static Cell criar(Sheet s, int linha, int col, String valor, CellStyle estilo) {
        Row r = s.getRow(linha);
        if (r == null) {
            r = s.createRow(linha);
        }
        return criar(r, col, valor, estilo);
    }

    private static Cell criar(Row r, int col, String valor, CellStyle estilo) {
        Cell c = r.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(estilo);
        return c;
    }

    private static void cabecalho(Row r, Estilos e, int col, String valor) {
        criar(r, col, valor, e.cabecalho);
    }

    private static void mesclar(Sheet s, int linha, int colIni, int colFim) {
        s.addMergedRegion(new CellRangeAddress(linha, linha, colIni, colFim));
    }

    /** Estilos reutilizados na planilha. */
    private static final class Estilos {
        final CellStyle titulo;
        final CellStyle sub;
        final CellStyle secao;
        final CellStyle rotulo;
        final CellStyle exemplo;
        final CellStyle dica;
        final CellStyle cabecalho;
        final CellStyle corpo;
        final CellStyle nota;

        Estilos(XSSFWorkbook wb) {
            Font fTitulo = wb.createFont();
            fTitulo.setBold(true);
            fTitulo.setFontHeightInPoints((short) 14);

            Font fBold = wb.createFont();
            fBold.setBold(true);

            Font fMuted = wb.createFont();
            fMuted.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

            titulo = wb.createCellStyle();
            titulo.setFont(fTitulo);

            sub = wrap(wb);
            sub.setFont(fMuted);

            secao = wb.createCellStyle();
            secao.setFont(fBold);
            secao.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            secao.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            secao.setVerticalAlignment(VerticalAlignment.CENTER);

            rotulo = wb.createCellStyle();
            rotulo.setFont(fBold);

            exemplo = wb.createCellStyle();
            exemplo.setBorderBottom(BorderStyle.HAIR);
            exemplo.setBorderTop(BorderStyle.HAIR);
            exemplo.setBorderLeft(BorderStyle.HAIR);
            exemplo.setBorderRight(BorderStyle.HAIR);

            dica = wb.createCellStyle();
            dica.setFont(fMuted);

            cabecalho = wb.createCellStyle();
            cabecalho.setFont(fBold);
            cabecalho.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cabecalho.setBorderBottom(BorderStyle.THIN);
            cabecalho.setAlignment(HorizontalAlignment.LEFT);

            corpo = wrap(wb);

            nota = wrap(wb);
            nota.setFont(fMuted);
        }

        private static CellStyle wrap(XSSFWorkbook wb) {
            CellStyle st = wb.createCellStyle();
            st.setWrapText(true);
            st.setVerticalAlignment(VerticalAlignment.TOP);
            return st;
        }
    }
}
