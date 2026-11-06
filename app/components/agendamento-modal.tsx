import { Ionicons } from '@expo/vector-icons';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import { OpcoesMapa } from '@/components/opcoes-mapa';
import { Agendamento } from '@/constants/agendamentos';
import { alpha, type Tema, useTema } from '@/hooks/use-tema';
import { buscarPerfilUnidade } from '@/services/unidade';

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
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const cancelarModo = modo === 'cancelar';

  // Endereço da unidade do agendamento (para o botão "Como chegar"). Reaproveita o perfil da
  // unidade do chat (/meu/unidades/{id}); o botão só aparece quando a unidade tem endereço.
  const [local, setLocal] = useState<{
    endereco: string;
    latitude?: number | null;
    longitude?: number | null;
  } | null>(null);
  const [escolhendoMapa, setEscolhendoMapa] = useState(false);
  useEffect(() => {
    let ativo = true;
    setLocal(null);
    setEscolhendoMapa(false);
    const unidadeId = agendamento?.unidadeId;
    if (!visivel || !unidadeId) return;
    buscarPerfilUnidade(unidadeId)
      .then((u) => {
        if (ativo && u.endereco) {
          setLocal({ endereco: u.endereco, latitude: u.latitude, longitude: u.longitude });
        }
      })
      .catch(() => ativo && setLocal(null));
    return () => {
      ativo = false;
    };
  }, [visivel, agendamento?.unidadeId]);

  return (
    <Modal
      visible={visivel}
      transparent
      animationType="fade"
      onRequestClose={() => (escolhendoMapa ? setEscolhendoMapa(false) : onFechar())}>
      <View style={styles.backdrop}>
        <View style={styles.card}>
          <Pressable
            style={styles.fechar}
            onPress={onFechar}
            disabled={processando}
            accessibilityRole="button"
            accessibilityLabel="Fechar">
            <Ionicons name="close" size={20} color={t.muted} />
          </Pressable>

          <View style={styles.icone}>
            <Ionicons
              name={cancelarModo ? 'close-circle-outline' : 'calendar'}
              size={26}
              color={t.brandDeep}
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
                <Ionicons name="person-outline" size={15} color={t.muted} />
                <Text style={styles.detTxt}>{agendamento.profissional}</Text>
              </View>
              <View style={styles.detLinha}>
                <Ionicons name="calendar-outline" size={15} color={t.muted} />
                <Text style={styles.detTxt}>
                  {agendamento.dia} {agendamento.mes} · {agendamento.hora}
                </Text>
              </View>
              <View style={styles.detLinha}>
                <Ionicons name="location-outline" size={15} color={t.muted} />
                <Text style={styles.detTxt}>{agendamento.unidade}</Text>
              </View>
            </View>
          )}

          {/* "Como chegar" nos dois modos (aguardando e confirmado): toca → escolhe o app (Google Maps/
              Waze/Apple Maps) INLINE, sem abrir outro Modal por cima deste (evita o travamento no iOS). */}
          {local ? (
            escolhendoMapa ? (
              <View style={styles.mapaBox}>
                <View style={styles.mapaTopo}>
                  <Pressable
                    style={styles.mapaVoltar}
                    onPress={() => setEscolhendoMapa(false)}
                    accessibilityRole="button"
                    accessibilityLabel="Voltar">
                    <Ionicons name="chevron-back" size={22} color={t.muted} />
                  </Pressable>
                  <Text style={styles.mapaTitulo}>Abrir rota em</Text>
                </View>
                <OpcoesMapa
                  endereco={local.endereco}
                  latitude={local.latitude}
                  longitude={local.longitude}
                  onEscolher={() => setEscolhendoMapa(false)}
                />
              </View>
            ) : (
              <Pressable
                style={({ pressed }) => [styles.btnComoChegar, pressed && styles.pressed]}
                onPress={() => setEscolhendoMapa(true)}
                accessibilityRole="button"
                accessibilityLabel="Como chegar à unidade">
                <Ionicons name="navigate" size={20} color={t.brandDeep} />
                <Text style={styles.btnComoChegarTxt}>Como chegar</Text>
              </Pressable>
            )
          ) : null}

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
                <Ionicons name="lock-closed-outline" size={15} color={t.muted} />
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
  fechar: {
    position: 'absolute',
    top: 14,
    right: 14,
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: t.bg,
  },
  icone: {
    width: 60,
    height: 60,
    borderRadius: 18,
    backgroundColor: t.brandTint,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
    marginTop: 6,
  },
  titulo: { fontSize: 20, fontWeight: '800', color: t.ink, letterSpacing: -0.3 },
  subtitulo: {
    fontSize: 13.5,
    color: t.muted,
    textAlign: 'center',
    marginTop: 6,
    marginBottom: 18,
    lineHeight: 19,
  },
  detalhe: {
    width: '100%',
    backgroundColor: t.bg,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: t.line,
    padding: 16,
    marginBottom: 20,
    gap: 8,
  },
  detEspecialidade: { fontSize: 16, fontWeight: '800', color: t.ink, marginBottom: 2 },
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
    backgroundColor: t.brand,
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
  btnComoChegar: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 52,
    borderRadius: 16,
    backgroundColor: t.brandTint,
    marginBottom: 12,
  },
  btnComoChegarTxt: { color: t.brandDeep, fontSize: 15.5, fontWeight: '700' },
  mapaBox: {
    width: '100%',
    marginBottom: 12,
    backgroundColor: t.bg,
    borderRadius: 16,
    paddingHorizontal: 8,
    paddingVertical: 6,
  },
  mapaTopo: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
    paddingRight: 8,
    paddingBottom: 2,
  },
  mapaVoltar: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
  mapaTitulo: { fontSize: 13, fontWeight: '700', color: t.muted },
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
    backgroundColor: t.bg,
  },
  semCancelamentoTxt: { flex: 1, color: t.muted, fontSize: 13, fontWeight: '600' },
});
