import { useMemo } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { OpcoesMapa } from '@/components/opcoes-mapa';
import { alpha, type Tema, useTema } from '@/hooks/use-tema';

interface Props {
  visivel: boolean;
  endereco: string;
  latitude?: number | null;
  longitude?: number | null;
  onFechar: () => void;
}

/**
 * Bottom-sheet "Como chegar": pergunta em qual app abrir a rota (Google Maps / Waze / Apple Maps).
 * Para uso em TELAS (rota de tela cheia) — é o único <Modal> em cena. Dentro de outro Modal (ex.: o
 * modal de agendamento), use {@link OpcoesMapa} inline para não empilhar modais (ver memória iOS).
 */
export function EscolherMapaSheet({ visivel, endereco, latitude, longitude, onFechar }: Props) {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const insets = useSafeAreaInsets();

  return (
    <Modal visible={visivel} transparent animationType="slide" statusBarTranslucent onRequestClose={onFechar}>
      <View style={styles.overlay}>
        <Pressable
          style={styles.fundo}
          onPress={onFechar}
          accessibilityRole="button"
          accessibilityLabel="Fechar"
        />
        <View style={[styles.sheet, { paddingBottom: insets.bottom + 12 }]}>
          <View style={styles.puxador} />
          <Text style={styles.titulo}>Como chegar</Text>
          <Text style={styles.sub}>Escolha em qual app abrir a rota</Text>
          <View style={styles.lista}>
            <OpcoesMapa endereco={endereco} latitude={latitude} longitude={longitude} onEscolher={onFechar} />
          </View>
          <Pressable
            style={({ pressed }) => [styles.cancelar, pressed && styles.cancelarPressed]}
            onPress={onFechar}
            accessibilityRole="button"
            accessibilityLabel="Cancelar">
            <Text style={styles.cancelarTxt}>Cancelar</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    overlay: { flex: 1, justifyContent: 'flex-end', backgroundColor: alpha(t.brandPine, 0.45) },
    fundo: { flex: 1 },
    sheet: {
      backgroundColor: t.surface,
      borderTopLeftRadius: 22,
      borderTopRightRadius: 22,
      paddingTop: 8,
      paddingHorizontal: 16,
    },
    puxador: {
      width: 40,
      height: 4,
      borderRadius: 2,
      backgroundColor: '#D4DEDA',
      alignSelf: 'center',
      marginBottom: 10,
    },
    titulo: { fontSize: 19, fontWeight: '800', color: t.ink, letterSpacing: -0.3 },
    sub: { fontSize: 13.5, color: t.muted, marginTop: 2, marginBottom: 10 },
    lista: { gap: 2 },
    cancelar: {
      marginTop: 10,
      paddingVertical: 14,
      alignItems: 'center',
      borderRadius: 14,
      backgroundColor: t.bg,
    },
    cancelarPressed: { opacity: 0.7 },
    cancelarTxt: { fontSize: 15, fontWeight: '700', color: t.ink },
  });
