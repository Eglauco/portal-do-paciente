import { Ionicons } from '@expo/vector-icons';
import { useEffect, useMemo, useState } from 'react';
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

import { Agendamento, MotivoFalta } from '@/constants/agendamentos';
import { alpha, type Tema, useTema } from '@/hooks/use-tema';

interface Props {
  visivel: boolean;
  agendamento: Agendamento | null;
  motivos: MotivoFalta[];
  carregandoMotivos?: boolean;
  processando?: boolean;
  onJustificar: (motivoIds: number[], justificativa: string) => void;
  onFechar: () => void;
}

export function FaltaModal({
  visivel,
  agendamento,
  motivos,
  carregandoMotivos = false,
  processando = false,
  onJustificar,
  onFechar,
}: Props) {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const [selecionados, setSelecionados] = useState<number[]>([]);
  const [texto, setTexto] = useState('');

  // Limpa a seleção sempre que abre para um agendamento diferente.
  useEffect(() => {
    setSelecionados([]);
    setTexto('');
  }, [agendamento?.id]);

  const alternar = (id: number) => {
    setSelecionados((atual) => (atual.includes(id) ? atual.filter((x) => x !== id) : [...atual, id]));
  };

  const podeEnviar = selecionados.length > 0 && !processando;

  const enviar = () => {
    if (!podeEnviar) return;
    onJustificar(selecionados, texto.trim());
  };

  return (
    <Modal visible={visivel} transparent animationType="fade" onRequestClose={onFechar}>
      <KeyboardAvoidingView
        style={styles.backdrop}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <View style={styles.card}>
          <Pressable
            style={styles.fechar}
            onPress={onFechar}
            disabled={processando}
            accessibilityRole="button"
            accessibilityLabel="Fechar">
            <Ionicons name="close" size={20} color={t.muted} />
          </Pressable>

          <ScrollView
            contentContainerStyle={styles.scroll}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}>
            <View style={styles.icone}>
              <Ionicons name="alert-circle" size={26} color="#C77700" />
            </View>

            <Text style={styles.titulo}>Informe o motivo da falta</Text>
            <Text style={styles.subtitulo}>
              Você não compareceu a este agendamento. Conte pra gente o que aconteceu.
            </Text>

            {agendamento && (
              <View style={styles.detalhe}>
                <Text style={styles.detEspecialidade}>{agendamento.especialidade}</Text>
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

            <Text style={styles.secao}>Selecione o(s) motivo(s)</Text>
            {carregandoMotivos ? (
              <ActivityIndicator color={t.brand} style={{ marginVertical: 12 }} />
            ) : motivos.length === 0 ? (
              <Text style={styles.semMotivos}>Nenhum motivo disponível no momento.</Text>
            ) : (
              <View style={styles.chips}>
                {motivos.map((m) => {
                  const ativo = selecionados.includes(m.id);
                  return (
                    <Pressable
                      key={m.id}
                      onPress={() => alternar(m.id)}
                      disabled={processando}
                      style={[styles.chip, ativo && styles.chipAtivo]}
                      accessibilityRole="checkbox"
                      accessibilityState={{ checked: ativo }}>
                      {ativo && <Ionicons name="checkmark" size={14} color="#fff" />}
                      <Text style={[styles.chipTxt, ativo && styles.chipTxtAtivo]}>{m.motivo}</Text>
                    </Pressable>
                  );
                })}
              </View>
            )}

            <Text style={styles.secao}>Quer explicar melhor? (opcional)</Text>
            <TextInput
              style={styles.campo}
              value={texto}
              onChangeText={setTexto}
              placeholder="Escreva o porquê não conseguiu comparecer…"
              placeholderTextColor="#9AAAA5"
              multiline
              editable={!processando}
            />
          </ScrollView>

          <Pressable
            style={({ pressed }) => [
              styles.btnEnviar,
              (!podeEnviar || pressed) && styles.btnEnviarInativo,
            ]}
            onPress={enviar}
            disabled={!podeEnviar}>
            {processando ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <>
                <Ionicons name="checkmark-circle" size={20} color="#fff" />
                <Text style={styles.btnEnviarTxt}>Enviar justificativa</Text>
              </>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>
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
    maxWidth: 400,
    maxHeight: '86%',
    backgroundColor: t.surface,
    borderRadius: 24,
    padding: 24,
  },
  fechar: {
    position: 'absolute',
    top: 14,
    right: 14,
    zIndex: 2,
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: t.bg,
  },
  scroll: { alignItems: 'center', paddingBottom: 4 },
  icone: {
    width: 60,
    height: 60,
    borderRadius: 18,
    backgroundColor: '#FFF3DD',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 14,
    marginTop: 6,
  },
  titulo: { fontSize: 20, fontWeight: '800', color: t.ink, letterSpacing: -0.3, textAlign: 'center' },
  subtitulo: {
    fontSize: 13.5,
    color: t.muted,
    textAlign: 'center',
    marginTop: 6,
    marginBottom: 16,
    lineHeight: 19,
  },
  detalhe: {
    width: '100%',
    backgroundColor: t.bg,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: t.line,
    padding: 14,
    gap: 7,
  },
  detEspecialidade: { fontSize: 15.5, fontWeight: '800', color: t.ink, marginBottom: 2 },
  detLinha: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  detTxt: { fontSize: 13, color: '#40514C' },
  secao: { alignSelf: 'flex-start', fontSize: 13, fontWeight: '800', color: t.ink, marginTop: 18, marginBottom: 10 },
  semMotivos: { alignSelf: 'flex-start', fontSize: 13, color: t.muted },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, width: '100%' },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: t.line,
    backgroundColor: t.surface,
  },
  chipAtivo: { backgroundColor: t.brand, borderColor: t.brand },
  chipTxt: { fontSize: 13, fontWeight: '600', color: t.ink },
  chipTxtAtivo: { color: '#fff' },
  campo: {
    width: '100%',
    minHeight: 74,
    maxHeight: 130,
    backgroundColor: t.bg,
    borderWidth: 1,
    borderColor: t.line,
    borderRadius: 14,
    paddingHorizontal: 14,
    paddingVertical: 10,
    fontSize: 14.5,
    color: t.ink,
    textAlignVertical: 'top',
  },
  btnEnviar: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 52,
    borderRadius: 16,
    marginTop: 18,
    backgroundColor: t.brand,
  },
  btnEnviarInativo: { opacity: 0.5 },
  btnEnviarTxt: { color: '#fff', fontSize: 15.5, fontWeight: '700' },
});
