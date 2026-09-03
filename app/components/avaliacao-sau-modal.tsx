import { Ionicons } from '@expo/vector-icons';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { Brand } from '@/constants/theme';

interface Props {
  visivel: boolean;
  processando?: boolean;
  erro?: string | null;
  onEnviar: (nota: number, comentario: string) => void;
  onFechar: () => void;
}

const ESTRELAS = [1, 2, 3, 4, 5];
const COR_ESTRELA = '#F2A900';

/**
 * Avaliação do atendimento do SAU ao encerrar a conversa: 1 nota (1-5) obrigatória
 * + comentário opcional. Segue o padrão de modal do app (statusBarTranslucent +
 * KeyboardAvoidingView) para o teclado não cobrir o campo no Android edge-to-edge.
 */
export function AvaliacaoSauModal({ visivel, processando = false, erro, onEnviar, onFechar }: Props) {
  const [nota, setNota] = useState(0);
  const [comentario, setComentario] = useState('');

  // Reinicia sempre que abre.
  useEffect(() => {
    if (visivel) {
      setNota(0);
      setComentario('');
    }
  }, [visivel]);

  const podeEnviar = nota >= 1 && nota <= 5 && !processando;

  return (
    <Modal
      visible={visivel}
      transparent
      animationType="fade"
      statusBarTranslucent
      // Não deixa o voltar do Android fechar no meio do envio (perderia o erro/estado).
      onRequestClose={() => {
        if (!processando) onFechar();
      }}>
      <KeyboardAvoidingView style={styles.backdrop} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <View style={styles.card}>
          <Pressable
            style={styles.fechar}
            onPress={onFechar}
            disabled={processando}
            accessibilityRole="button"
            accessibilityLabel="Fechar">
            <Ionicons name="close" size={20} color={Brand.muted} />
          </Pressable>

          <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled" showsVerticalScrollIndicator={false}>
            <View style={styles.icone}>
              <Ionicons name="star" size={24} color={Brand.brandDeep} />
            </View>
            <Text style={styles.titulo}>Encerrar e avaliar</Text>
            <Text style={styles.subtitulo}>Como foi o atendimento do SAU? Dê uma nota de 1 a 5 estrelas para encerrar.</Text>

            <View style={styles.estrelas}>
              {ESTRELAS.map((n) => {
                const preenchida = nota >= n;
                return (
                  <Pressable
                    key={n}
                    onPress={() => setNota(n)}
                    disabled={processando}
                    style={styles.estrelaBtn}
                    accessibilityRole="button"
                    accessibilityLabel={`${n} de 5 estrelas`}
                    accessibilityState={{ selected: nota === n }}>
                    <Ionicons name={preenchida ? 'star' : 'star-outline'} size={40} color={preenchida ? COR_ESTRELA : '#CBD6D1'} />
                  </Pressable>
                );
              })}
            </View>

            <Text style={styles.rotulo}>Comentário (opcional)</Text>
            <TextInput
              style={styles.input}
              value={comentario}
              onChangeText={setComentario}
              placeholder="Conte como foi o atendimento…"
              placeholderTextColor="#9AAAA5"
              multiline
              textAlignVertical="top"
              maxLength={500}
              editable={!processando}
            />
          </ScrollView>

          {!!erro && (
            <View style={styles.erroBox}>
              <Ionicons name="alert-circle" size={16} color="#B23B4E" />
              <Text style={styles.erroTxt}>{erro}</Text>
            </View>
          )}

          <Pressable
            style={[styles.enviar, !podeEnviar && styles.enviarDesativado]}
            onPress={() => podeEnviar && onEnviar(nota, comentario)}
            disabled={!podeEnviar}>
            {processando ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <>
                <Ionicons name="checkmark-circle" size={20} color="#fff" />
                <Text style={styles.enviarTxt}>Encerrar conversa</Text>
              </>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(7,46,43,0.55)', alignItems: 'center', justifyContent: 'center', padding: 24 },
  card: { width: '100%', maxWidth: 400, maxHeight: '88%', backgroundColor: Brand.surface, borderRadius: 24, padding: 22 },
  fechar: {
    position: 'absolute', top: 14, right: 14, width: 34, height: 34, borderRadius: 17,
    alignItems: 'center', justifyContent: 'center', backgroundColor: Brand.bg, zIndex: 2,
  },
  scroll: { alignItems: 'center', paddingBottom: 4 },
  icone: {
    width: 56, height: 56, borderRadius: 18, backgroundColor: '#E7F3EF',
    alignItems: 'center', justifyContent: 'center', marginBottom: 12, marginTop: 4,
  },
  titulo: { fontSize: 19, fontWeight: '800', color: Brand.ink, letterSpacing: -0.3 },
  subtitulo: { fontSize: 13, color: Brand.muted, textAlign: 'center', marginTop: 6, marginBottom: 12, lineHeight: 18 },
  estrelas: { flexDirection: 'row', width: '100%', marginTop: 2, marginBottom: 6 },
  estrelaBtn: { flex: 1, alignItems: 'center', paddingVertical: 4 },
  rotulo: { alignSelf: 'flex-start', fontSize: 12.5, fontWeight: '700', color: Brand.muted, marginTop: 8, marginBottom: 8 },
  input: {
    width: '100%', minHeight: 76, maxHeight: 130, backgroundColor: Brand.bg, borderRadius: 14,
    borderWidth: 1, borderColor: Brand.line, padding: 12, fontSize: 14.5, color: Brand.ink, textAlignVertical: 'top',
  },
  enviar: {
    width: '100%', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8,
    height: 52, borderRadius: 16, marginTop: 16, backgroundColor: Brand.brand,
  },
  enviarDesativado: { opacity: 0.5 },
  enviarTxt: { color: '#fff', fontSize: 15.5, fontWeight: '700' },
  erroBox: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 12 },
  erroTxt: { flex: 1, fontSize: 13, color: '#B23B4E' },
});
