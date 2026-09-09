package com.example.pop.postagem;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.pop.configuracao.ChaveConfiguracao;
import com.example.pop.configuracao.ConfiguracaoService;
import com.example.pop.paciente.Paciente;
import com.example.pop.paciente.PacienteRepository;
import com.example.pop.paciente.Responsavel;
import com.example.pop.paciente.ResponsavelRepository;
import com.example.pop.usuario.Usuario;
import com.example.pop.usuario.UsuarioRepository;

/**
 * Resolve, em tempo de LEITURA, o nome exibido do autor de um comentário do feed a
 * partir dos ids (nunca de um campo "autor" gravado). Regra:
 * <ul>
 *   <li>{@code usuarioId} presente → comentário do back-office → "Administração" (padrão,
 *       config {@code OCULTAR_NOME_USUARIO_NA_REDESOCIAL} ligada) ou o nome completo do
 *       usuário que comentou (config desligada);</li>
 *   <li>{@code pacienteId} presente → nome do paciente, abreviado conforme a config
 *       {@code NOME_PACIENTE_RESPONSAVEL_ABREVIADO_NA_REDESOCIAL} ("M. D.");</li>
 *   <li>nenhum id (comentário antigo/indeterminado) → rótulo genérico "Paciente".</li>
 * </ul>
 * O nome do responsável (marcador "Comentado por X (responsável)") é resolvido pelo
 * {@code responsavelId} e também abreviado conforme a config de abreviação.
 */
@Service
public class AutorComentarioService {

    /** Rótulo institucional para comentários do back-office (não expõe o atendente). */
    static final String ROTULO_ADMIN = "Administração";
    /** Rótulo genérico quando não há id para resolver (comentário antigo/indeterminado). */
    private static final String ROTULO_PADRAO = "Paciente";

    private final PacienteRepository pacienteRepository;
    private final ResponsavelRepository responsavelRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracaoService configuracaoService;

    public AutorComentarioService(PacienteRepository pacienteRepository,
            ResponsavelRepository responsavelRepository, UsuarioRepository usuarioRepository,
            ConfiguracaoService configuracaoService) {
        this.pacienteRepository = pacienteRepository;
        this.responsavelRepository = responsavelRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracaoService = configuracaoService;
    }

