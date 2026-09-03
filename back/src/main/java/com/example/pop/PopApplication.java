package com.example.pop;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PopApplication {

	public static void main(String[] args) {
		// Fixa o fuso da JVM em horário do Brasil ANTES de subir o Spring/Flyway.
		// O sistema guarda datas como LocalDateTime "hora local do Brasil" (ex.:
		// Agendamento.dataHora) e usa LocalDateTime.now() em vários criado_em/
		// atualizado_em (chat, SAU, notificações). Em produção a imagem base roda
		// em UTC; sem isso esses horários sairiam 3h adiantados e o app — que
		// interpreta a data como hora local — mostraria horas e agrupamentos por
		// dia errados. No dev (Windows BR) já é o padrão; aqui garante o mesmo em
		// produção.
		TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
		SpringApplication.run(PopApplication.class, args);
	}

}
