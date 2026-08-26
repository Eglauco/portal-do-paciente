import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { AgendamentoModal } from '@/components/agendamento-modal';
import { Agendamento } from '@/constants/agendamentos';
import { Brand, Status } from '@/constants/theme';
import { cancelarAgendamento, confirmarAgendamento, listarAgendamentos } from '@/services/agendamentos';

type Filtro = 'todos' | 'proximos' | 'concluidos';

const FILTROS: { chave: Filtro; rotulo: string }[] = [
  { chave: 'todos', rotulo: 'Todos' },
  { chave: 'proximos', rotulo: 'Próximos' },
  { chave: 'concluidos', rotulo: 'Concluídos' },
];

const capitalizar = (t: string) => t.charAt(0).toUpperCase() + t.slice(1);

export default function AgendamentosScreen() {
  const [lista, setLista] = useState<Agendamento[]>([]);
  const [filtro, setFiltro] = useState<Filtro>('todos');
  const [selecionado, setSelecionado] = useState<Agendamento | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [atualizando, setAtualizando] = useState(false);
  const [processando, setProcessando] = useState(false);
  const entradaMostrada = useRef(false);
  const jaCarregou = useRef(false);

  const carregar = useCallback(async (mostrarSpinner: boolean) => {
    try {
      if (mostrarSpinner) setCarregando(true);
      setErro(false);
      const dados = await listarAgendamentos();
      setLista(dados);
      jaCarregou.current = true;
      // Pop-up de entrada: abre o primeiro pendente na primeira carga bem-sucedida.
      if (!entradaMostrada.current) {
        entradaMostrada.current = true;
        const primeiroPendente = dados.find((a) => a.status === 'aguardando');
        if (primeiroPendente) setSelecionado(primeiroPendente);
      }
    } catch {
      setErro(true);
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, []);

  // Recarrega ao focar a aba (reflete mudanças de status), com spinner só na 1ª vez.
  useFocusEffect(
    useCallback(() => {
      carregar(!jaCarregou.current);
    }, [carregar]),
  );

  const aoAtualizar = () => {
    setAtualizando(true);
    carregar(false);
  };

  const pendentes = useMemo(() => lista.filter((a) => a.status === 'aguardando'), [lista]);
  const demais = useMemo(() => lista.filter((a) => a.status !== 'aguardando'), [lista]);
  const filtrados = useMemo(
    () => (filtro === 'todos' ? demais : demais.filter((a) => a.grupo === filtro)),
    [demais, filtro],
  );

  const confirmar = async () => {
    const alvo = selecionado;
    if (!alvo || processando) return;
    setProcessando(true);
    try {
      const atualizado = await confirmarAgendamento(alvo.id);
      setLista((l) => l.map((a) => (a.id === atualizado.id ? atualizado : a)));
      setSelecionado(null);
    } catch {
      Alert.alert('Ops', 'Não foi possível confirmar o agendamento. Tente novamente.');
    } finally {
      setProcessando(false);
    }
  };

  const cancelar = async () => {
    const alvo = selecionado;
    if (!alvo || processando) return;
    setProcessando(true);
    try {
      const atualizado = await cancelarAgendamento(alvo.id);
      setLista((l) => l.map((a) => (a.id === atualizado.id ? atualizado : a)));
      setSelecionado(null);
    } catch {
      Alert.alert('Ops', 'Não foi possível cancelar o agendamento. Tente novamente.');
    } finally {
      setProcessando(false);
    }
  };

  return (
    <>
      <ScrollView
        style={styles.screen}
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl refreshing={atualizando} onRefresh={aoAtualizar} tintColor={Brand.brand} colors={[Brand.brand]} />
        }>
        <Text style={styles.title}>Agendamentos</Text>
        <Text style={styles.subtitle}>Acompanhe suas consultas e exames.</Text>

        {/* Carregando (primeira carga) */}
        {carregando && (
          <View style={styles.estado}>
            <ActivityIndicator color={Brand.brand} />
            <Text style={styles.estadoTxt}>Carregando agendamentos…</Text>
          </View>
        )}

        {/* Erro de carga */}
        {!carregando && erro && (
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="cloud-offline-outline" size={26} color={Brand.muted} />
            </View>
            <Text style={styles.estadoTitulo}>Não foi possível carregar</Text>
            <Text style={styles.estadoTxt}>Verifique sua conexão com o servidor e tente novamente.</Text>
            <Pressable style={styles.estadoBtn} onPress={() => carregar(true)}>
              <Ionicons name="refresh" size={16} color="#fff" />
              <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
            </Pressable>
          </View>
        )}

        {/* Vazio */}
        {!carregando && !erro && lista.length === 0 && (
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="calendar-outline" size={26} color={Brand.muted} />
            </View>
            <Text style={styles.estadoTitulo}>Nenhum agendamento</Text>
            <Text style={styles.estadoTxt}>Você ainda não possui agendamentos cadastrados.</Text>
          </View>
        )}

        {/* Destaque total: aguardando confirmação */}
        {!carregando && !erro && pendentes.length > 0 && (
          <View style={styles.destaque}>
            <View style={styles.destaqueHeader}>
              <Ionicons name="alert-circle" size={18} color="#C77700" />
              <Text style={styles.destaqueTitulo}>Confirmação pendente</Text>
              <View style={styles.destaqueBadge}>
                <Text style={styles.destaqueBadgeTxt}>{pendentes.length}</Text>
              </View>
            </View>

            {pendentes.map((a) => (
              <Pressable
                key={a.id}
                onPress={() => setSelecionado(a)}
                style={({ pressed }) => [styles.pendente, pressed && styles.pendentePressed]}>
                <View style={styles.pendenteTopo}>
                  <View style={styles.pendenteTag}>
                    <Ionicons name="time" size={12} color={Brand.brandPine} />
                    <Text style={styles.pendenteTagTxt}>Aguardando confirmação</Text>
                  </View>
                  <View style={styles.pendenteData}>
                    <Text style={styles.pendenteDataTxt}>
                      {a.dia} {a.mes}
                    </Text>
                  </View>
                </View>

                <Text style={styles.pendenteEsp}>{a.especialidade}</Text>
                <Text style={styles.pendenteProf}>{a.profissional}</Text>
                <View style={styles.pendenteMeta}>
                  <Ionicons name="time-outline" size={14} color={Brand.onBrand} />
                  <Text style={styles.pendenteMetaTxt}>{a.hora}</Text>
                  <Text style={styles.pendenteDot}>·</Text>
                  <Ionicons name="location-outline" size={14} color={Brand.onBrand} />
                  <Text style={styles.pendenteMetaTxt} numberOfLines={1}>
                    {a.unidade}
                  </Text>
                </View>

                <View style={styles.pendenteCta}>
                  <Text style={styles.pendenteCtaTxt}>Toque para confirmar</Text>
                  <Ionicons name="arrow-forward" size={16} color={Brand.glow} />
                </View>
              </Pressable>
            ))}
          </View>
        )}

        {!carregando && !erro && lista.length > 0 && (
          <>
            <View style={styles.segment}>
              {FILTROS.map((f) => {
                const ativo = filtro === f.chave;
                return (
                  <Pressable
                    key={f.chave}
                    onPress={() => setFiltro(f.chave)}
                    style={[styles.segmentBtn, ativo && styles.segmentBtnAtivo]}>
                    <Text style={[styles.segmentTxt, ativo && styles.segmentTxtAtivo]}>{f.rotulo}</Text>
                  </Pressable>
                );
              })}
            </View>

            {filtrados.length === 0 ? (
              <Text style={styles.semItens}>Nenhum agendamento neste filtro.</Text>
            ) : (
              filtrados.map((a) => {
                const cor = Status[a.status];
                return (
                  <View key={a.id} style={styles.card}>
                    <View style={styles.dateBox}>
                      <Text style={styles.dateDay}>{a.dia}</Text>
                      <Text style={styles.dateMonth}>{a.mes}</Text>
                      <Text style={styles.dateWeek}>{a.semana}</Text>
                    </View>

                    <View style={styles.info}>
                      <View style={styles.infoTop}>
                        <Text style={styles.especialidade} numberOfLines={1}>
                          {a.especialidade}
                        </Text>
                        <View style={[styles.pill, { backgroundColor: cor.bg }]}>
                          <Text style={[styles.pillTxt, { color: cor.fg }]}>
                            {a.statusLabel ?? capitalizar(a.status)}
                          </Text>
                        </View>
                      </View>
                      <Text style={styles.profissional}>{a.profissional}</Text>
                      <View style={styles.metaRow}>
                        <Ionicons name="time-outline" size={14} color={Brand.muted} />
                        <Text style={styles.meta}>{a.hora}</Text>
                        <Text style={styles.metaDot}>·</Text>
                        <Ionicons name="location-outline" size={14} color={Brand.muted} />
                        <Text style={styles.meta} numberOfLines={1}>
                          {a.unidade}
                        </Text>
                      </View>
                    </View>
                  </View>
                );
              })
            )}
          </>
        )}
      </ScrollView>

      <AgendamentoModal
        visivel={!!selecionado}
        agendamento={selecionado}
        processando={processando}
        onConfirmar={confirmar}
        onCancelar={cancelar}
        onFechar={() => setSelecionado(null)}
      />
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  content: { padding: 20, paddingBottom: 32 },
  title: { fontSize: 26, fontWeight: '800', color: Brand.ink, letterSpacing: -0.4 },
  subtitle: { fontSize: 14, color: Brand.muted, marginTop: 4, marginBottom: 18 },

  // Estados (carregando / erro / vazio)
  estado: { alignItems: 'center', justifyContent: 'center', paddingVertical: 48, gap: 10 },
  estadoIcone: {
    width: 56,
    height: 56,
    borderRadius: 18,
    backgroundColor: Brand.surface,
    borderWidth: 1,
    borderColor: Brand.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: Brand.ink, marginTop: 2 },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center', paddingHorizontal: 24, lineHeight: 19 },
  estadoBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    marginTop: 6,
    height: 44,
    paddingHorizontal: 18,
    borderRadius: 14,
    backgroundColor: Brand.brand,
  },
  estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
  semItens: { fontSize: 13.5, color: Brand.muted, textAlign: 'center', paddingVertical: 24 },

  // Destaque
  destaque: {
    backgroundColor: '#FFF7E8',
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#F4E1B8',
    padding: 12,
    marginBottom: 22,
  },
  destaqueHeader: { flexDirection: 'row', alignItems: 'center', gap: 7, paddingHorizontal: 4, paddingVertical: 6 },
  destaqueTitulo: { flex: 1, fontSize: 13.5, fontWeight: '800', color: '#8A5A00', letterSpacing: 0.2 },
  destaqueBadge: { minWidth: 22, height: 22, borderRadius: 11, backgroundColor: '#C77700', alignItems: 'center', justifyContent: 'center', paddingHorizontal: 6 },
  destaqueBadgeTxt: { color: '#fff', fontSize: 12, fontWeight: '800' },
  pendente: {
    backgroundColor: Brand.brandDeep,
    borderRadius: 16,
    padding: 16,
    marginTop: 8,
  },
  pendentePressed: { backgroundColor: Brand.brandPine },
  pendenteTopo: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 },
  pendenteTag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: Brand.glow,
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: 20,
  },
  pendenteTagTxt: { fontSize: 11, fontWeight: '800', color: Brand.brandPine },
  pendenteData: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 10,
  },
  pendenteDataTxt: { fontSize: 12.5, fontWeight: '800', color: Brand.onBrand },
  pendenteEsp: { fontSize: 18, fontWeight: '800', color: '#fff', letterSpacing: -0.3 },
  pendenteProf: { fontSize: 13.5, color: 'rgba(234,250,244,0.82)', marginTop: 2 },
  pendenteMeta: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 10 },
  pendenteMetaTxt: { fontSize: 12.5, color: 'rgba(234,250,244,0.82)', flexShrink: 1 },
  pendenteDot: { color: 'rgba(234,250,244,0.6)', marginHorizontal: 2 },
  pendenteCta: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginTop: 14,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: 'rgba(255,255,255,0.14)',
  },
  pendenteCtaTxt: { flex: 1, fontSize: 13.5, fontWeight: '700', color: Brand.glow },

  // Segmented
  segment: { flexDirection: 'row', backgroundColor: '#EAF1EE', borderRadius: 12, padding: 4, marginBottom: 18 },
  segmentBtn: { flex: 1, paddingVertical: 8, borderRadius: 9, alignItems: 'center' },
  segmentBtnAtivo: {
    backgroundColor: Brand.surface,
    shadowColor: Brand.brandDeep,
    shadowOpacity: 0.12,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  segmentTxt: { fontSize: 13, fontWeight: '600', color: Brand.muted },
  segmentTxtAtivo: { color: Brand.brandDeep },

  // Card normal
  card: {
    flexDirection: 'row',
    gap: 14,
    backgroundColor: Brand.surface,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 14,
    marginBottom: 12,
  },
  dateBox: { width: 58, borderRadius: 14, backgroundColor: '#E7F3EF', alignItems: 'center', justifyContent: 'center', paddingVertical: 10 },
  dateDay: { fontSize: 22, fontWeight: '800', color: Brand.brandDeep, lineHeight: 24 },
  dateMonth: { fontSize: 11, fontWeight: '700', color: Brand.brand, letterSpacing: 1 },
  dateWeek: { fontSize: 11, color: Brand.muted, marginTop: 2 },
  info: { flex: 1, justifyContent: 'center' },
  infoTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  especialidade: { flex: 1, fontSize: 15, fontWeight: '700', color: Brand.ink },
  pill: { paddingHorizontal: 9, paddingVertical: 4, borderRadius: 20 },
  pillTxt: { fontSize: 11, fontWeight: '700' },
  profissional: { fontSize: 13, color: '#40514C', marginTop: 3 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 8 },
  meta: { fontSize: 12.5, color: Brand.muted, flexShrink: 1 },
  metaDot: { color: Brand.muted, marginHorizontal: 2 },
});
