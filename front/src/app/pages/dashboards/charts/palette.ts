/** Paleta estável para fatias/rankings (indexada; a ordem das fatias é fixa no backend). */
export const PALETA = ['#159a8a', '#3f8cff', '#f0a824', '#e0524d', '#7b61ff', '#12b76a', '#8a9b96', '#e879c9'];

export function cor(i: number): string {
  return PALETA[((i % PALETA.length) + PALETA.length) % PALETA.length];
}
