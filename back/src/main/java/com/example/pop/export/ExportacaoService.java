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

import com.example.pop.marca.MarcaService;
import com.example.pop.tema.PaletaTema;
import com.example.pop.tema.TemaService;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;

/**
 * Gera arquivos de exportação a partir de colunas + linhas, reutilizável por
 * qualquer tela: Excel (.xlsx) com cabeçalho em AutoFilter + painel congelado, e
 * PDF em paisagem com um layout minimalista e premium (tipografia hierárquica,
 * acento da marca, card de filtros, tabela sem grade pesada + rodapé paginado).
 * O chamador define as colunas e passa TODOS os registros que batem com os filtros.
 */
@Service
public class ExportacaoService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter GERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    /** Nome da plataforma (white-label) para o rodapé do PDF. */
    private final MarcaService marcaService;
    /** Paleta derivada da cor primária configurada (COR_PRIMARIA_PLATAFORMA) — mesma do app. */
    private final TemaService temaService;

    public ExportacaoService(MarcaService marcaService, TemaService temaService) {
        this.marcaService = marcaService;
        this.temaService = temaService;
    }

    /**
     * Filtra as colunas mantendo só as selecionadas (casando pelo título), na ordem
     * DEFINIDA. Seleção nula/vazia → todas as colunas (retrocompatível).
     */
    public static <T> List<ColunaExport<T>> filtrar(List<ColunaExport<T>> todas, List<String> selecionadas) {
        if (selecionadas == null || selecionadas.isEmpty()) {
            return todas;
        }
        return todas.stream().filter(c -> selecionadas.contains(c.titulo())).toList();
    }

    public static final String TIPO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    // Neutros (texto/linhas): NÃO seguem a cor da marca — ficam fixos para legibilidade.
    private static final Color INK = new Color(0x0C, 0x1F, 0x1C);
    private static final Color MUTED = new Color(0x56, 0x68, 0x63);
    private static final Color LINE = new Color(0xE2, 0xEA, 0xE6);
    private static final Color DIVIDER = new Color(0xB4, 0xC8, 0xC2);

    /**
     * Cores de marca do PDF, derivadas da cor primária configurada (COR_PRIMARIA_PLATAFORMA)
     * via {@link TemaService} — os mesmos tons do app. Os fundos claros são a marca misturada
     * com branco nas proporções que reproduzem o verde padrão e acompanham qualquer cor nova.
     */
    private record Paleta(Color brand, Color brandDeep, Color headerTint, Color cardBg, Color zebra) {
    }

    private Paleta paleta() {
        PaletaTema t = temaService.tema(); // deriva de COR_PRIMARIA_PLATAFORMA (fallback: verde padrão)
        Color brand = hex(t.brand());
        Color brandDeep = hex(t.brandDeep());
        return new Paleta(brand, brandDeep,
                mistura(brand, 0.09f),   // fundo do cabeçalho da tabela (era #EAF6F2 no verde)
                mistura(brand, 0.05f),   // fundo do card de filtros (era #F4FAF8)
                mistura(brand, 0.025f)); // zebra das linhas (era #F9FCFB)
    }

    /** Mistura a cor com branco: {@code peso} da marca + {@code (1-peso)} de branco. */
    private static Color mistura(Color base, float peso) {
        return new Color(
                Math.round(base.getRed() * peso + 255 * (1 - peso)),
                Math.round(base.getGreen() * peso + 255 * (1 - peso)),
                Math.round(base.getBlue() * peso + 255 * (1 - peso)));
    }

    /** "#RRGGBB" → Color (inválido → verde padrão da marca). */
    private static Color hex(String valor) {
        if (valor == null || !valor.trim().matches("^#[0-9A-Fa-f]{6}$")) {
            return new Color(0x0E, 0x8C, 0x7F);
        }
        String h = valor.trim();
        return new Color(Integer.parseInt(h.substring(1, 3), 16),
                Integer.parseInt(h.substring(3, 5), 16),
                Integer.parseInt(h.substring(5, 7), 16));
    }

    // ============================== Excel ==============================

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
                sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, dados.size()), 0, colunas.size() - 1));
                sheet.createFreezePane(0, 1);
                for (int c = 0; c < colunas.size(); c++) {
                    sheet.setColumnWidth(c, larguraExcel(colunas.get(c), dados));
                }
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gerar o Excel", e);
        }
    }

    // ============================== PDF ==============================

    /** PDF premium em paisagem: título, filtros aplicados e tabela minimalista paginada. */
    public <T> byte[] pdf(String titulo, List<FiltroAplicado> filtros, List<ColunaExport<T>> colunas, List<T> dados) {
        Document doc = new Document(PageSize.A4.rotate(), 38, 38, 52, 46);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Rodape(titulo, marcaService.nomePlataforma()));
            doc.open();

            Paleta pal = paleta();

            // Logomarca white-label no topo (se houver e for raster suportado); senão, só o título.
            adicionarLogo(doc);

            Paragraph tit = new Paragraph(titulo, fonte(19, com.lowagie.text.Font.BOLD, pal.brandDeep()));
            tit.setSpacingAfter(1);
            doc.add(tit);

            Paragraph sub = new Paragraph(
                    "Gerado em " + LocalDateTime.now(FUSO).format(GERADO) + "   ·   " + dados.size() + " registro(s)",
                    fonte(9, com.lowagie.text.Font.NORMAL, MUTED));
            sub.setSpacingAfter(9);
            doc.add(sub);

            doc.add(regua(pal.brand(), 1.4f));

            if (filtros != null && !filtros.isEmpty()) {
                doc.add(cartaoFiltros(pal, filtros));
            }

            if (!colunas.isEmpty()) {
                doc.add(tabela(pal, colunas, dados));
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

    /** Adiciona a logomarca configurada ao topo do PDF; ignora silenciosamente se não houver ou não for raster. */
    private void adicionarLogo(Document doc) {
        byte[] bytes = marcaService.logoBytes();
        if (bytes == null || bytes.length == 0) {
            return;
        }
        try {
            Image logo = Image.getInstance(bytes); // PNG/JPG/GIF; SVG/WebP lançam → cai no catch
            logo.scaleToFit(150, 34); // limita altura ~34pt mantendo proporção
            logo.setSpacingAfter(6);
            doc.add(logo);
        } catch (Exception e) {
            // formato não suportado pelo PDF (SVG/WebP) ou bytes inválidos → segue sem logo
        }
    }

    /** Card sutil com os filtros aplicados (acento à esquerda + rótulo minúsculo). */
    private static PdfPTable cartaoFiltros(Paleta pal, List<FiltroAplicado> filtros) {
        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100);
        card.setSpacingBefore(2);
        card.setSpacingAfter(12);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(pal.cardBg());
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderColorLeft(pal.brand());
        cell.setBorderWidthLeft(2.5f);
        cell.setPaddingTop(9);
        cell.setPaddingBottom(10);
        cell.setPaddingLeft(13);
        cell.setPaddingRight(12);

        Chunk rotulo = new Chunk("FILTROS APLICADOS", fonte(7, com.lowagie.text.Font.BOLD, MUTED));
        rotulo.setCharacterSpacing(1.1f);
        Paragraph labelP = new Paragraph(rotulo);
        labelP.setSpacingAfter(5);
        cell.addElement(labelP);

        Paragraph linha = new Paragraph();
        linha.setLeading(14);
        for (int i = 0; i < filtros.size(); i++) {
            FiltroAplicado f = filtros.get(i);
            linha.add(new Chunk(f.rotulo() + ": ", fonte(9.5f, com.lowagie.text.Font.BOLD, pal.brandDeep())));
            linha.add(new Chunk(nn(f.valor()), fonte(9.5f, com.lowagie.text.Font.NORMAL, INK)));
            if (i < filtros.size() - 1) {
                linha.add(new Chunk("     ·     ", fonte(9.5f, com.lowagie.text.Font.NORMAL, DIVIDER)));
            }
        }
        cell.addElement(linha);

        card.addCell(cell);
        return card;
    }

    /** Tabela minimalista: cabeçalho em tom da marca + linhas com hairline e zebra discreta. */
    private static <T> PdfPTable tabela(Paleta pal, List<ColunaExport<T>> colunas, List<T> dados) throws DocumentException {
        PdfPTable tabela = new PdfPTable(colunas.size());
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(2);
        tabela.setHeaderRows(1);
        tabela.setWidths(largurasRelativas(colunas, dados));

        com.lowagie.text.Font fonteCab = fonte(7.5f, com.lowagie.text.Font.BOLD, pal.brandDeep());
        for (ColunaExport<T> col : colunas) {
            Chunk ch = new Chunk(nn(col.titulo()).toUpperCase(), fonteCab);
            ch.setCharacterSpacing(0.4f);
            PdfPCell c = new PdfPCell(new Phrase(ch));
            c.setBackgroundColor(pal.headerTint());
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColorBottom(pal.brand());
            c.setBorderWidthBottom(1.3f);
            c.setPaddingTop(7);
            c.setPaddingBottom(7);
            c.setPaddingLeft(7);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            tabela.addCell(c);
        }

        com.lowagie.text.Font fonteCel = fonte(8f, com.lowagie.text.Font.NORMAL, INK);
        boolean zebra = false;
        for (T item : dados) {
            Color fundo = zebra ? pal.zebra() : Color.WHITE;
            for (ColunaExport<T> col : colunas) {
                PdfPCell c = new PdfPCell(new Phrase(nn(col.valor().apply(item)), fonteCel));
                c.setBackgroundColor(fundo);
                c.setBorder(Rectangle.BOTTOM);
                c.setBorderColorBottom(LINE);
                c.setBorderWidthBottom(0.5f);
                c.setPaddingTop(5.5f);
                c.setPaddingBottom(5.5f);
                c.setPaddingLeft(7);
                c.setVerticalAlignment(Element.ALIGN_MIDDLE);
                tabela.addCell(c);
            }
            zebra = !zebra;
        }
        return tabela;
    }

    /** Régua fina como acento visual (cor da marca). */
    private static Paragraph regua(Color cor, float espessura) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(new LineSeparator(espessura, 100, cor, Element.ALIGN_CENTER, 0)));
        p.setSpacingAfter(10);
        return p;
    }

    private static com.lowagie.text.Font fonte(float tamanho, int estilo, Color cor) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamanho, estilo, cor);
    }

    /** Larguras proporcionais ao conteúdo (limitadas), para colunas equilibradas. */
    private static <T> float[] largurasRelativas(List<ColunaExport<T>> colunas, List<T> dados) {
        float[] larguras = new float[colunas.size()];
        int amostra = Math.min(dados.size(), 200);
        for (int c = 0; c < colunas.size(); c++) {
            int max = colunas.get(c).titulo().length();
            for (int i = 0; i < amostra; i++) {
                String v = colunas.get(c).valor().apply(dados.get(i));
                if (v != null) {
                    max = Math.max(max, v.length());
                }
            }
            larguras[c] = Math.min(42f, Math.max(7f, max));
        }
        return larguras;
    }

    // ============================== Helpers ==============================

    private static String nn(String s) {
        return s == null ? "" : s;
    }

    private static <T> int larguraExcel(ColunaExport<T> col, List<T> dados) {
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

    /** Rodapé elegante (hairline + "Página X de Y") e título contínuo a partir da 2ª página. */
    private static final class Rodape extends PdfPageEventHelper {

        private final String titulo;
        private final String marca;
        private PdfTemplate totalPaginas;
        private BaseFont baseFont;

        Rodape(String titulo, String marca) {
            this.titulo = titulo;
            this.marca = marca;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document doc) {
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException("Falha ao carregar a fonte do PDF", e);
            }
            totalPaginas = writer.getDirectContent().createTemplate(40, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle pagina = doc.getPageSize();
            float esquerda = doc.leftMargin();
            float direita = pagina.getWidth() - doc.rightMargin();

            if (writer.getPageNumber() > 1) {
                float y = pagina.getHeight() - doc.topMargin() + 18;
                escrever(cb, titulo, esquerda, y, Element.ALIGN_LEFT);
                hairline(cb, esquerda, direita, y - 5);
            }

            float yRodape = doc.bottomMargin() - 18;
            hairline(cb, esquerda, direita, yRodape + 11);
            escrever(cb, marca + " · Relatório", esquerda, yRodape, Element.ALIGN_LEFT);

            String prefixo = "Página " + writer.getPageNumber() + " de ";
            float largura = baseFont.getWidthPoint(prefixo, 7.5f);
            escrever(cb, prefixo, direita - largura - 13, yRodape, Element.ALIGN_LEFT);
            cb.addTemplate(totalPaginas, direita - 13, yRodape);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document doc) {
            totalPaginas.beginText();
            totalPaginas.setFontAndSize(baseFont, 7.5f);
            totalPaginas.setColorFill(MUTED);
            totalPaginas.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPaginas.endText();
        }

        private void escrever(PdfContentByte cb, String texto, float x, float y, int alinhamento) {
            ColumnText.showTextAligned(cb, alinhamento,
                    new Phrase(texto, FontFactory.getFont(FontFactory.HELVETICA, 7.5f, MUTED)), x, y, 0);
        }

        private void hairline(PdfContentByte cb, float x1, float x2, float y) {
            cb.saveState();
            cb.setColorStroke(LINE);
            cb.setLineWidth(0.6f);
            cb.moveTo(x1, y);
            cb.lineTo(x2, y);
            cb.stroke();
            cb.restoreState();
        }
    }
}
