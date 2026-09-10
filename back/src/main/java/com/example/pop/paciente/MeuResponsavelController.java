package com.example.pop.paciente;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

/**
 * O próprio paciente gerencia, pelo app, os responsáveis que ele autoriza — SEMPRE com o
 * escopo TRAVADO em AGENDAMENTOS (visualizar e lançar). Só na sessão do perfil PRÓPRIO:
 * um responsável agindo por outro paciente não pode adicionar responsáveis a ele.
 *
 * <p>Mesma regra do back-office: quem já tem lançamentos NÃO pode ser excluído (só
 * inativado/reativado, preservando o histórico); sem lançamentos, pode ser excluído.
 */
@RestController
@RequestMapping("/meu/responsaveis")
public class MeuResponsavelController {

    private final PacienteAcessoService acessoService;
    private final ResponsavelRepository responsavelRepository;
    private final ResponsavelLancamentoService lancamentoService;
    private final PacienteLogService pacienteLogService;

    public MeuResponsavelController(PacienteAcessoService acessoService,
            ResponsavelRepository responsavelRepository, ResponsavelLancamentoService lancamentoService,
            PacienteLogService pacienteLogService) {
        this.acessoService = acessoService;
        this.responsavelRepository = responsavelRepository;
        this.lancamentoService = lancamentoService;
        this.pacienteLogService = pacienteLogService;
    }

    /**
     * Responsáveis adicionados pelo próprio paciente (origem PACIENTE), ATIVOS e INATIVOS —
     * os inativos aparecem para poder reativar. Ativos primeiro; cada um traz {@code podeExcluir}.
     */
    @GetMapping
    public List<MeuResponsavelResponse> listar(@AuthenticationPrincipal Jwt jwt) {
        Paciente paciente = perfilProprio(jwt);
        return responsavelRepository.findByPaciente_Id(paciente.getId()).stream()
                .filter(r -> r.getOrigem() == OrigemResponsavel.PACIENTE)
                .sorted(Comparator.comparing((Responsavel r) -> !r.isAtivo()).thenComparing(Responsavel::getId))
                .map(this::paraResposta)
                .toList();
    }

    /** Adiciona um responsável (nome + telefone) com acesso só a AGENDAMENTOS (visualizar e lançar). */
    @PostMapping
    @Transactional
    public ResponseEntity<MeuResponsavelResponse> adicionar(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MeuResponsavelRequest request) {
        Paciente paciente = perfilProprio(jwt);
        String nome = request.nome().trim();
        String telefone = Documentos.somenteDigitos(request.telefone());
        if (telefone == null || telefone.length() < 10) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Informe um telefone válido com DDD.");
        }
        // O responsável tem de ser outra pessoa: não pode ter nenhum telefone do paciente.
        if (ResponsavelTelefones.ehDoPaciente(paciente, telefone)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "O responsável precisa ter um telefone diferente do seu.");
        }
        // Sem telefone repetido na lista deste paciente (ativos ou inativos): evita duplicados.
        if (responsavelRepository.findFirstByPaciente_IdAndTelefoneOrderByIdAsc(paciente.getId(), telefone).isPresent()) {
            // Pode ser um inativo (é só reativar) ou um cadastrado pela unidade (não aparece aqui).
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um responsável com este telefone neste cadastro. "
                            + "Se ele não aparece na lista, fale com a sua unidade de saúde.");
        }
        Responsavel r = new Responsavel();
        r.setPaciente(paciente);
        r.setNome(nome);
        r.setTelefone(telefone);
        r.setAtivo(true);
        r.setOrigem(OrigemResponsavel.PACIENTE);
        // Escopo TRAVADO no servidor: só AGENDAMENTOS, visualizar e lançar (o corpo não escolhe permissão).
        r.getPermissoes().put(FuncionalidadeApp.AGENDAMENTOS, NivelAcessoResponsavel.VISUALIZAR_LANCAR);
        // Mantém o agregado consistente: o Paciente é o dono da coleção (cascade + orphanRemoval),
        // então o vínculo tem de passar pela coleção — senão o remover depois não apaga a linha.
        paciente.getResponsaveis().add(r);
        Responsavel salvo = responsavelRepository.save(r);
        // Auditoria (LGPD): o próprio paciente autorizou uma pessoa pelo app.
        pacienteLogService.registrarResponsavelAdicionadoPeloPaciente(paciente, salvo);
        return ResponseEntity.status(HttpStatus.CREATED).body(paraResposta(salvo));
    }

    /** Inativa (ativo=false) ou reativa (ativo=true) uma pessoa autorizada — preserva o histórico. */
    @PatchMapping("/{id}")
    @Transactional
    public MeuResponsavelResponse definirSituacao(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody MeuResponsavelSituacaoRequest request) {
        Paciente paciente = perfilProprio(jwt);
        Responsavel r = carregarDoPaciente(paciente, id);
        boolean novoAtivo = request.ativo();
        if (r.isAtivo() != novoAtivo) { // só muda (e audita) quando de fato houve transição
            r.setAtivo(novoAtivo);
            pacienteLogService.registrarResponsavelSituacaoPeloPaciente(paciente, r, novoAtivo);
        }
        return paraResposta(r);
    }

    /** Exclui de vez uma pessoa autorizada SEM lançamentos; com lançamentos, bloqueia (409). */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> remover(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Paciente paciente = perfilProprio(jwt);
        Responsavel r = carregarDoPaciente(paciente, id);
        // Mesma regra do back-office: com lançamentos, não exclui — só inativa (preserva a autoria).
        if (lancamentoService.temLancamentos(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este responsável já tem atividade no sistema. Inative-o em vez de excluir.");
        }
        // O Paciente é o dono da coleção (orphanRemoval); apagar direto no repositório é desfeito
        // no flush enquanto o agregado ainda referencia o filho. Remover pela coleção.
        paciente.getResponsaveis().removeIf(x -> x.getId().equals(id));
        // Auditoria (LGPD): lê os dados de r ANTES do flush, então o texto sobrevive à remoção.
        pacienteLogService.registrarResponsavelRemovidoPeloPaciente(paciente, r);
        return ResponseEntity.noContent().build();
    }

    /** Carrega o responsável e garante que é origem PACIENTE e deste paciente (senão 404/403). */
    private Responsavel carregarDoPaciente(Paciente paciente, Long id) {
        Responsavel r = responsavelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável não encontrado"));
        if (r.getOrigem() != OrigemResponsavel.PACIENTE || r.getPaciente() == null
                || !r.getPaciente().getId().equals(paciente.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você não pode gerenciar este responsável.");
        }
        return r;
    }

    /** Resposta com {@code podeExcluir} = sem lançamentos (aí cabe excluir; senão, só inativar/reativar). */
    private MeuResponsavelResponse paraResposta(Responsavel r) {
        return MeuResponsavelResponse.from(r, !lancamentoService.temLancamentos(r.getId()));
    }

    /** Garante que a sessão é o perfil PRÓPRIO (não um responsável agindo por outro paciente). */
    private Paciente perfilProprio(Jwt jwt) {
        if (acessoService.responsavelDaSessao(jwt).isPresent()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas o próprio paciente pode gerenciar seus responsáveis.");
        }
        return acessoService.pacienteDoToken(jwt);
    }
}
