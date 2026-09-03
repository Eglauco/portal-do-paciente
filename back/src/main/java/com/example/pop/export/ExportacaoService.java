package com.example.pop.export;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Gera arquivos de exportação a partir de colunas + linhas, reutilizável por
 * qualquer tela: Excel (.xlsx) com cabeçalho em AutoFilter + painel congelado, e
 * PDF em paisagem com a tabela completa. O chamador define as colunas e passa
 * TODOS os registros que batem com os filtros da tela.
 */
@Service
public class ExportacaoService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter GERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color BRAND = new Color(14, 140, 127);

    public static final String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Excel: cabeçalho em negrito + AutoFilter em todo o intervalo, cabeçalho congelado. */
    public <T> byte[] excel(String nomeAba, List<ColunaExport<T>> colunas, List<T> dados) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(WorkbookUtil.createSafeSheetName(nomeAba));

            CellStyle estiloCab = wb.createCellStyle();
            Font negrito = wb.createFont();
            negrito.setBold(true);
            estiloCab.setFont(negrito);
            estiloCab.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            estiloCab.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estiloCab.setBorderBottom(BorderStyle.THIN);

            Row cabecalho = sheet.createRow(0);
            for (int c = 0; c < colunas.size(); c++) {
                Cell cell = cabecalho.createCell(c);
                cell.setCellValue(colunas.get(c).titulo());
                cell.setCellStyle(estiloCab);
            }

            for (int r = 0; r < dados.size(); r++) {
                Row linha = sheet.createRow(r + 1);
                T item = dados.get(r);
                for (int c = 0; c < colunas.size(); c++) {
                    linha.createCell(c).setCellValue(nn(colunas.get(c).valor().apply(item)));
                }
            }

            if (!colunas.isEmpty()) {
                // Filtros no cabeçalho sobre o intervalo inteiro (cabeçalho + dados).
                sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, dados.size()), 0, colunas.size() - 1));
                sheet.createFreezePane(0, 1);
                for (int c = 0; c < colunas.size(); c++) {
                    sheet.setColumnWidth(c, larguraColuna(colunas.get(c), dados));
                }
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar o Excel", e);
        }
    }

    /** PDF em paisagem: título + data de geração + tabela com o cabeçalho repetido nas páginas. */
    public <T> byte[] pdf(String titulo, List<ColunaExport<T>> colunas, List<T> dados) {
        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 28, 24);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            doc.add(new Paragraph(
                    "Gerado em " + LocalDateTime.now(FUSO).format(GERADO) + " · " + dados.size() + " registro(s)",
                    FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY)));
            doc.add(new Paragraph(" "));

            if (!colunas.isEmpty()) {
                PdfPTable tabela = new PdfPTable(colunas.size());
                tabela.setWidthPercentage(100);
                var fonteCab = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.WHITE);
                var fonteCel = FontFactory.getFont(FontFactory.HELVETICA, 7.5f);
                for (ColunaExport<T> col : colunas) {
                    PdfPCell cell = new PdfPCell(new Phrase(col.titulo(), fonteCab));
                    cell.setBackgroundColor(BRAND);
                    cell.setPadding(4);
                    tabela.addCell(cell);
                }
                tabela.setHeaderRows(1);
                for (T item : dados) {
                    for (ColunaExport<T> col : colunas) {
                        PdfPCell cell = new PdfPCell(new Phrase(nn(col.valor().apply(item)), fonteCel));
                        cell.setPadding(3);
                        tabela.addCell(cell);
                    }
                }
                doc.add(tabela);
            }
            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            if (doc.isOpen()) {
                doc.close();
            }
            throw new IllegalStateException("Falha ao gerar o PDF", e);
        }
    }

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    /** Largura ~ maior conteúdo (amostra), limitada entre 12 e 60 caracteres. */
    private static <T> int larguraColuna(ColunaExport<T> col, List<T> dados) {
        int max = col.titulo().length();
        int amostra = Math.min(dados.size(), 300);
        for (int i = 0; i < amostra; i++) {
            String v = col.valor().apply(dados.get(i));
            if (v != null) {
                max = Math.max(max, v.length());
            }
        }
        return Math.min(60, Math.max(12, max + 2)) * 256;
    }
}
