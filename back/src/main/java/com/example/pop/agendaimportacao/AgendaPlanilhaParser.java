package com.example.pop.agendaimportacao;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lê a planilha de importação de agenda (layout do {@link AgendaModeloPlanilha}: um bloco de cabeçalho
 * "DADOS DA AGENDA" com rótulo na coluna A / valor na coluna B, seguido de uma tabela "HORÁRIOS" com uma
 * linha por paciente). Só extrai as células como texto já normalizado para exibição — NÃO resolve nomes
 * contra o banco nem valida regra de negócio; isso fica no {@link AgendaImportacaoService}, mantendo o parser
 * desacoplado das entidades (reconciliável no merge com a renomeação paralela de Procedimento).
 *
 * <p>A leitura é tolerante: casa os rótulos por texto normalizado (sem acento, minúsculo, sem o "*"), então a
 * ordem das colunas de horário e pequenas variações de rótulo não quebram o import.
 */
@Component
public class AgendaPlanilhaParser {

    private static final DateTimeFormatter DATA_PLANILHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_PLANILHA = DateTimeFormatter.ofPattern("HH:mm");

    /** Valores brutos (texto) extraídos da planilha, antes de qualquer resolução/validação. */
    public record Bruta(Map<String, String> agenda, List<LinhaBruta> horarios) {
    }

    public record LinhaBruta(int linhaExcel, String paciente, String cpf, String inicio, String fim, String status) {
    }

    public Bruta parse(byte[] bytes) {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null) {
                throw invalida();
            }

            Map<String, String> agenda = new LinkedHashMap<>();
            int linhaCabecalhoHorarios = -1;
            Map<String, Integer> colunas = null;

            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String rotuloA = normalizar(texto(row.getCell(0)));

                // Cabeçalho da tabela de horários: a partir daqui as linhas são marcações.
                if (ehCabecalhoHorarios(row)) {
                    linhaCabecalhoHorarios = r;
                    colunas = mapearColunasHorario(row);
                    break;
                }

                // Bloco da agenda: rótulo na coluna A, valor na coluna B.
                String chave = chaveAgenda(rotuloA);
                if (chave != null && !agenda.containsKey(chave)) {
                    Cell valor = row.getCell(1);
                    agenda.put(chave, "data".equals(chave) ? lerData(valor) : texto(valor));
                }
            }

            if (linhaCabecalhoHorarios < 0 || colunas == null || !colunas.containsKey("paciente")) {
                throw invalida();
            }

            List<LinhaBruta> horarios = new ArrayList<>();
            for (int r = linhaCabecalhoHorarios + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String paciente = texto(celula(row, colunas, "paciente"));
                String cpf = lerCpf(celula(row, colunas, "cpf"));
                String inicio = lerHora(celula(row, colunas, "inicio"));
                String fim = lerHora(celula(row, colunas, "fim"));
                String status = texto(celula(row, colunas, "status"));
                if (paciente.isBlank() && cpf.isBlank() && inicio.isBlank() && fim.isBlank() && status.isBlank()) {
                    continue; // linha totalmente vazia: ignora (não vira erro)
                }
                horarios.add(new LinhaBruta(r + 1, paciente, cpf, inicio, fim, status));
            }

            return new Bruta(agenda, horarios);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw invalida();
        }
    }

    // ---------------- Rótulos ----------------

    private static String chaveAgenda(String rotulo) {
        if (rotulo.equals("data")) {
            return "data";
        }
        if (rotulo.startsWith("profissional")) {
            return "profissional";
        }
        if (rotulo.startsWith("especialidade")) {
            return "especialidade";
        }
        if (rotulo.startsWith("configuracao")) {
            return "config";
        }
        if (rotulo.startsWith("unidade")) {
            return "unidade";
        }
        if (rotulo.startsWith("nome da agenda")) {
            return "nome";
        }
        return null;
    }

    private static boolean ehCabecalhoHorarios(Row row) {
        for (Cell c : row) {
            if (normalizar(texto(c)).startsWith("nome do paciente")) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Integer> mapearColunasHorario(Row cabecalho) {
        Map<String, Integer> colunas = new LinkedHashMap<>();
        for (Cell c : cabecalho) {
            String r = normalizar(texto(c));
            if (r.isBlank()) {
                continue;
            }
            if (r.contains("paciente")) {
                colunas.putIfAbsent("paciente", c.getColumnIndex());
            } else if (r.contains("cpf")) {
                colunas.putIfAbsent("cpf", c.getColumnIndex());
            } else if (r.contains("inicio")) {
                colunas.putIfAbsent("inicio", c.getColumnIndex());
            } else if (r.contains("fim")) {
                colunas.putIfAbsent("fim", c.getColumnIndex());
            } else if (r.contains("status")) {
                colunas.putIfAbsent("status", c.getColumnIndex());
            }
        }
        return colunas;
    }

    private static Cell celula(Row row, Map<String, Integer> colunas, String chave) {
        Integer idx = colunas.get(chave);
        return idx == null ? null : row.getCell(idx);
    }

    // ---------------- Leitura de células ----------------

    /** Texto genérico (nomes, códigos, CPF/hora digitados como texto). Número inteiro vira string sem casas. */
    private static String texto(Cell c) {
        if (c == null) {
            return "";
        }
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case NUMERIC -> numeroParaTexto(c);
            case FORMULA -> formulaParaTexto(c);
            default -> "";
        };
    }

    private static String numeroParaTexto(Cell c) {
        double v = c.getNumericCellValue();
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private static String formulaParaTexto(Cell c) {
        try {
            return switch (c.getCachedFormulaResultType()) {
                case STRING -> c.getStringCellValue().trim();
                case NUMERIC -> numeroParaTexto(c);
                case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
                default -> "";
            };
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Data: célula de data do Excel vira dd/MM/yyyy; senão devolve o texto como digitado. */
    private static String lerData(Cell c) {
        if (c != null && c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            LocalDateTime dt = c.getLocalDateTimeCellValue();
            return dt == null ? "" : dt.toLocalDate().format(DATA_PLANILHA);
        }
        return texto(c);
    }

    /** Hora: célula de hora do Excel vira HH:mm; senão devolve o texto como digitado. */
    private static String lerHora(Cell c) {
        if (c != null && c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            LocalDateTime dt = c.getLocalDateTimeCellValue();
            return dt == null ? "" : dt.toLocalTime().format(HORA_PLANILHA);
        }
        return texto(c);
    }

    /** CPF digitado como número perde o zero à esquerda — recompõe com padding para 11 dígitos. */
    private static String lerCpf(Cell c) {
        if (c != null && c.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(c)) {
            long v = (long) c.getNumericCellValue();
            String s = Long.toString(v);
            return s.length() < 11 ? "0".repeat(11 - s.length()) + s : s;
        }
        return texto(c);
    }

    // ---------------- Helpers ----------------

    /** minúsculo, sem acento, sem o "*" de obrigatório, espaços colapsados. */
    static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return semAcento.toLowerCase().replace("*", "").replaceAll("\\s+", " ").trim();
    }

    private static ResponseStatusException invalida() {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Planilha em formato inesperado. Baixe a planilha de exemplo e preencha sobre ela.");
    }
}
