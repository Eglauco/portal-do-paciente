package com.example.pop.agendaimportacao;

/**
 * Uma linha da tabela "HORÁRIOS" do preview (a marcação de um paciente). {@code linha} é o número da
 * linha na planilha (1-based) para o cliente localizar e corrigir. O paciente é LOCALIZADO por
 * id/prontuário/código (nunca por nome); {@code paciente.resolvido} traz o nome encontrado. Toda marcação
 * entra como "Aguardando confirmação do paciente" (sem coluna de status na planilha).
 *
 * <p>Os campos {@code cpf}, {@code nome}, {@code nomeSocial}, {@code telefone} e {@code dataNascimento} são
 * os dados do paciente informados NA LINHA — validados aqui e gravados junto ao horário (não criam nem
 * alteram o cadastro do paciente). CPF/Nome/Telefone/Data de nascimento são obrigatórios; Nome social é
 * opcional (quando preenchido ⇒ "utiliza nome social" no registro importado).
 */
public record HorarioPreview(
        int linha,
        CampoPreview paciente,
        CampoPreview cpf,
        CampoPreview nome,
        CampoPreview nomeSocial,
        CampoPreview telefone,
        CampoPreview dataNascimento,
        CampoPreview horaInicio) {
}
