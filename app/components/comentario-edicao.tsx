import { useState } from 'react';
import { Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { Brand } from '@/constants/theme';

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
        placeholderTextColor={Brand.muted}
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

const styles = StyleSheet.create({
  editBox: { marginTop: 2 },
  editInput: {
    borderWidth: 1,
    borderColor: Brand.line,
    borderRadius: 10,
    paddingHorizontal: 10,
    paddingVertical: 8,
    fontSize: 14,
    color: Brand.ink,
    backgroundColor: '#fff',
    minHeight: 38,
  },
  editAcoes: { flexDirection: 'row', justifyContent: 'flex-end', gap: 18, marginTop: 6 },
  acaoLink: { fontSize: 11.5, fontWeight: '700', color: Brand.brandDeep },
  acaoSalvar: { fontSize: 12.5, fontWeight: '800', color: Brand.brandDeep },
  acaoDesabilitada: { opacity: 0.5 },
});
