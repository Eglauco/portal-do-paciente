import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { AgendamentoModal } from '@/components/agendamento-modal';
import { FaltaModal } from '@/components/falta-modal';
import { Agendamento, MotivoFalta } from '@/constants/agendamentos';
import { Brand, Status } from '@/constants/theme';
import {
  cancelarAgendamento,
  confirmarAgendamento,
  formatarRestante,
  infoCancelamento,
  justificarFalta,
  listarAgendamentos,
  listarMotivosFalta,
} from '@/services/agendamentos';

type Filtro = 'todos' | 'proximos' | 'concluidos';

const FILTROS: { chave: Filtro; rotulo: string }[] = [
  { chave: 'todos', rotulo: 'Todos' },
  { chave: 'proximos', rotulo: 'Próximos' },
  { chave: 'concluidos', rotulo: 'Concluídos' },
];

const capitalizar = (t: string) => t.charAt(0).toUpperCase() + t.slice(1);

/** Relógio do prazo de cancelamento no card (variante clara/escura conforme o fundo). */
function RelogioCancelamento({ a, agora, escuro }: { a: Agendamento; agora: number; escuro?: boolean }) {
  const info = infoCancelamento(a, agora);
  if (!info) return null;
  if (!info.podeCancelar) {
    const cor = escuro ? '#F4C7CE' : '#B23B4E';
    return (
      <View style={styles.relogioLinha}>
        <Ionicons name="lock-closed-outline" size={13} color={cor} />
        <Text style={[styles.relogioTxt, { color: cor }]}>Não pode mais ser cancelado</Text>
      </View>
    );
  }
  const cor = escuro ? Brand.glow : Brand.brandDeep;
  return (
    <View style={styles.relogioLinha}>
      <Ionicons name="timer-outline" size={13} color={cor} />
      <Text style={[styles.relogioTxt, { color: cor }]}>
        Cancelamento disponível por mais {formatarRestante(info.restanteMs)}
      </Text>
    </View>
  );
}

