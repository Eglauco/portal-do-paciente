import { Ionicons } from '@expo/vector-icons';
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import { Agendamento } from '@/constants/agendamentos';
import { Brand } from '@/constants/theme';

interface Props {
  visivel: boolean;
  /** 'confirmar' (agendamento aguardando) ou 'cancelar' (agendamento confirmado). */
  modo?: 'confirmar' | 'cancelar';
  agendamento: Agendamento | null;
  processando?: boolean;
  /** false quando o prazo de cancelamento já passou (esconde o botão e mostra aviso). */
  podeCancelar?: boolean;
  onConfirmar: () => void;
  onCancelar: () => void;
  onFechar: () => void;
}

export function AgendamentoModal({
  visivel,
  modo = 'confirmar',
  agendamento,
  processando = false,
  podeCancelar = true,
  onConfirmar,
  onCancelar,
  onFechar,
}: Props) {
  const cancelarModo = modo === 'cancelar';
  return (
    <Modal visible={visivel} transparent animationType="fade" onRequestClose={onFechar}>
      <View style={styles.backdrop}>
        <View style={styles.card}>
          <Pressable
            style={styles.fechar}
            onPress={onFechar}
            disabled={processando}
            accessibilityRole="button"
            accessibilityLabel="Fechar">
            <Ionicons name="close" size={20} color={Brand.muted} />
          </Pressable>

          <View style={styles.icone}>
            <Ionicons
              name={cancelarModo ? 'close-circle-outline' : 'calendar'}
              size={26}
              color={Brand.brandDeep}
            />
          </View>

          <Text style={styles.titulo}>{cancelarModo ? 'Cancelar agendamento' : 'Confirmar agendamento'}</Text>
          <Text style={styles.subtitulo}>
            {cancelarModo
              ? 'Você pode cancelar este agendamento direto por aqui, sem precisar entrar em contato com a unidade.'
              : 'Você tem um agendamento aguardando confirmação. Confirme para garantir seu horário.'}
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

          {cancelarModo ? (
            podeCancelar ? (
              <Pressable
                style={({ pressed }) => [styles.btnCancelarPrimario, (pressed || processando) && styles.pressed]}
                onPress={onCancelar}
                disabled={processando}>
                {processando ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <>
                    <Ionicons name="close-circle" size={20} color="#fff" />
                    <Text style={styles.btnCancelarPrimarioTxt}>Cancelar agendamento</Text>
                  </>
                )}
              </Pressable>
            ) : (
              <View style={styles.semCancelamento}>
                <Ionicons name="lock-closed-outline" size={15} color={Brand.muted} />
                <Text style={styles.semCancelamentoTxt}>Este agendamento não pode mais ser cancelado.</Text>
              </View>
            )
          ) : (
            <Pressable
              style={({ pressed }) => [styles.btnConfirmar, (pressed || processando) && styles.pressed]}
              onPress={onConfirmar}
              disabled={processando}>
              {processando ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <>
                  <Ionicons name="checkmark-circle" size={20} color="#fff" />
                  <Text style={styles.btnConfirmarTxt}>Confirmar agendamento</Text>
                </>
              )}
            </Pressable>
          )}
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
  btnCancelarPrimario: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 52,
    borderRadius: 16,
    backgroundColor: '#B23B4E',
  },
  btnCancelarPrimarioTxt: { color: '#fff', fontSize: 15.5, fontWeight: '700' },
  semCancelamento: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
    marginTop: 10,
    paddingVertical: 12,
    paddingHorizontal: 14,
    borderRadius: 14,
    backgroundColor: Brand.bg,
  },
  semCancelamentoTxt: { flex: 1, color: Brand.muted, fontSize: 13, fontWeight: '600' },
});
