package com.example.pop.agendaimportacao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

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

import com.example.pop.agendamento.StatusAgendamento;

/**
 * Gera a planilha-modelo (.xlsx) da importação de agenda, para o cliente baixar, preencher e reenviar.
 *
 * <p>Layout escolhido — <b>um arquivo = uma agenda</b> (regra do dono): a aba "Agenda" tem um bloco
 * "DADOS DA AGENDA" preenchido UMA vez (rótulo na coluna A, valor na coluna B) e, abaixo, a tabela
 * "HORÁRIOS" com uma linha por paciente. Assim o cliente não repete os dados do slot em cada linha (como
 * ocorreria numa planilha achatada), o que elimina a chance de digitar profissionais/datas diferentes entre
 * as linhas do mesmo arquivo. A aba "Instruções" explica formatos e os valores aceitos.
 *
 * <p>O modelo vem com um exemplo preenchido (dados fictícios) para o cliente ver o formato — ele apaga e
 * preenche com os próprios dados. A planilha é estática (não consulta o banco).
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
        s.setColumnWidth(0, 30 * 256);
        s.setColumnWidth(1, 34 * 256);
        s.setColumnWidth(2, 30 * 256);
        s.setColumnWidth(3, 18 * 256);
        s.setColumnWidth(4, 18 * 256);

        // Título
        criar(s, 0, 0, "Modelo de importação de Agenda", e.titulo);
        mesclar(s, 0, 0, 4);
        criar(s, 1, 0, "Um arquivo = uma agenda. Preencha os DADOS DA AGENDA uma vez e liste os pacientes em HORÁRIOS. "
                + "Campos com * são obrigatórios. Veja a aba \"Instruções\".", e.sub);
        mesclar(s, 1, 0, 4);

        // Bloco DADOS DA AGENDA
        criar(s, 3, 0, "DADOS DA AGENDA (preencha uma vez)", e.secao);
        mesclar(s, 3, 0, 4);
        linhaCampo(s, e, 4, "Data*", "25/03/2026", "Dia da agenda — dd/mm/aaaa");
        linhaCampo(s, e, 5, "Profissional*", "Dra. Ana Lima", "Nome como cadastrado OU o código de integração");
        linhaCampo(s, e, 6, "Especialidade*", "Cardiologia", "Nome como cadastrado OU o código de integração");
        linhaCampo(s, e, 7, "Configuração da Agenda*", "Consulta padrão", "Nome como cadastrado (regras do tipo de agenda)");
        linhaCampo(s, e, 8, "Unidade executante*", "Unidade Centro", "Nome como cadastrado OU o código de integração");
        linhaCampo(s, e, 9, "Nome da agenda", "Manhã — Cardiologia", "Opcional: um rótulo livre para a agenda");

        // Bloco HORÁRIOS. As 2 primeiras linhas são exemplos (dados fictícios): o aviso fica no rótulo da seção,
        // NUNCA em uma linha abaixo da tabela — texto abaixo dos horários seria lido pelo parser como uma marcação.
        criar(s, 11, 0, "HORÁRIOS (um paciente por linha — as 2 primeiras são exemplos; apague e preencha)", e.secao);
        mesclar(s, 11, 0, 4);
        Row cab = s.createRow(12);
        cabecalho(cab, e, 0, "Nome do paciente*");
        cabecalho(cab, e, 1, "CPF*");
        cabecalho(cab, e, 2, "Hora início*");
        cabecalho(cab, e, 3, "Hora fim");
        cabecalho(cab, e, 4, "Status");

        exemploHorario(s, e, 13, "Maria de Souza", "529.982.247-25", "08:00", "08:30", "Aguardando confirmação do paciente");
        exemploHorario(s, e, 14, "João Pereira", "111.444.777-35", "08:30", "09:00", "Paciente confirmou");
    }

    private void linhaCampo(Sheet s, Estilos e, int linha, String rotulo, String exemplo, String dica) {
        Row r = s.createRow(linha);
        criar(r, 0, rotulo, e.rotulo);
        criar(r, 1, exemplo, e.exemplo);
        criar(r, 2, dica, e.dica);
    }

    private void exemploHorario(Sheet s, Estilos e, int linha, String nome, String cpf, String ini, String fim,
            String status) {
        Row r = s.createRow(linha);
        criar(r, 0, nome, e.exemplo);
        criar(r, 1, cpf, e.exemplo);
        criar(r, 2, ini, e.exemplo);
        criar(r, 3, fim, e.exemplo);
        criar(r, 4, status, e.exemplo);
    }

    // ---------------- Aba "Instruções" ----------------

    private void abaInstrucoes(XSSFWorkbook wb, Estilos e) {
        Sheet s = wb.createSheet("Instruções");
        s.setColumnWidth(0, 100 * 256);

        int[] linha = {0};
        texto(s, linha, "Como preencher a importação de Agenda", e.titulo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "• Um arquivo importa UMA agenda (um slot). Não junte várias agendas no mesmo arquivo.", e.corpo);
        texto(s, linha, "• Preencha o bloco DADOS DA AGENDA uma única vez (coluna B).", e.corpo);
        texto(s, linha, "• Liste um paciente por linha na tabela HORÁRIOS.", e.corpo);
        texto(s, linha, "• Campos marcados com * são obrigatórios.", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Profissional, Especialidade, Configuração da Agenda e Unidade executante", e.secao);
        texto(s, linha, "Informe o NOME exatamente como está no cadastro OU o código de integração. "
                + "Profissional, Especialidade e Unidade aceitam código; Configuração da Agenda casa só por nome.", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Formatos", e.secao);
        texto(s, linha, "• Data: dd/mm/aaaa (ex.: 25/03/2026).", e.corpo);
        texto(s, linha, "• Hora início / Hora fim: HH:mm em 24h (ex.: 08:00, 14:30). Hora fim é opcional.", e.corpo);
        texto(s, linha, "• CPF: 11 dígitos, com ou sem pontuação (ex.: 529.982.247-25 ou 52998224725).", e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Status (opcional — em branco assume \"" + statusPadrao() + "\")", e.secao);
        texto(s, linha, "Use um destes valores: " + statusAceitos(), e.corpo);
        texto(s, linha, "", e.corpo);
        texto(s, linha, "Depois de preencher, volte à tela de Agendamentos, clique em \"Importar agenda (Excel)\" e "
                + "envie este arquivo. Você verá um preview com os erros destacados antes de confirmar.", e.corpo);
    }

    private static String statusPadrao() {
        return StatusAgendamento.AGUARDANDO_CONFIRMACAO_PACIENTE.getDescricao();
    }

    private static String statusAceitos() {
        return Arrays.stream(StatusAgendamento.values())
                .map(StatusAgendamento::getDescricao)
                .collect(Collectors.joining("; "));
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
        }

        private static CellStyle wrap(XSSFWorkbook wb) {
            CellStyle st = wb.createCellStyle();
            st.setWrapText(true);
            st.setVerticalAlignment(VerticalAlignment.TOP);
            return st;
        }
    }
}
