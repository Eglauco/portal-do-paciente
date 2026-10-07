package com.example.pop.siresp;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.example.pop.auth.Permissoes;
import com.example.pop.storage.StorageService;
import com.example.pop.unidade.Unidade;
import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;

/**
 * Importa o XML do SIRESP/CROSS (NewDataSet &gt; Mensagem) e popula a tabela {@code siresp}: UMA linha por
 * {@code <Mensagem>}, cada campo do XML na sua coluna (texto cru). Suporta vários {@code <Mensagem>} no mesmo
 * arquivo e insere TUDO (não deduplica — decisão de negócio fica para depois). Não gera agendamento.
 *
 * <p>O ARQUIVO ORIGINAL (bytes exatos do upload) é gravado no S3 (pasta {@code siresp/}) e sua URL fica em cada
 * linha do import — é o que reenviamos ao Sistema de Gestão no "Post XML" (sem divergência) e o que o botão de
 * download baixa. Se o S3 falhar, o import inteiro é cancelado (para nenhum registro ficar sem o original fiel).
 */
@Service
public class SirespImportacaoService {

    private final SirespRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final SirespConfigService configService;
    private final SirespPacienteAtualizador atualizador;
    private final StorageService storageService;

    public SirespImportacaoService(SirespRepository repository, UsuarioRepository usuarioRepository,
            SirespConfigService configService, SirespPacienteAtualizador atualizador,
            StorageService storageService) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.configService = configService;
        this.atualizador = atualizador;
        this.storageService = storageService;
    }

    /**
     * Resultado do import: Mensagens gravadas + pacientes atualizados/criados + a URL do arquivo original no S3 e
     * os ids das linhas gravadas (usados pela rotina de envio automático ao Sistema de Gestão, feita após o commit).
     */
    public record Resultado(int importados, String arquivo, int pacientesAtualizados, int pacientesCriados,
            String arquivoUrl, List<Long> ids) {
    }

    @Transactional
    public Resultado importar(byte[] conteudo, String arquivo, Long usuarioId) {
        if (conteudo == null || conteudo.length == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Arquivo XML vazio.");
        }
        List<Map<String, String>> mensagens = parse(conteudo);
        if (mensagens.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Nenhuma <Mensagem> encontrada no XML. Verifique se é um arquivo do SIRESP.");
        }

        // Guarda o ARQUIVO ORIGINAL (bytes exatos) no S3. Se falhar, cancela o import inteiro (tx desfaz tudo) —
        // nenhum registro deve ficar sem o original fiel para reenviar/baixar.
        String arquivoUrl;
        try {
            arquivoUrl = storageService.salvarBytes(conteudo, "application/xml", "siresp", arquivo);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Não foi possível guardar o arquivo original no armazenamento (S3). O import foi cancelado.");
        }

        // Metadados: unidade ativa + nome de quem importou (resolvidos no servidor pelo uid do token).
        Long unidadeId = null;
        String importadoPorNome = null;
        Usuario usuario = usuarioId == null ? null : usuarioRepository.findById(usuarioId).orElse(null);
        if (usuario != null) {
            importadoPorNome = usuario.getNome();
            Unidade ativa = Permissoes.unidadeAtivaEfetiva(usuario);
            unidadeId = ativa == null ? null : ativa.getId();
        }
        LocalDateTime agora = LocalDateTime.now();

        List<Siresp> linhas = new ArrayList<>();
        for (Map<String, String> m : mensagens) {
            Siresp s = new Siresp();
            s.setUnidadeSaudeId(unidadeId);
            s.setArquivo(arquivo);
            s.setArquivoUrl(arquivoUrl);
            s.setImportadoEm(agora);
            s.setImportadoPorUsuarioId(usuarioId);
            s.setImportadoPorNome(importadoPorNome);
            aplicar(s, m);
            // O log de integração (diagnóstico) e a eventual criação do agendamento são feitos logo após o commit,
            // registro a registro, por SirespAgendamentoService.processarRegistro (chamado pelo controller).
            linhas.add(s);
        }
        List<Long> ids = repository.saveAll(linhas).stream().map(Siresp::getId).toList();

        // Regra de negócio: casa o paciente por codigoIntegracao = COD_PACIENTE (e, se "criar" ligado, por CPF como
        // fallback). Se achar + "atualizar" ligado → atualiza campo a campo. Se não achar + "criar" ligado → cria o
        // cadastro completo. Audita (autor IMPORTACAO_SIRESP). Cada paciente em tx própria (REQUIRES_NEW): uma falha
        // (ex.: CPF duplicado) não derruba a importação nem os demais. Processa cada COD_PACIENTE 1x por arquivo.
        int atualizados = 0;
        int criados = 0;
        boolean atualizar = configService.habilitado();
        boolean criar = configService.criar();
        if (atualizar || criar) {
            Map<String, AcaoAtualizacao> acoes = configService.acoes();
            Set<String> processados = new HashSet<>();
            for (Map<String, String> m : mensagens) {
                String cod = m.get("COD_PACIENTE");
                if (cod == null || cod.isBlank() || !processados.add(cod.trim())) {
                    continue;
                }
                try {
                    switch (atualizador.processar(cod, m, acoes, atualizar, criar, unidadeId, usuarioId)) {
                        case ATUALIZADO -> atualizados++;
                        case CRIADO -> criados++;
                        default -> { }
                    }
                } catch (RuntimeException ignorado) {
                    // Falha isolada deste paciente (tx própria já desfez) — segue para os próximos.
                }
            }
        }
        return new Resultado(linhas.size(), arquivo, atualizados, criados, arquivoUrl, ids);
    }

    /** Lê o XML e devolve uma lista de mapas {NOME_DO_CAMPO → valor}, um por {@code <Mensagem>}. */
    private List<Map<String, String>> parse(byte[] conteudo) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // Proteção contra XXE (o XML vem de upload externo).
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(conteudo));
            doc.getDocumentElement().normalize();

            List<Map<String, String>> mensagens = new ArrayList<>();
            NodeList todas = doc.getElementsByTagName("Mensagem");
            for (int i = 0; i < todas.getLength(); i++) {
                Node no = todas.item(i);
                if (no.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Map<String, String> campos = new LinkedHashMap<>();
                NodeList filhos = no.getChildNodes();
                for (int j = 0; j < filhos.getLength(); j++) {
                    Node filho = filhos.item(j);
                    if (filho.getNodeType() == Node.ELEMENT_NODE) {
                        String nome = ((Element) filho).getTagName().toUpperCase();
                        String valor = filho.getTextContent();
                        campos.put(nome, valor == null || valor.isBlank() ? null : valor.trim());
                    }
                }
                mensagens.add(campos);
            }
            return mensagens;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Não foi possível ler o XML do SIRESP. Verifique o arquivo.");
        }
    }

    /** Atribui cada campo do XML à coluna correspondente (texto cru). Campos ausentes ficam nulos. */
    private void aplicar(Siresp s, Map<String, String> m) {
        s.setTipoRegistro(detectarTipo(m));
        s.setTipoMovimento(detectarMovimento(m, s.getTipoRegistro()));
        // Campos da CONSULTA (nulos quando o registro é EXAME).
        s.setTipoConsulta(m.get("TIPO_CONSULTA"));
        s.setCodUnidadeExecutante(m.get("COD_UNIDADE_EXECUTANTE"));
        s.setIdAgeConsultaHor(m.get("ID_AGE_CONSULTA_HOR"));
        s.setIdAgeConsulta(m.get("ID_AGE_CONSULTA"));
        s.setAgeConsultaNome(m.get("AGE_CONSULTA_NOME"));
        s.setIdEspecialidade(m.get("ID_ESPECIALIDADE"));
        s.setNomeEspecialidade(m.get("NOME_ESPECIALIDADE"));
        s.setCodDia(m.get("COD_DIA"));
        s.setDataAgenda(m.get("DATA_AGENDA"));
        s.setHorIni(m.get("HOR_INI"));
        s.setHorFim(m.get("HOR_FIM"));
        s.setTipo(m.get("TIPO"));
        s.setIdMotivo(m.get("ID_MOTIVO"));
        s.setIdProfissional(m.get("ID_PROFISSIONAL"));
        s.setDocProfissional(m.get("DOC_PROFISSIONAL"));
        s.setOrigem(m.get("ORIGEM"));
        s.setNomeProfissional(m.get("NOME_PROFISSIONAL"));
        s.setIdProtocolo(m.get("ID_PROTOCOLO"));
        s.setSubcateg(m.get("SUBCATEG"));
        s.setNomeProtocolo(m.get("NOME_PROTOCOLO"));
        s.setCodUnidadeSolicitante(m.get("COD_UNIDADE_SOLICITANTE"));
        s.setNomeUnidadeSolicitante(m.get("NOME_UNIDADE_SOLICITANTE"));
        s.setCnesUnidadeSolicitante(m.get("CNES_UNIDADE_SOLICITANTE"));
        s.setNomeUsuarioSolicitante(m.get("NOME_USUARIO_SOLICITANTE"));
        s.setDtUltimaAtualiz(m.get("DT_ULTIMA_ATUALIZ"));
        s.setCodPaciente(m.get("COD_PACIENTE"));
        s.setNomePaciente(m.get("NOME_PACIENTE"));
        s.setSexo(m.get("SEXO"));
        s.setDtNascimento(m.get("DT_NASCIMENTO"));
        s.setRg(m.get("RG"));
        s.setCpf(m.get("CPF"));
        s.setNomeMae(m.get("NOME_MAE"));
        s.setNomePai(m.get("NOME_PAI"));
        s.setEndereco(m.get("ENDERECO"));
        s.setEnderecoNumero(m.get("ENDERECO_NUMERO"));
        s.setBairro(m.get("BAIRRO"));
        s.setMunicipio(m.get("MUNICIPIO"));
        s.setUf(m.get("UF"));
        s.setCep(m.get("CEP"));
        s.setTelResDdd(m.get("TEL_RES_DDD"));
        s.setTelRes(m.get("TEL_RES"));
        s.setTelCelularDdd(m.get("TEL_CELULAR_DDD"));
        s.setTelCelular(m.get("TEL_CELULAR"));
        s.setTelComDdd(m.get("TEL_COM_DDD"));
        s.setTelCom(m.get("TEL_COM"));
        s.setTelComRamal(m.get("TEL_COM_RAMAL"));
        s.setEmail(m.get("EMAIL"));
        s.setContatoNome(m.get("CONTATO_NOME"));
        s.setContatoTelDdd(m.get("CONTATO_TEL_DDD"));
        s.setContatoTel(m.get("CONTATO_TEL"));
        s.setNumCns(m.get("NUM_CNS"));
        s.setNumProntuario(m.get("NUM_PRONTUARIO"));

        // Campos do EXAME (nulos quando o registro é CONSULTA).
        s.setTipoExame(m.get("TIPO_EXAME"));
        s.setIdAgeExameHor(m.get("ID_AGE_EXAME_HOR"));
        s.setIdAgeExame(m.get("ID_AGE_EXAME"));
        // Horário de origem da transferência (consulta/exame).
        s.setIdAgeConsultaHorOrigem(m.get("ID_AGE_CONSULTA_HOR_ORIGEM"));
        s.setIdAgeExameHorOrigem(m.get("ID_AGE_EXAME_HOR_ORIGEM"));
        s.setAgeExameNome(m.get("AGE_EXAME_NOME"));
        s.setIdAssociacao(m.get("ID_ASSOCIACAO"));
        s.setNomeAssociacao(m.get("NOME_ASSOCIACAO"));
        s.setIdExame(m.get("ID_EXAME"));
        s.setCodExame(m.get("COD_EXAME"));
        s.setNomeExame(m.get("NOME_EXAME"));
        s.setTipoTabela(m.get("TIPO_TABELA"));
    }

    /** Detecta o tipo pela estrutura do XML: elementos do exame presentes → EXAME; senão CONSULTA. */
    private static TipoRegistroSiresp detectarTipo(Map<String, String> m) {
        boolean exame = m.get("ID_AGE_EXAME") != null || m.get("ID_AGE_EXAME_HOR") != null
                || m.get("TIPO_EXAME") != null || m.get("ID_EXAME") != null;
        return exame ? TipoRegistroSiresp.EXAME : TipoRegistroSiresp.CONSULTA;
    }

    /** Movimentação pelo TIPO_CONSULTA/TIPO_EXAME: C=cancelamento, T=transferência, senão (A/vazio)=agendamento. */
    private static TipoMovimento detectarMovimento(Map<String, String> m, TipoRegistroSiresp tipo) {
        String mov = tipo == TipoRegistroSiresp.EXAME ? m.get("TIPO_EXAME") : m.get("TIPO_CONSULTA");
        if ("C".equalsIgnoreCase(mov)) {
            return TipoMovimento.CANCELAMENTO;
        }
        if ("T".equalsIgnoreCase(mov)) {
            return TipoMovimento.TRANSFERENCIA;
        }
        return TipoMovimento.AGENDAMENTO;
    }
}
