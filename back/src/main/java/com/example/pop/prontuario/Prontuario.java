package com.example.pop.prontuario;

import java.util.ArrayList;
import java.util.List;

import com.example.pop.agendamento.Agendamento;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prontuario")
@Getter
@Setter
@NoArgsConstructor
public class Prontuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false)
    private Agendamento agendamento;

    /** Número do atendimento (único). */
    @Column(name = "numero_atendimento", nullable = false, unique = true, length = 40)
    private String numeroAtendimento;

    @OneToMany(mappedBy = "prontuario", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Documento> documentos = new ArrayList<>();

    /** Substitui a lista de documentos, mantendo o vínculo bidirecional. */
    public void substituirDocumentos(List<Documento> novos) {
        this.documentos.clear();
        for (Documento d : novos) {
            d.setProntuario(this);
            this.documentos.add(d);
        }
    }
}
