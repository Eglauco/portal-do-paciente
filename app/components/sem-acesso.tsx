import { Ionicons } from '@expo/vector-icons';
import { useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { type Tema, useTema } from '@/hooks/use-tema';

/** Aviso de "sem acesso" para uma funcionalidade que o responsável não pode ver. */
export function SemAcesso({ mensagem }: { mensagem?: string }) {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  return (
    <View style={styles.wrap}>
      <View style={styles.icone}>
        <Ionicons name="lock-closed-outline" size={26} color={t.muted} />
      </View>
      <Text style={styles.titulo}>Sem acesso</Text>
      <Text style={styles.txt}>
        {mensagem ?? 'O responsável não tem acesso a esta funcionalidade para este perfil.'}
      </Text>
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
  wrap: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, padding: 28 },
  icone: {
    width: 56,
    height: 56,
    borderRadius: 18,
    backgroundColor: t.surface,
    borderWidth: 1,
    borderColor: t.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  titulo: { fontSize: 16, fontWeight: '800', color: t.ink, marginTop: 2 },
  txt: { fontSize: 13.5, color: t.muted, textAlign: 'center', lineHeight: 19 },
});
