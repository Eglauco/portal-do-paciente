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

import { CategoriaNota, CategoriaNps } from '@/services/nps';
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
  /** Categorias ativas para avaliar (modo "avaliar"). */
  categorias: CategoriaNps[];
  carregandoCategorias?: boolean;
  /** Dados da resposta (modo "ver"). */
  mediaAtual?: number | null;
  notasRespondidas?: CategoriaNota[];
  observacaoAtual?: string | null;
  respondidoEm?: string | null;
  processando?: boolean;
  onEnviar: (notas: { categoriaId: number; nota: number }[], observacao: string) => void;
  onFechar: () => void;
}

const doisDigitos = (n: number) => String(n).padStart(2, '0');

function dataHoraFmt(iso: string): string {
  const d = new Date(iso);
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}/${d.getFullYear()} · ${doisDigitos(
    d.getHours(),
  )}:${doisDigitos(d.getMinutes())}`;
}

const ESTRELAS = [1, 2, 3, 4, 5];
const COR_ESTRELA = '#F2A900';

export function NpsModal({
  visivel,
  modo,
  dados,
  categorias,
  carregandoCategorias = false,
  mediaAtual,
  notasRespondidas,
  observacaoAtual,
  respondidoEm,
  processando = false,
  onEnviar,
  onFechar,
}: Props) {
  const [notas, setNotas] = useState<Record<number, number>>({});
  const [observacao, setObservacao] = useState('');

  // Reinicia o formulário sempre que o modal abre.
  useEffect(() => {
    if (visivel) {
      setNotas({});
      setObservacao('');
    }
  }, [visivel]);

  const avaliar = modo === 'avaliar';
  const todasRespondidas = categorias.length > 0 && categorias.every((c) => notas[c.id] !== undefined);
  const podeEnviar = todasRespondidas && !processando;

  const enviar = () => {
    if (!podeEnviar) return;
    onEnviar(
      categorias.map((c) => ({ categoriaId: c.id, nota: notas[c.id] })),
      observacao,
    );
  };

  return (
    <Modal visible={visivel} transparent animationType="fade" statusBarTranslucent onRequestClose={onFechar}>
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
              <Ionicons name={avaliar ? 'star' : 'star-outline'} size={24} color={Brand.brandDeep} />
            </View>

            <Text style={styles.titulo}>{avaliar ? 'Avaliar atendimento' : 'Sua avaliação'}</Text>
            <Text style={styles.subtitulo}>
              {avaliar
                ? 'Toque nas estrelas para avaliar cada categoria (1 a 5).'
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
                {carregandoCategorias ? (
                  <ActivityIndicator color={Brand.brand} style={{ marginVertical: 16 }} />
                ) : categorias.length === 0 ? (
                  <Text style={styles.semCategorias}>Nenhuma categoria disponível para avaliação.</Text>
                ) : (
                  categorias.map((cat) => (
                    <View key={cat.id} style={styles.catBloco}>
                      <Text style={styles.catNome}>{cat.nome}</Text>
                      <View style={styles.estrelas}>
                        {ESTRELAS.map((n) => {
                          const preenchida = (notas[cat.id] ?? 0) >= n;
                          return (
                            <Pressable
                              key={n}
                              onPress={() => setNotas((p) => ({ ...p, [cat.id]: n }))}
                              style={styles.estrelaBtn}
                              accessibilityRole="button"
                              accessibilityLabel={`${cat.nome}: ${n} de 5 estrelas`}
                              accessibilityState={{ selected: notas[cat.id] === n }}>
                              <Ionicons
                                name={preenchida ? 'star' : 'star-outline'}
                                size={38}
                                color={preenchida ? COR_ESTRELA : '#CBD6D1'}
                              />
                            </Pressable>
                          );
                        })}
                      </View>
                    </View>
                  ))
                )}

                <Text style={styles.rotulo}>Observação (opcional)</Text>
                <TextInput
                  style={styles.obsInput}
                  value={observacao}
                  onChangeText={setObservacao}
                  placeholder="Conte como foi o seu atendimento…"
                  placeholderTextColor="#9AAAA5"
                  multiline
                  editable={!processando}
                />
              </>
            ) : (
              <>
                <View style={styles.verMedia}>
                  <Text style={styles.verMediaNum}>{mediaAtual != null ? mediaAtual.toFixed(1) : '—'}</Text>
                  <View>
                    <View
                      style={styles.estrelasVer}
                      accessible
                      accessibilityLabel={mediaAtual != null ? `Média ${mediaAtual.toFixed(1)} de 5 estrelas` : 'Sem média'}>
                      {ESTRELAS.map((n) => (
                        <Ionicons
                          key={n}
                          name={mediaAtual != null && Math.round(mediaAtual) >= n ? 'star' : 'star-outline'}
                          size={16}
                          color={mediaAtual != null && Math.round(mediaAtual) >= n ? COR_ESTRELA : '#CBD6D1'}
                        />
                      ))}
                    </View>
                    <Text style={styles.verMediaDe}>média de 5</Text>
                  </View>
                </View>

                <Text style={styles.rotulo}>Notas por categoria</Text>
                <View style={styles.verNotas}>
                  {(notasRespondidas ?? []).map((n) => (
                    <View key={n.categoriaId} style={styles.verNotaLinha}>
                      <Text style={styles.verNotaCat}>{n.categoria}</Text>
                      <View
                        style={styles.estrelasVer}
                        accessible
                        accessibilityLabel={`${n.categoria}: ${n.nota} de 5 estrelas`}>
                        {ESTRELAS.map((s) => (
                          <Ionicons
                            key={s}
                            name={n.nota >= s ? 'star' : 'star-outline'}
                            size={16}
                            color={n.nota >= s ? COR_ESTRELA : '#CBD6D1'}
                          />
                        ))}
                      </View>
                    </View>
                  ))}
                </View>

                <Text style={styles.rotulo}>Sua observação</Text>
                <Text style={observacaoAtual ? styles.verObs : styles.verObsVazio}>
                  {observacaoAtual || 'Você não deixou observação.'}
                </Text>

                {!!respondidoEm && <Text style={styles.verQuando}>Enviada em {dataHoraFmt(respondidoEm)}</Text>}
              </>
            )}
          </ScrollView>

          {avaliar && (
            <Pressable
              style={[styles.btnEnviar, !podeEnviar && styles.btnEnviarDesativado]}
              onPress={enviar}
              disabled={!podeEnviar}>
              {processando ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <>
                  <Ionicons name="checkmark-circle" size={20} color="#fff" />
                  <Text style={styles.btnEnviarTxt}>Enviar avaliação</Text>
                </>
              )}
            </Pressable>
          )}
        </View>
      </KeyboardAvoidingView>
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
    maxHeight: '88%',
    backgroundColor: Brand.surface,
    borderRadius: 24,
    padding: 22,
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
    zIndex: 2,
  },
  scroll: { alignItems: 'center', paddingBottom: 4 },
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

  semCategorias: { alignSelf: 'flex-start', fontSize: 13, color: Brand.muted, marginBottom: 12 },
  catBloco: { width: '100%', marginBottom: 14 },
  catNome: { fontSize: 13.5, fontWeight: '700', color: Brand.ink, marginBottom: 8 },
  estrelas: { flexDirection: 'row', width: '100%', marginTop: 2 },
  estrelaBtn: { flex: 1, alignItems: 'center', paddingVertical: 4 },
  estrelasVer: { flexDirection: 'row', gap: 2 },

  rotulo: { alignSelf: 'flex-start', fontSize: 12.5, fontWeight: '700', color: Brand.muted, marginTop: 6, marginBottom: 8 },
  obsInput: {
    width: '100%',
    minHeight: 66,
    maxHeight: 120,
    backgroundColor: Brand.bg,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 12,
    fontSize: 14.5,
    color: Brand.ink,
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
    marginTop: 16,
    backgroundColor: Brand.brand,
  },
  btnEnviarDesativado: { opacity: 0.5 },
  btnEnviarTxt: { color: '#fff', fontSize: 15.5, fontWeight: '700' },

  // Ver
  verMedia: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: Brand.bg,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Brand.line,
    paddingVertical: 14,
    paddingHorizontal: 20,
    marginBottom: 4,
    alignSelf: 'stretch',
    justifyContent: 'center',
  },
  verMediaNum: { fontSize: 44, fontWeight: '800', color: Brand.brandDeep, letterSpacing: -1 },
  verMediaDe: { fontSize: 15, color: Brand.muted, fontWeight: '600' },
  verNotas: { width: '100%', gap: 8 },
  verNotaLinha: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    backgroundColor: Brand.bg,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: Brand.line,
    paddingVertical: 10,
    paddingHorizontal: 14,
  },
  verNotaCat: { flex: 1, fontSize: 14, fontWeight: '600', color: Brand.ink },
  verObs: { alignSelf: 'flex-start', fontSize: 14.5, color: Brand.ink, lineHeight: 20 },
  verObsVazio: { alignSelf: 'flex-start', fontSize: 14, color: Brand.muted, fontStyle: 'italic' },
  verQuando: { alignSelf: 'flex-start', fontSize: 12, color: Brand.muted, marginTop: 12 },
});