export default function AgendamentosScreen() {
  const [lista, setLista] = useState<Agendamento[]>([]);
  const [filtro, setFiltro] = useState<Filtro>('todos');
  const [selecionado, setSelecionado] = useState<Agendamento | null>(null);
  const [modoModal, setModoModal] = useState<'confirmar' | 'cancelar'>('confirmar');
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [atualizando, setAtualizando] = useState(false);
  const [processando, setProcessando] = useState(false);
  const [faltaSelecionada, setFaltaSelecionada] = useState<Agendamento | null>(null);
  const [motivosFalta, setMotivosFalta] = useState<MotivoFalta[]>([]);
  const [carregandoMotivos, setCarregandoMotivos] = useState(false);
  const [processandoFalta, setProcessandoFalta] = useState(false);
  const motivosCarregados = useRef(false);
  const entradaMostrada = useRef(false);
  const jaCarregou = useRef(false);
  const [agora, setAgora] = useState(Date.now());

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
        if (primeiroPendente) {
          setSelecionado(primeiroPendente);
          setModoModal('confirmar');
        }
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
  const temConfirmados = useMemo(() => lista.some((a) => a.status === 'confirmado'), [lista]);

  // Relógio: só liga o tick (30s) quando há agendamentos confirmados (onde a contagem aparece).
  useEffect(() => {
    if (!temConfirmados) return;
    const t = setInterval(() => setAgora(Date.now()), 30_000);
    return () => clearInterval(t);
  }, [temConfirmados]);

  const abrirCancelamento = (a: Agendamento) => {
    setSelecionado(a);
    setModoModal('cancelar');
  };
  const faltasPendentes = useMemo(
    () => lista.filter((a) => a.statusBackend === 'FALTA_PACIENTE' && !a.faltaJustificada),
    [lista],
  );
  // A lista normal exclui os que já estão em destaque (aguardando / falta a justificar).
  const demais = useMemo(
    () =>
      lista.filter(
        (a) => a.status !== 'aguardando' && !(a.statusBackend === 'FALTA_PACIENTE' && !a.faltaJustificada),
      ),
    [lista],
  );
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
    } catch (e) {
      // 409 = não é mais cancelável (prazo vencido OU status mudou no servidor): avisa e recarrega.
      if (e instanceof Error && e.message.includes('409')) {
        Alert.alert('Não foi possível cancelar', 'Este agendamento não está mais disponível para cancelamento.');
        setSelecionado(null);
        carregar(false);
      } else {
        Alert.alert('Ops', 'Não foi possível cancelar o agendamento. Tente novamente.');
      }
    } finally {
      setProcessando(false);
    }
  };

  const abrirFalta = useCallback(async (a: Agendamento) => {
    setFaltaSelecionada(a);
    if (motivosCarregados.current) return;
    setCarregandoMotivos(true);
    try {
      const ms = await listarMotivosFalta();
      setMotivosFalta(ms);
      motivosCarregados.current = true;
    } catch {
      // deixa vazio; a modal mostra "nenhum motivo disponível"
    } finally {
      setCarregandoMotivos(false);
    }
  }, []);

  const justificar = async (motivoIds: number[], justificativa: string) => {
    const alvo = faltaSelecionada;
    if (!alvo || processandoFalta) return;
    setProcessandoFalta(true);
    try {
      const atualizado = await justificarFalta(alvo.id, motivoIds, justificativa);
      setLista((l) => l.map((a) => (a.id === atualizado.id ? atualizado : a)));
      setFaltaSelecionada(null);
    } catch {
      Alert.alert('Ops', 'Não foi possível enviar a justificativa. Tente novamente.');
    } finally {
      setProcessandoFalta(false);
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
                onPress={() => {
                  setSelecionado(a);
                  setModoModal('confirmar');
                }}
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

        {/* Destaque: falta do paciente aguardando justificativa */}
        {!carregando && !erro && faltasPendentes.length > 0 && (
          <View style={styles.destaqueFalta}>
            <View style={styles.destaqueHeader}>
              <Ionicons name="alert-circle" size={18} color="#B23B4E" />
              <Text style={styles.destaqueFaltaTitulo}>Justifique sua falta</Text>
              <View style={styles.destaqueFaltaBadge}>
                <Text style={styles.destaqueBadgeTxt}>{faltasPendentes.length}</Text>
              </View>
            </View>

            {faltasPendentes.map((a) => (
              <Pressable
                key={a.id}
                onPress={() => abrirFalta(a)}
                style={({ pressed }) => [styles.faltaCard, pressed && styles.faltaCardPressed]}>
                <View style={styles.pendenteTopo}>
                  <View style={styles.faltaTag}>
                    <Ionicons name="close-circle" size={12} color="#fff" />
                    <Text style={styles.faltaTagTxt}>Falta registrada</Text>
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
                  <Text style={styles.faltaCtaTxt}>Toque para informar o motivo</Text>
                  <Ionicons name="arrow-forward" size={16} color="#fff" />
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
                // Só agendamentos CONFIRMADOS podem ser cancelados (toque abre o cancelamento).
                const cancelavel = a.status === 'confirmado';
                const conteudo = (
                  <>
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
                      {cancelavel && <RelogioCancelamento a={a} agora={agora} />}
                    </View>
                  </>
                );

                return cancelavel ? (
                  <Pressable
                    key={a.id}
                    onPress={() => abrirCancelamento(a)}
                    style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
                    {conteudo}
                  </Pressable>
                ) : (
                  <View key={a.id} style={styles.card}>{conteudo}</View>
                );
              })
            )}
          </>
        )}
      </ScrollView>

      <AgendamentoModal
        visivel={!!selecionado}
        modo={modoModal}
        agendamento={selecionado}
        processando={processando}
        podeCancelar={selecionado ? (infoCancelamento(selecionado, agora)?.podeCancelar ?? true) : true}
        onConfirmar={confirmar}
        onCancelar={cancelar}
        onFechar={() => setSelecionado(null)}
      />

      <FaltaModal
        visivel={!!faltaSelecionada}
        agendamento={faltaSelecionada}
        motivos={motivosFalta}
        carregandoMotivos={carregandoMotivos}
        processando={processandoFalta}
        onJustificar={justificar}
        onFechar={() => setFaltaSelecionada(null)}
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
  relogioLinha: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 10 },
  relogioTxt: { fontSize: 12, fontWeight: '700', flexShrink: 1 },
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

  // Destaque de falta (aguardando justificativa)
  destaqueFalta: {
    backgroundColor: '#FDECEE',
    borderRadius: 20,
    borderWidth: 1,
    borderColor: '#F4C9CF',
    padding: 12,
    marginBottom: 22,
  },
  destaqueFaltaTitulo: { flex: 1, fontSize: 13.5, fontWeight: '800', color: '#8A2531', letterSpacing: 0.2 },
  destaqueFaltaBadge: {
    minWidth: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: '#B23B4E',
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 6,
  },
  faltaCard: { backgroundColor: '#8A2F3B', borderRadius: 16, padding: 16, marginTop: 8 },
  faltaCardPressed: { backgroundColor: '#752530' },
  faltaTag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: 'rgba(255,255,255,0.16)',
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: 20,
  },
  faltaTagTxt: { fontSize: 11, fontWeight: '800', color: '#fff' },
  faltaCtaTxt: { flex: 1, fontSize: 13.5, fontWeight: '700', color: '#fff' },

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
  cardPressed: { backgroundColor: '#F1F6F4' },
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
