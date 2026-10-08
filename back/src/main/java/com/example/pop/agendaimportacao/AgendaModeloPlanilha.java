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
 * <p>Layout — <b>um arquivo = uma agenda</b> e <b>tudo por código/id, nunca por nome</b> (p/ não haver
 * divergência). A aba "Agenda" tem o bloco "DADOS DA AGENDA" (rótulo na coluna A, valor na coluna B) com data +
 * profissional + especialidade + Configuração da Agenda; a <b>unidade executante NÃO vai na planilha</b> — é a
 * unidade do usuário logado. Abaixo, a tabela "HORÁRIOS" com uma linha por paciente: identificador do paciente +
 * hora início + hora fim (sem status — toda marcação entra como "aguardando confirmação"). A aba "Instruções"
 * explica os formatos. A planilha é estática (não consulta o banco).
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
        s.setColumnWidth(0, 34 * 256);
        s.setColumnWidth(1, 24 * 256);
        s.setColumnWidth(2, 44 * 256);

        // Título
        criar(s, 0, 0, "Modelo de importação de Agenda", e.titulo);
        mesclar(s, 0, 0, 2);
        criar(s, 1, 0, "Um arquivo = uma agenda. Tudo por CÓDIGO/ID (nunca por nome). Preencha os DADOS DA AGENDA "
                + "uma vez e liste os pacientes em HORÁRIOS. Campos com * são obrigatórios. Veja a aba \"Instruções\".",
                e.sub);
        mesclar(s, 1, 0, 2);

        // Bloco DADOS DA AGENDA
        criar(s, 3, 0, "DADOS DA AGENDA (preencha uma vez)", e.secao);
        mesclar(s, 3, 0, 2);
        linhaCampo(s, e, 4, "Data*", "25/03/2026", "Dia da agenda — dd/mm/aaaa");
        linhaCampo(s, e, 5, "Profissional (id ou código)*", "1023", "Id interno OU código de integração");
        linhaCampo(s, e, 6, "Especialidade (id ou código)*", "15", "Id interno OU código de integração");
        linhaCampo(s, e, 7, "Configuração da Agenda (id)*", "7", "Id interno do cadastro (não tem código)");
        linhaCampo(s, e, 8, "Nome da agenda", "Manhã — Cardiologia", "Opcional: um rótulo livre para a agenda");
        criar(s, 9, 0, "A unidade executante NÃO vai aqui — é a unidade do usuário logado.", e.nota);
        mesclar(s, 9, 0, 2);

        // Bloco HORÁRIOS. As 2 primeiras linhas são exemplos (fictícios): o aviso fica no rótulo da seção,
        // NUNCA em uma linha abaixo da tabela — texto abaixo dos horários seria lido pelo parser como marcação.
        criar(s, 11, 0, "HORÁRIOS (um paciente por linha — as 2 primeiras são exemplos; apague e preencha)", e.secao);
        mesclar(s, 11, 0, 2);
        Row cab = s.createRow(12);
        cabecalho(cab, e, 0, "Paciente (id, prontuário ou código)*");
        cabecalho(cab, e, 1, "Hora início*");
        cabecalho(cab, e, 2, "Hora fim");

        exemploHorario(s, e, 13, "1001", "08:00", "08:30");
        exemploHorario(s, e, 14, "1002", "08:30", "09:00");
    }

    private void linhaCampo(Sheet s, Estilos e, int linha, String rotulo, String exemplo, String dica) {
        Row r = s.createRow(linha);
        criar(r, 0, rotulo, e.rotulo);
        criar(r, 1, exemplo, e.exemplo);
        criar(r, 2, dica, e.dica);
    }

    private void exemploHorario(Sheet s, Estilos e, int linha, String paciente, String ini, String fim) {
        Row r = s.createRow(linha);
        criar(r, 0, paciente, e.exemplo);
        criar(r, 1, ini, e.exemplo);
        criar(r, 2, fim, e.exemplo);
    }

    // ---------------- Aba "Instruções" ----------------

    private void abaInstrucoes(XSSFWorkbook wb, Estilos e) {
        Sheet s = wb.createSheet("Instruções");
        s.setColumnWidth(0, 100 * 256);

        int[] linha = {0};
        texto(s, linha, "Como preencher a importação de Agenda", e.titulo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "• Um arquivo importa UMA agenda (um slot). Não junte várias agendas no mesmo arquivo.", e.corpo);
        texto(s, linha, "• TUDO por código/id — nunca por nome, para não haver divergência de cadastro.", e.corpo);
        texto(s, linha, "• Preencha o bloco DADOS DA AGENDA uma única vez (coluna B).", e.corpo);
        texto(s, linha, "• Liste um paciente por linha na tabela HORÁRIOS.", e.corpo);
        texto(s, linha, "• Campos marcados com * são obrigatórios.", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Profissional e Especialidade", e.secao);
        texto(s, linha, "Informe o id interno OU o código de integração. Se o valor existir como código, o código "
                + "tem precedência; senão tentamos o id interno.", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Configuração da Agenda", e.secao);
        texto(s, linha, "Informe o id interno do cadastro. (Esta entidade não possui código de integração.)", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Unidade executante", e.secao);
        texto(s, linha, "Não vai na planilha: é sempre a unidade do usuário logado que estiver importando.", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Paciente", e.secao);
        texto(s, linha, "Localizado por código de integração → número do prontuário → id interno (nessa ordem). "
                + "O preview mostra o nome encontrado para você conferir.", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Formatos", e.secao);
        texto(s, linha, "• Data: dd/mm/aaaa (ex.: 25/03/2026).", e.corpo);
        texto(s, linha, "• Hora início / Hora fim: HH:mm em 24h (ex.: 08:00, 14:30). Hora fim é opcional.", e.corpo);
        texto(s, linha, "• Códigos com zero à esquerda: formate a célula como Texto para não perder o zero.", e.corpo);
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
