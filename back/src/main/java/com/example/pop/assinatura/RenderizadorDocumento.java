package com.example.pop.assinatura;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Preenche um modelo Word (.docx) substituindo as variáveis {@code {{...}}} pelos valores do paciente,
 * PRESERVANDO a formatação do Word — usado por provedores que não substituem variáveis no servidor
 * (ex.: Autentique, que aceita e converte o .docx com fidelidade). Usa Apache POI (já no projeto).
 *
 * <p>Substitui em duas passadas: (1) dentro de cada "run" (mantém toda a formatação quando o placeholder
 * está inteiro num run — o caso comum); (2) se um placeholder ficou quebrado entre runs do mesmo parágrafo,
 * concatena o parágrafo e escreve o texto substituído no 1º run (só esse parágrafo perde formatação inline).
 * Cobre parágrafos do corpo e das tabelas.
 */
@Service
public class RenderizadorDocumento {

    /** Preenche as variáveis do .docx e devolve o .docx resultante (mesmo formato). */
    public byte[] preencherDocx(byte[] docxBytes, Map<String, String> variaveis) {
        if (docxBytes == null || docxBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Modelo do termo vazio.");
        }
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            substituirTudo(doc, variaveis);
            return escrever(doc);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Não foi possível ler o modelo (.docx) do termo. Reenvie o arquivo em .docx válido.");
        }
    }

    /**
     * Combina vários .docx (já preenchidos) num único .docx, com quebra de página entre eles — usado no
     * modo de lote COMBINADO. Copia o conteúdo (parágrafos/tabelas) preservando a formatação direta.
     */
    public byte[] combinarDocx(List<byte[]> docxList) {
        if (docxList == null || docxList.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Nenhum termo para combinar.");
        }
        try (XWPFDocument base = new XWPFDocument(new ByteArrayInputStream(docxList.get(0)))) {
            for (int i = 1; i < docxList.size(); i++) {
                base.createParagraph().createRun().addBreak(BreakType.PAGE);
                try (XWPFDocument next = new XWPFDocument(new ByteArrayInputStream(docxList.get(i)))) {
                    for (IBodyElement el : next.getBodyElements()) {
                        if (el instanceof XWPFParagraph par) {
                            base.createParagraph().getCTP().set(par.getCTP());
                        } else if (el instanceof XWPFTable tbl) {
                            base.createTable().getCTTbl().set(tbl.getCTTbl());
                        }
                    }
                }
            }
            return escrever(base);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Falha ao combinar os termos (.docx).");
        }
    }

    /**
     * Anexa ao final do .docx um marcador de texto INVISÍVEL (branco, minúsculo) — usado como
     * {@code anchorString} pelo DocuSign para posicionar o campo de assinatura sem poluir o documento.
     * Se o provedor ignorar a âncora ausente, cai em assinatura livre; com a âncora, o campo fica no marcador.
     */
    public byte[] anexarAncora(byte[] docxBytes, String ancora) {
        if (docxBytes == null || docxBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Documento vazio para ancorar.");
        }
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docxBytes))) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText(ancora);
            r.setColor("FFFFFF"); // branco: invisível no fundo branco (o campo de assinatura cobre)
            r.setFontSize(2);
            return escrever(doc);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Falha ao preparar o documento (.docx).");
        }
    }

    // ---- helpers ----

    private static void substituirTudo(XWPFDocument doc, Map<String, String> variaveis) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            substituirParagrafo(p, variaveis);
        }
        for (XWPFTable t : doc.getTables()) {
            for (XWPFTableRow r : t.getRows()) {
                for (XWPFTableCell c : r.getTableCells()) {
                    for (XWPFParagraph p : c.getParagraphs()) {
                        substituirParagrafo(p, variaveis);
                    }
                }
            }
        }
    }

    private static void substituirParagrafo(XWPFParagraph p, Map<String, String> variaveis) {
        List<XWPFRun> runs = p.getRuns();
        if (runs == null || runs.isEmpty()) {
            return;
        }
        // Passada 1: substitui dentro de cada run (preserva a formatação no caso comum).
        for (XWPFRun run : runs) {
            String texto = run.getText(0);
            if (texto == null) {
                continue;
            }
            String novo = substituir(texto, variaveis);
            if (!novo.equals(texto)) {
                run.setText(novo, 0);
            }
        }
        // Passada 2: placeholder quebrado entre runs → funde o parágrafo no 1º run.
        String completo = textoParagrafo(runs);
        if (contemToken(completo, variaveis)) {
            runs.get(0).setText(substituir(completo, variaveis), 0);
            for (int i = 1; i < runs.size(); i++) {
                runs.get(i).setText("", 0);
            }
        }
    }

    private static String textoParagrafo(List<XWPFRun> runs) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : runs) {
            String t = run.getText(0);
            if (t != null) {
                sb.append(t);
            }
        }
        return sb.toString();
    }

    private static boolean contemToken(String texto, Map<String, String> variaveis) {
        for (String token : variaveis.keySet()) {
            if (texto.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static String substituir(String texto, Map<String, String> variaveis) {
        if (variaveis == null || variaveis.isEmpty()) {
            return texto;
        }
        String out = texto;
        for (Map.Entry<String, String> e : variaveis.entrySet()) {
            if (out.contains(e.getKey())) {
                out = out.replace(e.getKey(), e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }

    private static byte[] escrever(XWPFDocument doc) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao gerar o .docx preenchido.");
        }
    }
}
