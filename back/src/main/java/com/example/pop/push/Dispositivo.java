package com.example.pop.push;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Dispositivo registrado para receber notificações push (Expo Push Token). */
@Entity
@Table(name = "dispositivo")
@Getter
@Setter
@NoArgsConstructor
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    /** Conta (telefone) dona do aparelho — base do push por conta. Null antes do login. */
    @Column(name = "conta_id")
    private Long contaId;

    /** Perfil ativo no registro. Legado: o push direcionado agora resolve pela conta. */
    @Column(name = "paciente_id")
    private Long pacienteId;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
