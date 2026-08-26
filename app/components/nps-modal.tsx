import { Ionicons } from '@expo/vector-icons';
import { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { Brand } from '@/constants/theme';

export interface NpsModalDados {
  especialidade: string;
  unidade: string;
  dataHora: string;
  profissional?: string;
}

interface Props {
  visivel: boolean;
  modo: 'avaliar' | 'ver';
  dados: NpsModalDados | null;
  notaAtual?: number | null;
  observacaoAtual?: string | null;
  respondidoEm?: string | null;
  processando?: boolean;
  onEnviar: (nota: number, observacao: string) => void;
  onFechar: () => void;
}

const doisDigitos = (n: number) => String(n).padStart(2, '0');

function dataHoraFmt(iso: string): string {
  const d = new Date(iso);
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}/${d.getFullYear()} · ${doisDigitos(
    d.getHours(),
  )}:${doisDigitos(d.getMinutes())}`;
}

const NOTAS = Array.from({ length: 11 }, (_, i) => i);

export function NpsModal({
  visivel,
  modo,
  dados,
  notaAtual,
  observacaoAtual,
  respondidoEm,
  processando = false,
  onEnviar,
  onFechar,
}: Props) {
  const [nota, setNota] = useState<number | null>(null);
  const [observacao, setObservacao] = useState('');

  // Reinicia o formulário sempre que o modal abre.
  useEffect(() => {
    if (visivel) {
      setNota(null);
      setObservacao('');
    }
  }, [visivel]);

  const avaliar = modo === 'avaliar';

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
            <Ionicons name={avaliar ? 'star' : 'star-outline'} size={24} color={Brand.brandDeep} />
          </View>

          <Text style={styles.titulo}>{avaliar ? 'Avaliar atendimento' : 'Sua avaliação'}</Text>
          <Text style={styles.subtitulo}>
            {avaliar
              ? 'Dê uma nota de 0 a 10 para o seu atendimento.'
              : 'Esta avaliação já foi enviada e não pode ser alterada.'}
          </Text>

          {dados && (
            <View style={styles.detalhe}>
              <Text style={styles.detEsp}>{dados.especialidade}</Text>
              {!!dados.profissional && (
                <View style={styles.detLinha}>
                  <Ionicons name="person-outline" size={15} color={Brand.muted} />
                  <Text style={styles.detTxt}>{dados.profissional}</Text>
                </View>
              )}
              <View style={styles.detLinha}>
                <Ionicons name="calendar-outline" size={15} color={Brand.muted} />
                <Text style={styles.detTxt}>{dataHoraFmt(dados.dataHora)}</Text>
              </View>
              <View style={styles.detLinha}>
                <Ionicons name="location-outline" size={15} color={Brand.muted} />
                <Text style={styles.detTxt}>{dados.unidade}</Text>
              </View>
            </View>
          )}

          {avaliar ? (
            <>
              <Text style={styles.rotulo}>Nota</Text>
              <View style={styles.notas}>
                {NOTAS.map((n) => {
                  const ativo = nota === n;
                  return (
                    <Pressable
                      key={n}
                      onPress={() => setNota(n)}
                      style={[styles.notaBtn, ativo && styles.notaBtnAtivo]}>
                      <Text style={[styles.notaBtnTxt, ativo && styles.notaBtnTxtAtivo]}>{n}</Text>
                    </Pressable>
                  );
                })}
              </View>

              <Text style={styles.rotulo}>Observação (opcional)</Text>
              <TextInput
                style={styles.obsInput}
                value={observacao}
                onChangeText={setObservacao}
                placeholder="Conte como foi o seu atendimento…"
                placeholderTextColor="#9AAAA5"
                multiline
              />

              <Pressable
                style={[styles.btnEnviar, (nota === null || processando) && styles.btnEnviarDesativado]}
                onPress={() => nota !== null && onEnviar(nota, observacao)}
                disabled={nota === null || processando}>
                {processando ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <>
                    <Ionicons name="checkmark-circle" size={20} color="#fff" />
                    <Text style={styles.btnEnviarTxt}>Enviar avaliação</Text>
                  </>
                )}
              </Pressable>
            </>
          ) : (
            <>
              <View style={styles.verNota}>
                <Text style={styles.verNotaNum}>{notaAtual}</Text>
                <Text style={styles.verNotaDe}>de 10</Text>
              </View>

              <Text style={styles.rotulo}>Sua observação</Text>
              <ScrollView style={styles.verObsWrap} contentContainerStyle={styles.verObsContent}>
                <Text style={observacaoAtual ? styles.verObs : styles.verObsVazio}>
                  {observacaoAtual || 'Você não deixou observação.'}
                </Text>
              </ScrollView>

              {!!respondidoEm && (
                <Text style={styles.verQuando}>Enviada em {dataHoraFmt(respondidoEm)}</Text>
              )}
            </>
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
    maxWidth: 400,
    backgroundColor: Brand.surface,
    borderRadius: 24,
    padding: 22,
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
    zIndex: 1,
  },
  icone: {
    width: 56,
    height: 56,
    borderRadius: 18,
    backgroundColor: '#E7F3EF',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12,
    marginTop: 4,
  },
  titulo: { fontSize: 19, fontWeight: '800', color: Brand.ink, letterSpacing: -0.3 },
  subtitulo: { fontSize: 13, color: Brand.muted, textAlign: 'center', marginTop: 6, marginBottom: 16, lineHeight: 18 },

  detalhe: {
    width: '100%',
    backgroundColor: Brand.bg,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 14,
    marginBottom: 16,
    gap: 7,
  },
  detEsp: { fontSize: 15.5, fontWeight: '800', color: Brand.ink, marginBottom: 1 },
  detLinha: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  detTxt: { fontSize: 13, color: '#40514C' },

  rotulo: { alignSelf: 'flex-start', fontSize: 12.5, fontWeight: '700', color: Brand.muted, marginBottom: 8 },
  notas: { flexDirection: 'row', flexWrap: 'wrap', gap: 7, marginBottom: 16 },
  notaBtn: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.bg,
    borderWidth: 1,
    borderColor: Brand.line,
  },
  notaBtnAtivo: { backgroundColor: Brand.brand, borderColor: Brand.brand },
  notaBtnTxt: { fontSize: 15, fontWeight: '700', color: Brand.ink },
  notaBtnTxtAtivo: { color: '#fff' },

  obsInput: {
    width: '100%',
    minHeight: 74,
    maxHeight: 120,
    backgroundColor: Brand.bg,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 12,
    fontSize: 14.5,
    color: Brand.ink,
    textAlignVertical: 'top',
    marginBottom: 18,
  },
  btnEnviar: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    height: 52,
    borderRadius: 16,
    backgroundColor: Brand.brand,
  },
  btnEnviarDesativado: { opacity: 0.5 },
  btnEnviarTxt: { color: '#fff', fontSize: 15.5, fontWeight: '700' },

  verNota: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: 8,
    backgroundColor: Brand.bg,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Brand.line,
    paddingVertical: 14,
    paddingHorizontal: 20,
    marginBottom: 16,
  },
  verNotaNum: { fontSize: 44, fontWeight: '800', color: Brand.brandDeep, letterSpacing: -1 },
  verNotaDe: { fontSize: 15, color: Brand.muted, fontWeight: '600' },
  verObsWrap: {
    width: '100%',
    maxHeight: 120,
    backgroundColor: Brand.bg,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Brand.line,
  },
  verObsContent: { padding: 12 },
  verObs: { fontSize: 14.5, color: Brand.ink, lineHeight: 20 },
  verObsVazio: { fontSize: 14, color: Brand.muted, fontStyle: 'italic' },
  verQuando: { alignSelf: 'flex-start', fontSize: 12, color: Brand.muted, marginTop: 12 },
});
