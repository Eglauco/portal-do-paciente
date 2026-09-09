import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { type Tema, useTema } from '@/hooks/use-tema';

/** Caixa de edição inline de um comentário — mantém o texto em estado próprio
 *  (não re-renderiza a lista a cada tecla). Usada na tela e na folha de comentários. */
export function EdicaoComentario({
  inicial,
  salvando,
  onSalvar,
  onCancelar,
}: {
  inicial: string;
  salvando: boolean;
  onSalvar: (texto: string) => void;
  onCancelar: () => void;
}) {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const [txt, setTxt] = useState(inicial);
  const bloqueado = salvando || !txt.trim();
  return (
    <View style={styles.editBox}>
      <TextInput
        style={styles.editInput}
        value={txt}
        onChangeText={setTxt}
        multiline
        autoFocus
        editable={!salvando}
        placeholder="Edite seu comentário"
        placeholderTextColor={t.muted}
      />
      <View style={styles.editAcoes}>
        <Pressable onPress={onCancelar} hitSlop={6} disabled={salvando}>
          <Text style={styles.acaoLink}>Cancelar</Text>
        </Pressable>
        <Pressable onPress={() => onSalvar(txt)} hitSlop={6} disabled={bloqueado}>
          <Text style={[styles.acaoSalvar, bloqueado && styles.acaoDesabilitada]}>
            {salvando ? 'Salvando…' : 'Salvar'}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    editBox: { marginTop: 2 },
    editInput: {
      borderWidth: 1,
      borderColor: t.line,
      borderRadius: 10,
      paddingHorizontal: 10,
      paddingVertical: 8,
      fontSize: 14,
      color: t.ink,
      backgroundColor: '#fff',
      minHeight: 38,
    },
    editAcoes: { flexDirection: 'row', justifyContent: 'flex-end', gap: 18, marginTop: 6 },
    acaoLink: { fontSize: 11.5, fontWeight: '700', color: t.brandDeep },
    acaoSalvar: { fontSize: 12.5, fontWeight: '800', color: t.brandDeep },
    acaoDesabilitada: { opacity: 0.5 },
  });
