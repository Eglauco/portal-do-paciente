import { Ionicons } from '@expo/vector-icons';
import { StyleSheet, Text, View } from 'react-native';

import { Brand } from '@/constants/theme';

/** Aviso de "sem acesso" para uma funcionalidade que o responsável não pode ver. */
export function SemAcesso({ mensagem }: { mensagem?: string }) {
  return (
    <View style={styles.wrap}>
      <View style={styles.icone}>
        <Ionicons name="lock-closed-outline" size={26} color={Brand.muted} />
      </View>
      <Text style={styles.titulo}>Sem acesso</Text>
      <Text style={styles.txt}>
        {mensagem ?? 'O responsável não tem acesso a esta funcionalidade para este perfil.'}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, padding: 28 },
  icone: {
    width: 56,
    height: 56,
    borderRadius: 18,
    backgroundColor: Brand.surface,
    borderWidth: 1,
    borderColor: Brand.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  titulo: { fontSize: 16, fontWeight: '800', color: Brand.ink, marginTop: 2 },
  txt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center', lineHeight: 19 },
});
