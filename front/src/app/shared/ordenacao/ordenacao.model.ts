export type Direcao = 'asc' | 'desc';

export interface Ordenacao {
  readonly campo: string;
  readonly direcao: Direcao;
}

/**
 * Aplica um clique de ordenação num campo e devolve a NOVA lista de ordenações.
 * Ciclo por campo: asc → desc → (removido).
 *  - combinar=false (clique normal): ordena SÓ por este campo (substitui os demais);
 *    se ele já era o único, avança o ciclo (asc→desc→nenhuma).
 *  - combinar=true (Shift+clique): mantém os outros e avança o ciclo só deste campo.
 */
export function alternarOrdenacao(atual: readonly Ordenacao[], campo: string, combinar: boolean): Ordenacao[] {
  const existente = atual.find((o) => o.campo === campo);
  const proxima = (dir: Direcao | undefined): Direcao | null =>
    dir === undefined ? 'asc' : dir === 'asc' ? 'desc' : null;

  if (!combinar) {
    const soEste = existente !== undefined && atual.length === 1;
    const dir = soEste ? proxima(existente.direcao) : 'asc';
    return dir ? [{ campo, direcao: dir }] : [];
  }

  if (!existente) {
    return [...atual, { campo, direcao: 'asc' }];
  }
  const dir = proxima(existente.direcao);
  if (dir === null) {
    return atual.filter((o) => o.campo !== campo);
  }
  return atual.map((o) => (o.campo === campo ? { campo, direcao: dir } : o));
}

/**
 * Serializa para os parâmetros de query do backend: ["nome:asc", "prontuario:desc"].
 * O separador é ":" (NÃO vírgula): quando há uma única ordenação, o Spring binda o
 * `List<String>` de uma ocorrência só quebrando o valor na vírgula — o que descartaria
 * a direção. Com ":" o valor chega inteiro tanto no caso único quanto no múltiplo.
 */
export function ordenacoesParaParametros(ordenacoes: readonly Ordenacao[]): string[] {
  return ordenacoes.map((o) => `${o.campo}:${o.direcao}`);
}
