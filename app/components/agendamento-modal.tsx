import { Ionicons } from '@expo/vector-icons';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import { Agendamento } from '@/constants/agendamentos';
import { Brand } from '@/constants/theme';

interface Props {
  visivel: boolean;
  agendamento: Agendamento | null;
  onConfirmar: () => void;
  onCancelar: () => void;
  onFechar: () => void;
}

export function AgendamentoModal({ visivel, agendamento, onConfirmar, onCancelar, onFechar }: Props) {
  return (
    <Modal visible={visivel} transparent animationType="fade" onRequestClose={onFechar}>
      <View style={styles.backdrop}>
        <View style={styles.card}>
          <Pressable
            style={styles.fechar}
            onPress={onFechar}
            accessibilityRole="button"
            accessibilityLabel="Fechar">
            <Ionicons name="close" size={20} color={Brand.muted} />
          </Pressable>

          <View style={styles.icone}>
            <Ionicons name="calendar" size={26} color={Brand.brandDeep} />
          </View>

          <Text style={styles.titulo}>Confirmar agendamento</Text>
          <Text style={styles.subtitulo}>
            Você tem um agendamento aguardando confirmação. Confirme para garantir seu horário.
          </Text>

          {agendamento && (
            <View style={styles.detalhe}>
              <Text style={styles.detEspecialidade}>{agendamento.especialidade}</Text>
              <View style={styles.detLinha}>
                <Ionicons name="person-outline" size={15} color={Brand.muted} />
                <Text style={styles.detTxt}>{agendamento.profissional}</Text>
              </View>
              <View style={styles.detLinha}>
                <Ionicons name="calendar-outline" size={15} color={Brand.muted} />
                <Text style={styles.detTxt}>
                  {agendamento.dia} {agendamento.mes} · {agendamento.hora}
                </Text>
              </View>
              <View style={styles.detLinha}>
                <Ionicons name="location-outline" size={15} color={Brand.muted} />
                <Text style={styles.detTxt}>{agendamento.unidade}</Text>
              </View>
            </View>
          )}

          <Pressable
            style={({ pressed }) => [styles.btnConfirmar, pressed && styles.pressed]}
            onPress={onConfirmar}>
            <Ionicons name="checkmark-circle" size={20} color="#fff" />
            <Text style={styles.btnConfirmarTxt}>Confirmar agendamento</Text>
          </Pressable>

          <Pressable
            style={({ pressed }) => [styles.btnCancelar, pressed && styles.btnCancelarPressed]}
            onPress={onCancelar}>
            <Text style={styles.btnCancelarTxt}>Cancelar agendamento</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(7,46,43,0.55)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    maxWidth: 380,
    backgroundColor: Brand.surface,
    borderRadius: 24,
    padding: 24,
    alignItems: 'center',
  },
  fechar: {
    position: 'absolute',
    top: 14,
    right: 14,
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.bg,
  },
  icone: {
    width: 60,
    height: 60,
    borderRadius: 18,
    backgroundColor: '#E7F3EF',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
    marginTop: 6,
  },
  titulo: { fontSize: 20, fontWeight: '800', color: Brand.ink, letterSpacing: -0.3 },
  subtitulo: {
    fontSize: 13.5,
    color: Brand.muted,
    textAlign: 'center',
    marginTop: 6,
    marginBottom: 18,
    lineHeight: 19,
  },
  detalhe: {
    width: '100%',
    backgroundColor: Brand.bg,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 16,
    marginBottom: 20,
    gap: 8,
  },
  detEspecialidade: { fontSize: 16, fontWeight: '800', color: Brand.ink, marginBottom: 2 },
  detLinha: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  detTxt: { fontSize: 13.5, color: '#40514C' },
  btnConfirmar: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 52,
    borderRadius: 16,
    backgroundColor: Brand.brand,
  },
  pressed: { opacity: 0.9 },
  btnConfirmarTxt: { color: '#fff', fontSize: 15.5, fontWeight: '700' },
  btnCancelar: {
    width: '100%',
    height: 50,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 16,
    marginTop: 10,
    borderWidth: 1,
    borderColor: '#F3D6DB',
    backgroundColor: Brand.surface,
  },
  btnCancelarPressed: { backgroundColor: '#FDF2F3' },
  btnCancelarTxt: { color: '#B23B4E', fontSize: 15, fontWeight: '700' },
});
