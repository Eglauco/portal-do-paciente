/** Utilitários de exibição compartilhados pelas telas do Prontuário Médico. */

/** Iniciais (até 2 letras) para o avatar quando não há foto. */
export function iniciais(nome: string | null | undefined): string {
  const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return '?';
  const primeira = partes[0][0] ?? '';
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] ?? '' : '';
  return (primeira + ultima).toUpperCase();
}

/** Idade em anos a partir da data de nascimento ISO (yyyy-MM-dd); null se ausente/inválida. */
export function idadeAnos(dataNascimento: string | null | undefined): number | null {
  if (!dataNascimento) return null;
  const [ano, mes, dia] = dataNascimento.split('-').map(Number);
  if (!ano || !mes || !dia) return null;
  const hoje = new Date();
  let idade = hoje.getFullYear() - ano;
  const mesAtual = hoje.getMonth() + 1;
  const diaAtual = hoje.getDate();
  if (mesAtual < mes || (mesAtual === mes && diaAtual < dia)) idade--;
  return idade >= 0 && idade < 150 ? idade : null;
}

/** Rótulo curto de idade ("34 anos") ou "Idade não informada". */
export function idadeRotulo(dataNascimento: string | null | undefined): string {
  const idade = idadeAnos(dataNascimento);
  return idade == null ? 'Idade não informada' : `${idade} ${idade === 1 ? 'ano' : 'anos'}`;
}

/** Rótulo legível do sexo a partir da chave do enum. */
export function sexoRotulo(sexo: string | null | undefined): string | null {
  switch (sexo) {
    case 'MASCULINO':
      return 'Masculino';
    case 'FEMININO':
      return 'Feminino';
    case 'OUTRO':
      return 'Outro';
    default:
      return null;
  }
}

/** Formata CPF (só dígitos) como 000.000.000-00; devolve o original se não tiver 11 dígitos. */
export function formatarCpf(cpf: string | null | undefined): string {
  const d = (cpf ?? '').replace(/\D/g, '');
  if (d.length !== 11) return cpf ?? '';
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
}

/** Formata telefone brasileiro (10/11 dígitos) como (00) 00000-0000; devolve o original caso contrário. */
export function formatarTelefone(telefone: string | null | undefined): string {
  const d = (telefone ?? '').replace(/\D/g, '');
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return telefone ?? '';
}

/** Detecta o tipo de arquivo pela extensão da URL/nome (ignora query string). */
export function detectarTipoArquivo(referencia: string | null | undefined): 'pdf' | 'imagem' | 'outro' {
  const semQuery = (referencia ?? '').split('?')[0].toLowerCase();
  const ext = semQuery.slice(semQuery.lastIndexOf('.') + 1);
  if (ext === 'pdf') return 'pdf';
  if (['jpg', 'jpeg', 'png', 'webp', 'gif', 'bmp', 'avif'].includes(ext)) return 'imagem';
  return 'outro';
}
