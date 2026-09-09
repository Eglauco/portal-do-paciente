import { Ionicons } from '@expo/vector-icons';
import { useMemo } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import { alpha, type Tema, useTema } from '@/hooks/use-tema';

interface Props {
  visivel: boolean;
  nome: string | null;
  onAbrir: () => void;
  onBaixar: () => void;
  onFechar: () => void;
}

export function DocumentoModal({ visivel, nome, onAbrir, onBaixar, onFechar }: Props) {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  return (
    <Modal visible={visivel} transparent animationType="fade" onRequestClose={onFechar}>
      <Pressable style={styles.backdrop} onPress={onFechar}>
        {/* Impede o toque no card de fechar o modal */}
        <Pressable style={styles.card} onPress={() => {}}>
          <View style={styles.icone}>
            <Ionicons name="document-text" size={26} color={t.brandDeep} />
          </View>

          <Text style={styles.titulo} numberOfLines={2}>
            {nome ?? 'Documento'}
          </Text>
          <Text style={styles.subtitulo}>O que deseja fazer com este documento?</Text>

          <Pressable
            style={({ pressed }) => [styles.btnAbrir, pressed && styles.pressed]}
            onPress={onAbrir}>
            <Ionicons name="eye-outline" size={20} color="#fff" />
            <Text style={styles.btnAbrirTxt}>Abrir</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [styles.btnBaixar, pressed && styles.btnBaixarPressed]}
            onPress={onBaixar}>
            <Ionicons name="download-outline" size={20} color={t.brandDeep} />
            <Text style={styles.btnBaixarTxt}>Baixar / Compartilhar</Text>
          </Pressable>

          <Pressable style={styles.btnCancelar} onPress={onFechar}>
            <Text style={styles.btnCancelarTxt}>Cancelar</Text>
          </Pressable>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: alpha(t.brandPine, 0.55),
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: t.surface,
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
  },
  icone: {
    width: 60,
    height: 60,
    borderRadius: 18,
    backgroundColor: t.brandTint,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
  },
  titulo: { fontSize: 18, fontWeight: '800', color: t.ink, textAlign: 'center', letterSpacing: -0.3 },
  subtitulo: {
    fontSize: 13.5,
    color: t.muted,
    textAlign: 'center',
    marginTop: 6,
    marginBottom: 20,
    lineHeight: 19,
  },
  btnAbrir: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 52,
    borderRadius: 16,
    backgroundColor: t.brand,
  },
  pressed: { opacity: 0.9 },
  btnAbrirTxt: { color: '#fff', fontSize: 15.5, fontWeight: '700' },
  btnBaixar: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 52,
    borderRadius: 16,
    marginTop: 10,
    borderWidth: 1,
    borderColor: t.brandTintStrong,
    backgroundColor: t.surface,
  },
  btnBaixarPressed: { backgroundColor: t.brandTint },
  btnBaixarTxt: { color: t.brandDeep, fontSize: 15, fontWeight: '700' },
  btnCancelar: {
    width: '100%',
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 6,
  },
  btnCancelarTxt: { color: t.muted, fontSize: 14.5, fontWeight: '600' },
});
