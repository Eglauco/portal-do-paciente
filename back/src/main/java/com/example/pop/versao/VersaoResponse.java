package com.example.pop.versao;

/**
 * Versão do BACKEND: número (semver, ex. "1.0.0") + data do release em ISO (yyyy-MM-dd).
 * Contrato público de GET /versao. O front/app formatam a data para dd/MM/aaaa.
 *
 * <p>PRINCÍPIO: cada componente é dono só da SUA versão. O back expõe a dele aqui; o front e o
 * app leem a própria do próprio arquivo (não trafegam por este endpoint).
 */
public record VersaoResponse(String versao, String data) {
}