    /**
     * Resolver em lote do nome de exibição (negrito) do autor, pelos ids. Pré-carrega os
     * nomes dos pacientes envolvidos em uma única consulta. A config de abreviação é lida
     * uma vez por chamada (mesmo valor para toda a resposta).
     */
    public Function<Comentario, String> autores(List<Comentario> comentarios) {
        Set<Long> ids = comentarios.stream().map(Comentario::getPacienteId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        // filtra nome null (Collectors.toMap não aceita valor null): paciente sem nome
        // some do mapa → autorDe cai no rótulo genérico "Paciente" em vez de estourar.
        Map<Long, String> nomesPaciente = ids.isEmpty()
                ? Map.of()
                : pacienteRepository.findAllById(ids).stream()
                        .filter(p -> p.getNome() != null)
                        .collect(Collectors.toMap(Paciente::getId, Paciente::getNome));
        boolean soIniciais = soIniciaisNoFeed();
        // Nome do usuário (back-office) só é buscado quando a config manda MOSTRAR; no padrão
        // (ocultar) nem consulta os usuários — é sempre "Administração".
        boolean ocultarUsuario = ocultarNomeUsuario();
        Map<Long, String> nomesUsuario = ocultarUsuario ? Map.of() : nomesUsuario(comentarios);
        return c -> autorDe(c, nomesPaciente, soIniciais, ocultarUsuario, nomesUsuario);
    }

    /** Nomes dos usuários (back-office) dos comentários, por id, em uma única consulta. */
    private Map<Long, String> nomesUsuario(List<Comentario> comentarios) {
        Set<Long> ids = comentarios.stream().map(Comentario::getUsuarioId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty()
                ? Map.of()
                : usuarioRepository.findAllById(ids).stream()
                        .filter(u -> u.getNome() != null)
                        .collect(Collectors.toMap(Usuario::getId, Usuario::getNome));
    }

    private String autorDe(Comentario c, Map<Long, String> nomesPaciente, boolean soIniciais,
            boolean ocultarUsuario, Map<Long, String> nomesUsuario) {
        if (c.getUsuarioId() != null) {
            if (ocultarUsuario) {
                return ROTULO_ADMIN;
            }
            // Mostrar o nome: sempre COMPLETO (o usuário/atendente fica fora da abreviação);
            // sem nome/usuário removido → volta ao rótulo institucional (seguro).
            String nome = nomesUsuario.get(c.getUsuarioId());
            return (nome == null || nome.isBlank()) ? ROTULO_ADMIN : nome.trim();
        }
        if (c.getPacienteId() != null) {
            String nome = nomesPaciente.get(c.getPacienteId());
            return (nome == null || nome.isBlank()) ? ROTULO_PADRAO : abreviar(nome, soIniciais);
        }
        return ROTULO_PADRAO;
    }

    /**
     * Nome de exibição do autor de um comentário recém-criado por um paciente — o nome já
     * está em mãos (evita reconsultar o banco). Aplica a abreviação conforme a config.
     */
    public String autorPaciente(String nomePaciente) {
        return (nomePaciente == null || nomePaciente.isBlank())
                ? ROTULO_PADRAO
                : abreviar(nomePaciente, soIniciaisNoFeed());
    }

    /**
     * Resolver em lote do nome (abreviado conforme a config) do responsável que comentou
     * pelo paciente, pelo {@code responsavelId}. Null quando foi o próprio paciente.
     */
    public Function<Long, String> nomesResponsavel(List<Comentario> comentarios) {
        Set<Long> ids = comentarios.stream().map(Comentario::getResponsavelId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return rid -> null;
        }
        boolean soIniciais = soIniciaisNoFeed();
        // Responsável sem nome (dado legado/manual) some do mapa → resolve para null (sem
        // marcador), em vez de estourar no split de um nome em branco na listagem PÚBLICA.
        Map<Long, String> nomes = responsavelRepository.findAllById(ids).stream()
                .filter(r -> r.getNome() != null && !r.getNome().isBlank())
                .collect(Collectors.toMap(Responsavel::getId, r -> abreviar(r.getNome(), soIniciais)));
        return rid -> rid == null ? null : nomes.get(rid);
    }

    /** Nome (abreviado) do responsável em mãos (recém-criado), ou null se não houver. */
    public String nomeResponsavel(Responsavel responsavel) {
        return responsavel == null ? null : abreviar(responsavel.getNome(), soIniciaisNoFeed());
    }

    /**
     * Abrevia um nome de pessoa para as iniciais ("Mariana Duarte" → "M. D.") quando a
     * config {@code NOME_PACIENTE_RESPONSAVEL_ABREVIADO_NA_REDESOCIAL} está ligada; caso contrário, nome completo.
     */
    private String abreviar(String nome, boolean soIniciais) {
        if (nome == null || nome.isBlank()) {
            return ""; // defensivo: nunca deixa um nome vazio estourar o split abaixo
        }
        String limpo = nome.trim();
        if (!soIniciais) {
            return limpo;
        }
        String[] partes = limpo.split("\\s+");
        String primeira = partes[0].substring(0, 1).toUpperCase();
        if (partes.length == 1) {
            return primeira + ".";
        }
        String ultima = partes[partes.length - 1].substring(0, 1).toUpperCase();
        return primeira + ". " + ultima + ".";
    }

    /**
     * Config {@code NOME_PACIENTE_RESPONSAVEL_ABREVIADO_NA_REDESOCIAL}: mostrar só as iniciais no feed. Fail-safe é
     * SIM (mais privado) se a config estiver ausente/indisponível — nunca derruba o feed
     * (caminho público) por causa de uma config quebrada.
     */
    private boolean soIniciaisNoFeed() {
        try {
            return configuracaoService.lerBooleano(ChaveConfiguracao.NOME_PACIENTE_RESPONSAVEL_ABREVIADO_NA_REDESOCIAL);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * Config {@code OCULTAR_NOME_USUARIO_NA_REDESOCIAL}: ligada → comentário do back-office
     * aparece como "Administração"; desligada → nome completo do usuário. Fail-safe é OCULTAR
     * (mais privado) se a config estiver ausente/indisponível — nunca derruba o feed público.
     */
    private boolean ocultarNomeUsuario() {
        try {
            return configuracaoService.lerBooleano(ChaveConfiguracao.OCULTAR_NOME_USUARIO_NA_REDESOCIAL);
        } catch (RuntimeException e) {
            return true;
        }
    }
}
