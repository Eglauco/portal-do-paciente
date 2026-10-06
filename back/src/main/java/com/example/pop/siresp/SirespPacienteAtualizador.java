package com.example.pop.siresp;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteLogService;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.Sexo;
import com.example.pop.unidade.UnidadeRepository;

/**
 * Processa UMA Mensagem do SIRESP contra o cadastro de pacientes: casa por {@code codigoIntegracao} = COD_PACIENTE
 * (e, se habilitado e não achar, por CPF como fallback). Se achar, ATUALIZA campo a campo conforme a política; se
 * não achar e "criar" estiver ligado, CRIA o paciente completo com os dados do XML. Audita (LGPD) com autor
 * IMPORTACAO_SIRESP (criação e alteração).
 *
 * <p>Cada paciente roda em transação PRÓPRIA ({@code REQUIRES_NEW}): uma falha (ex.: CPF duplicado) não derruba a
 * importação nem os outros pacientes.
 */
@Component
public class SirespPacienteAtualizador {

    public enum Resultado {
        NADA, ATUALIZADO, CRIADO
    }

    private final PacienteRepository pacienteRepository;
    private final UnidadeRepository unidadeRepository;
    private final PacienteLogService logService;

    public SirespPacienteAtualizador(PacienteRepository pacienteRepository, UnidadeRepository unidadeRepository,
            PacienteLogService logService) {
        this.pacienteRepository = pacienteRepository;
        this.unidadeRepository = unidadeRepository;
        this.logService = logService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resultado processar(String codPaciente, Map<String, String> xml, Map<String, AcaoAtualizacao> acoes,
            boolean atualizar, boolean criar, Long unidadeId, Long usuarioId) {
        if (codPaciente == null || codPaciente.isBlank()) {
            return Resultado.NADA;
        }
        String cod = codPaciente.trim();
        Paciente p = pacienteRepository.findByCodigoIntegracao(cod).orElse(null);

        // Fallback por CPF (só quando "criar" está ligado): evita duplicar quem já existe sem código de integração.
        boolean vinculadoPorCpf = false;
        if (p == null && criar) {
            String cpf = digitos(nz(xml.get("CPF")));
            if (cpf.length() == 11) {
                p = pacienteRepository.findByCpf(cpf).orElse(null);
                vinculadoPorCpf = p != null;
            }
        }

        if (p != null) {
            PacienteLogService.SnapshotPaciente antes = logService.snapshot(p);
            boolean mudou = false;
            if (vinculadoPorCpf && !preenchido(p.getCodigoIntegracao())) {
                p.setCodigoIntegracao(trunc(cod, 60)); // amarra o código p/ casar direto nas próximas importações
                mudou = true;
            }
            if (atualizar) {
                for (CampoMapeado campo : CampoMapeado.CAMPOS) {
                    AcaoAtualizacao acao = acoes.getOrDefault(campo.chave(), AcaoAtualizacao.NUNCA);
                    if (acao == AcaoAtualizacao.NUNCA) {
                        continue;
                    }
                    String valor = xml.get(campo.xml());
                    if (valor == null || valor.isBlank()) {
                        continue;
                    }
                    if (aplicar(p, campo.chave(), valor.trim(), campo.tam(), acao == AcaoAtualizacao.SE_VAZIO)) {
                        mudou = true;
                    }
                }
            }
            if (!mudou) {
                return Resultado.NADA;
            }
            pacienteRepository.save(p);
            logService.registrarAlteracaoSiresp(antes, p, usuarioId);
            return Resultado.ATUALIZADO;
        }

        // Não encontrado por nenhum caminho: cria se habilitado.
        if (!criar) {
            return Resultado.NADA;
        }
        Paciente novo = montarNovo(cod, xml, unidadeId);
        if (novo == null) {
            return Resultado.NADA; // sem nome → não dá para cadastrar
        }
        pacienteRepository.save(novo);
        logService.registrarCriacaoSiresp(novo, usuarioId);
        return Resultado.CRIADO;
    }

    /** Monta um paciente NOVO completo com os dados do XML (todos os campos + telefones + unidade). null se sem nome. */
    private Paciente montarNovo(String cod, Map<String, String> xml, Long unidadeId) {
        String nome = nz(xml.get("NOME_PACIENTE")).trim();
        if (nome.isBlank()) {
            return null;
        }
        Paciente p = new Paciente();
        p.setCodigoIntegracao(trunc(cod, 60));
        p.setAtivo(false); // ativação (código de acesso do app) é feita depois, manualmente
        // Preenche TODOS os campos mapeados (cadastro completo; a matriz de ações rege só o UPDATE de quem já existe).
        for (CampoMapeado campo : CampoMapeado.CAMPOS) {
            String valor = xml.get(campo.xml());
            if (valor != null && !valor.isBlank()) {
                aplicar(p, campo.chave(), valor.trim(), campo.tam(), false);
            }
        }
        adicionarTelefone(p, xml.get("TEL_CELULAR_DDD"), xml.get("TEL_CELULAR"));
        adicionarTelefone(p, xml.get("TEL_RES_DDD"), xml.get("TEL_RES"));
        if (unidadeId != null) {
            unidadeRepository.findById(unidadeId).ifPresent(u -> p.getUnidades().add(u));
        }
        return p;
    }

    private static void adicionarTelefone(Paciente p, String ddd, String numero) {
        String n = digitos(nz(numero));
        if (n.length() < 8) {
            return; // sem número válido
        }
        String completo = digitos(nz(ddd)) + n;
        if (!p.getTelefonesAdicionais().contains(completo)) {
            p.getTelefonesAdicionais().add(completo);
        }
    }

    /** Normaliza e aplica um campo; respeita "só se vazio". @return true se de fato alterou. */
    private boolean aplicar(Paciente p, String chave, String v, int tam, boolean soSeVazio) {
        switch (chave) {
            case "nome":
                if (soSeVazio && preenchido(p.getNome())) return false;
                p.setNome(trunc(v, tam));
                return true;
            case "sexo":
                Sexo s = mapearSexo(v);
                if (s == null) return false;
                if (soSeVazio && p.getSexo() != null) return false;
                p.setSexo(s);
                return true;
            case "dataNascimento":
                LocalDate d = parseData(v);
                if (d == null) return false;
                if (soSeVazio && p.getDataNascimento() != null) return false;
                p.setDataNascimento(d);
                return true;
            case "rg":
                if (soSeVazio && preenchido(p.getRg())) return false;
                p.setRg(trunc(v, tam));
                return true;
            case "cpf":
                String cpf = digitos(v);
                if (cpf.length() != 11) return false;
                if (soSeVazio && preenchido(p.getCpf())) return false;
                p.setCpf(cpf);
                return true;
            case "nomeMae":
                if (soSeVazio && preenchido(p.getNomeMae())) return false;
                p.setNomeMae(trunc(v, tam));
                return true;
            case "nomePai":
                if (soSeVazio && preenchido(p.getNomePai())) return false;
                p.setNomePai(trunc(v, tam));
                return true;
            case "rua":
                if (soSeVazio && preenchido(p.getRua())) return false;
                p.setRua(trunc(v, tam));
                return true;
            case "numero":
                if (soSeVazio && preenchido(p.getNumero())) return false;
                p.setNumero(trunc(v, tam));
                return true;
            case "bairro":
                if (soSeVazio && preenchido(p.getBairro())) return false;
                p.setBairro(trunc(v, tam));
                return true;
            case "municipio":
                if (soSeVazio && preenchido(p.getMunicipio())) return false;
                p.setMunicipio(trunc(v, tam));
                return true;
            case "uf":
                if (soSeVazio && preenchido(p.getUf())) return false;
                p.setUf(trunc(v.toUpperCase(), tam));
                return true;
            case "cep":
                String cep = digitos(v);
                if (cep.isEmpty()) return false;
                if (soSeVazio && preenchido(p.getCep())) return false;
                p.setCep(trunc(cep, tam));
                return true;
            case "email":
                if (soSeVazio && preenchido(p.getEmail())) return false;
                p.setEmail(trunc(v, tam));
                return true;
            case "cns":
                String cns = digitos(v);
                if (cns.isEmpty()) return false;
                if (soSeVazio && preenchido(p.getCns())) return false;
                p.setCns(trunc(cns, tam));
                return true;
            case "prontuario":
                if (soSeVazio && preenchido(p.getProntuario())) return false;
                p.setProntuario(trunc(v, tam));
                return true;
            default:
                return false;
        }
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static boolean preenchido(String v) {
        return v != null && !v.isBlank();
    }

    private static String trunc(String v, int tam) {
        return v.length() > tam ? v.substring(0, tam) : v;
    }

    private static String digitos(String v) {
        return v.replaceAll("\\D", "");
    }

    /** SEXO do SIRESP (M/F/I) → enum Sexo do POP. Outros valores → null (não atualiza). */
    private static Sexo mapearSexo(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return switch (v.trim().toUpperCase().charAt(0)) {
            case 'M' -> Sexo.MASCULINO;
            case 'F' -> Sexo.FEMININO;
            case 'I' -> Sexo.OUTRO;
            default -> null;
        };
    }

    /** Data no formato ISO (yyyy-MM-dd). "0001-01-01" = não informada → null. Formato inválido → null. */
    private static LocalDate parseData(String v) {
        try {
            LocalDate d = LocalDate.parse(v.trim());
            return d.getYear() <= 1 ? null : d;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
