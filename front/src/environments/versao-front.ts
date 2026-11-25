/**
 * Versão do ADMIN (front) — número semver + data do release em ISO (yyyy-MM-dd).
 *
 * ESTE é o arquivo da versão do front: cada componente é dono só da SUA versão. Como ele entra
 * no BUILD do front, o que o admin exibe é a versão REAL que está no ar (impossível ficar
 * dessincronizado com o deploy).
 *
 * A cada correção/melhoria NO FRONT, atualize aqui:
 *   - correção (bugfix) -> +0.0.1 (patch)
 *   - melhoria (feature) -> +0.1.0 (minor)
 *   - quebra grande      -> +1.0.0 (major)
 * e ajuste a data para o dia do release.
 */
export const VERSAO_FRONT = {
  versao: '1.2.0',
  data: '2026-11-24',
} as const;
