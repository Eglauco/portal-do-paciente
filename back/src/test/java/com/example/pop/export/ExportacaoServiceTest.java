package com.example.pop.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExportacaoServiceTest {

    private final ExportacaoService service = new ExportacaoService();

    private record Linha(String nome, int idade) {
    }

    private static final List<ColunaExport<Linha>> COLUNAS = List.of(
            ColunaExport.de("Nome", Linha::nome),
            ColunaExport.de("Idade", l -> String.valueOf(l.idade())));

    @Test
    void geraExcelEPdfValidos() {
        List<Linha> dados = List.of(new Linha("Ana", 30), new Linha("Bruno", 25));

        byte[] xlsx = service.excel("Teste", COLUNAS, dados);
        assertTrue(xlsx.length > 0);
        // .xlsx é um zip → assinatura "PK".
        assertEquals('P', (char) xlsx[0]);
        assertEquals('K', (char) xlsx[1]);

        byte[] pdf = service.pdf("Teste", List.of(new FiltroAplicado("Status", "Todos")), COLUNAS, dados);
        assertTrue(pdf.length > 0);
        // PDF começa com "%PDF".
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
    }

    @Test
    void geraComListaVazia() {
        assertTrue(service.excel("Vazio", COLUNAS, List.of()).length > 0);
        // Sem filtros também deve gerar (branch sem "Filtros aplicados").
        assertTrue(service.pdf("Vazio", List.of(), COLUNAS, List.of()).length > 0);
    }

    /** Gera uma amostra realista do PDF premium em target/ para conferência visual. */
    @Test
    void amostraPremiumParaConferencia() throws Exception {
        record Ag(String dataHora, String paciente, String unidade, String especialidade,
                String profissional, String procedimento, String status, String justificativa) {
        }
        List<ColunaExport<Ag>> colunas = List.of(
                ColunaExport.de("Data/Hora", Ag::dataHora),
                ColunaExport.de("Paciente", Ag::paciente),
                ColunaExport.de("Unidade", Ag::unidade),
                ColunaExport.de("Especialidade", Ag::especialidade),
                ColunaExport.de("Profissional", Ag::profissional),
                ColunaExport.de("Procedimento", Ag::procedimento),
                ColunaExport.de("Status", Ag::status),
                ColunaExport.de("Justificativa", Ag::justificativa));

        String[] pacientes = { "Ana Beatriz Souza", "Bruno Carvalho Lima", "Carla Mendes", "Diego Rocha",
                "Eduarda Nogueira", "Felipe Andrade", "Gabriela Martins", "Henrique Oliveira", "Isabela Ferreira",
                "João Pedro Castro", "Larissa Ramos", "Marcelo Tavares" };
        String[] especialidades = { "Cardiologia", "Dermatologia", "Clínica Geral", "Ortopedia", "Pediatria" };
        String[] profissionais = { "Dr. Rafael Lima", "Dra. Marina Alves", "Dr. Paulo Nunes", "Dra. Sofia Reis" };
        String[] procedimentos = { "Consulta", "Retorno", "Exame de sangue", "Ultrassonografia", "Eletrocardiograma" };
        String[] status = { "Aguardando confirmação do paciente", "Paciente confirmou", "Presença do paciente",
                "Falta do paciente" };
        String[] justificativas = { "", "", "", "Trânsito intenso na região", "", "Imprevisto de trabalho", "" };

        List<Ag> dados = new ArrayList<>();
        for (int i = 0; i < 46; i++) {
            String dh = String.format("%02d/09/2026 %02d:%02d", (i % 28) + 1, 8 + (i % 9), (i % 2) * 30);
            dados.add(new Ag(dh, pacientes[i % pacientes.length], "Unidade de Saúde Central",
                    especialidades[i % especialidades.length], profissionais[i % profissionais.length],
                    procedimentos[i % procedimentos.length], status[i % status.length],
                    justificativas[i % justificativas.length]));
        }

        List<FiltroAplicado> filtros = List.of(
                new FiltroAplicado("Status", "Todos"),
                new FiltroAplicado("Unidade", "Unidade de Saúde Central"));

        byte[] pdf = service.pdf("Agendamentos", filtros, colunas, dados);
        assertTrue(pdf.length > 0);
        Files.write(Path.of("target", "exportacao-agendamentos-amostra.pdf"), pdf);
    }
}
