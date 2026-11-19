/**
 * Regra da senha de acesso do paciente (espelha PacienteAcessoService.validarSenha no backend):
 * LIVRE — letras, números, símbolos e espaços — com no mínimo 8 e no máximo 64 caracteres.
 */
export const SENHA_MIN = 8;
export const SENHA_MAX = 64;

/** Nº de caracteres como o backend conta (code points: um emoji vale 1). */
export function tamanhoSenha(senha: string): number {
  return Array.from(senha).length;
}

/** Mensagem de erro da senha nova, ou null quando ela é válida. */
export function erroSenhaNova(senha: string): string | null {
  const n = tamanhoSenha(senha);
  if (senha.trim().length === 0 || n < SENHA_MIN) return `A senha deve ter no mínimo ${SENHA_MIN} caracteres.`;
  if (n > SENHA_MAX) return `A senha deve ter no máximo ${SENHA_MAX} caracteres.`;
  return null;
}
