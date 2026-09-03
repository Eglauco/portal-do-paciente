import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';

import { NpsModal, NpsModalDados } from '@/components/nps-modal';
import { Brand } from '@/constants/theme';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import {
  buscarNps,
  CategoriaNps,
  listarCategoriasNps,
  listarNps,
  NpsDetalhe,
  NpsItem,
  responderNps,
} from '@/services/nps';

const doisDigitos = (n: number) => String(n).padStart(2, '0');

function dataCurta(iso: string): string {
  const d = new Date(iso);
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}/${d.getFullYear()}`;
}

export default function NpsScreen() {
  const [lista, setLista] = useState<NpsItem[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [atualizando, setAtualizando] = useState(false);
  const jaCarregou = useRef(false);

  // Modal
  const [modo, setModo] = useState<'avaliar' | 'ver' | null>(null);
  const [selecionado, setSelecionado] = useState<NpsItem | null>(null);
  const [detalhe, setDetalhe] = useState<NpsDetalhe | null>(null);
  const [processando, setProcessando] = useState(false);
  const [categorias, setCategorias] = useState<CategoriaNps[]>([]);
  const [carregandoCategorias, setCarregandoCategorias] = useState(false);
  const categoriasCarregadas = useRef(false);

  const carregar = useCallback(async (mostrarSpinner: boolean) => {
    try {
      if (mostrarSpinner) setCarregando(true);
      setErro(false);
      const dados = await listarNps();
      setLista(dados);
      jaCarregou.current = true;
    } catch {
      if (!jaCarregou.current) setErro(true); // silencioso: nao apaga o conteudo ja exibido
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      carregar(!jaCarregou.current);
    }, [carregar]),
  );

  // Notificação (app aberto) ou volta ao primeiro plano: atualiza sem spinner.
  useAtualizarComPush(() => carregar(false));

  const aoAtualizar = () => {
    setAtualizando(true);
    carregar(false);
  };

  const pendentes = useMemo(() => lista.filter((n) => n.status === 'PENDENTE'), [lista]);
  const avaliados = useMemo(() => lista.filter((n) => n.status === 'RESPONDIDO'), [lista]);

  const media = useMemo(() => {
    if (avaliados.length === 0) return '—';
    const soma = avaliados.reduce((s, a) => s + (a.media ?? 0), 0);
    return (soma / avaliados.length).toFixed(1);
  }, [avaliados]);

  const abrirAvaliar = async (item: NpsItem) => {
    setSelecionado(item);
    setDetalhe(null);
    setModo('avaliar');
    if (categoriasCarregadas.current) return;
    setCarregandoCategorias(true);
    try {
      const cats = await listarCategoriasNps();
      setCategorias(cats);
      categoriasCarregadas.current = true;
    } catch {
      // deixa vazio; a modal mostra "nenhuma categoria disponível"
    } finally {
      setCarregandoCategorias(false);
    }
  };

  const abrirVer = async (item: NpsItem) => {
    setSelecionado(item);
    setDetalhe(null);
    setModo('ver');
    try {
      const d = await buscarNps(item.id);
      setDetalhe(d);
    } catch {
      setDetalhe(null);
    }
  };

  const fechar = () => {
    setModo(null);
    setSelecionado(null);
    setDetalhe(null);
  };

  const enviar = async (notas: { categoriaId: number; nota: number }[], observacao: string) => {
    if (!selecionado || processando) return;
    setProcessando(true);
    try {
      await responderNps(selecionado.id, notas, observacao);
      fechar();
      await carregar(false);
    } catch {
      Alert.alert('Ops', 'Não foi possível enviar sua avaliação. Tente novamente.');
    } finally {
      setProcessando(false);
    }
  };

  const dadosModal: NpsModalDados | null = selecionado
    ? {
        especialidade: selecionado.especialidade.nome,
        unidade: selecionado.unidadeSaude.nome,
        dataHora: selecionado.dataHora,
        profissional: detalhe?.profissionalSaude.nome,
      }
    : null;

  return (
    <>
      <ScrollView
        style={styles.screen}
        contentContainerStyle={styles.content}
        refreshControl={
          <RefreshControl refreshing={atualizando} onRefresh={aoAtualizar} tintColor={Brand.brand} colors={[Brand.brand]} />
        }>
        <Text style={styles.title}>NPS</Text>
        <Text style={styles.subtitle}>Avalie os seus atendimentos.</Text>

        {carregando && (
          <View style={styles.estado}>
            <ActivityIndicator color={Brand.brand} />
            <Text style={styles.estadoTxt}>Carregando avaliações…</Text>
          </View>
        )}

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

        {!carregando && !erro && (
          <>
            {/* Destaque: avaliações pendentes */}
            {pendentes.length > 0 && (
              <View style={styles.destaque}>
                <View style={styles.destaqueHeader}>
                  <Ionicons name="star" size={18} color="#C77700" />
                  <Text style={styles.destaqueTitulo}>Avaliações pendentes</Text>
                  <View style={styles.destaqueBadge}>
                    <Text style={styles.destaqueBadgeTxt}>{pendentes.length}</Text>
                  </View>
                </View>

                {pendentes.map((n) => (
                  <Pressable
                    key={n.id}
                    onPress={() => abrirAvaliar(n)}
                    style={({ pressed }) => [styles.pendente, pressed && styles.pendentePressed]}>
                    <View style={styles.pendenteTopo}>
                      <View style={styles.pendenteTag}>
                        <Ionicons name="star-outline" size={12} color={Brand.brandPine} />
                        <Text style={styles.pendenteTagTxt}>Aguardando avaliação</Text>
                      </View>
                      <Text style={styles.pendenteData}>{dataCurta(n.dataHora)}</Text>
                    </View>

                    <Text style={styles.pendenteEsp}>{n.especialidade.nome}</Text>
                    <View style={styles.pendenteMeta}>
                      <Ionicons name="location-outline" size={14} color={Brand.onBrand} />
                      <Text style={styles.pendenteMetaTxt} numberOfLines={1}>
                        {n.unidadeSaude.nome}
                      </Text>
                    </View>

                    <View style={styles.pendenteCta}>
                      <Text style={styles.pendenteCtaTxt}>Toque para avaliar</Text>
                      <Ionicons name="arrow-forward" size={16} color={Brand.glow} />
                    </View>
                  </Pressable>
                ))}
              </View>
            )}

            {/* Resumo */}
            <View style={styles.resumo}>
              <View style={styles.mediaBox}>
                <Text style={styles.mediaNum}>{media}</Text>
                <View style={styles.mediaEstrelas}>
                  {[1, 2, 3, 4, 5].map((s) => {
                    const cheia = media !== '—' && Math.round(Number(media)) >= s;
                    return (
                      <Ionicons key={s} name={cheia ? 'star' : 'star-outline'} size={12} color={cheia ? '#F2A900' : '#CBD6D1'} />
                    );
                  })}
                </View>
                <Text style={styles.mediaLabel}>média de 5</Text>
              </View>
              <View style={styles.divisor} />
              <View style={styles.resumoInfo}>
                <Text style={styles.resumoInfoNum}>{avaliados.length}</Text>
                <Text style={styles.resumoInfoLabel}>
                  atendimento{avaliados.length === 1 ? '' : 's'} avaliado{avaliados.length === 1 ? '' : 's'}
                </Text>
              </View>
            </View>

            <Text style={styles.secao}>Atendimentos avaliados</Text>

            {avaliados.length === 0 ? (
              <Text style={styles.vazioTxt}>Você ainda não avaliou nenhum atendimento.</Text>
            ) : (
              avaliados.map((n) => (
                <Pressable
                  key={n.id}
                  onPress={() => abrirVer(n)}
                  style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}>
                  <View style={styles.nota}>
                    <Text style={styles.notaNum}>{n.media != null ? n.media.toFixed(1) : '—'}</Text>
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.especialidade}>{n.especialidade.nome}</Text>
                    <Text style={styles.meta}>
                      {dataCurta(n.dataHora)} · {n.unidadeSaude.nome}
                    </Text>
                  </View>
                  <Ionicons name="chevron-forward" size={20} color={Brand.muted} />
                </Pressable>
              ))
            )}
          </>
        )}
      </ScrollView>

      <NpsModal
        visivel={modo !== null}
        modo={modo ?? 'ver'}
        dados={dadosModal}
        categorias={categorias}
        carregandoCategorias={carregandoCategorias}
        mediaAtual={detalhe?.media ?? selecionado?.media}
        notasRespondidas={detalhe?.notas}
        observacaoAtual={detalhe?.observacao}
        respondidoEm={detalhe?.respondidoEm}
        processando={processando}
        onEnviar={enviar}
        onFechar={fechar}
      />
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  content: { padding: 20, paddingBottom: 32 },
  title: { fontSize: 26, fontWeight: '800', color: Brand.ink, letterSpacing: -0.4 },
  subtitle: { fontSize: 14, color: Brand.muted, marginTop: 4, marginBottom: 18 },

  // Estados
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

  // Destaque pendentes
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
  pendente: { backgroundColor: Brand.brandDeep, borderRadius: 16, padding: 16, marginTop: 8 },
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
    fontSize: 12.5,
    fontWeight: '800',
    color: Brand.onBrand,
    backgroundColor: 'rgba(255,255,255,0.12)',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 10,
    overflow: 'hidden',
  },
  pendenteEsp: { fontSize: 18, fontWeight: '800', color: '#fff', letterSpacing: -0.3 },
  pendenteMeta: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 8 },
  pendenteMetaTxt: { fontSize: 12.5, color: 'rgba(234,250,244,0.82)', flexShrink: 1 },
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

  // Resumo
  resumo: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: Brand.surface,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 18,
    marginBottom: 22,
  },
  mediaBox: { alignItems: 'center', width: 96 },
  mediaNum: { fontSize: 40, fontWeight: '800', color: Brand.brandDeep, letterSpacing: -1 },
  mediaEstrelas: { flexDirection: 'row', gap: 1, marginTop: 2 },
  mediaLabel: { fontSize: 12, color: Brand.muted, marginTop: 2 },
  divisor: { width: 1, alignSelf: 'stretch', backgroundColor: Brand.line, marginHorizontal: 16 },
  resumoInfo: { flex: 1 },
  resumoInfoNum: { fontSize: 26, fontWeight: '800', color: Brand.ink },
  resumoInfoLabel: { fontSize: 13, color: Brand.muted, marginTop: 2 },

  secao: { fontSize: 13, fontWeight: '700', color: Brand.muted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 12 },
  vazioTxt: { fontSize: 13.5, color: Brand.muted, paddingVertical: 8 },

  // Card avaliado
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: Brand.surface,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 12,
    marginBottom: 10,
  },
  cardPressed: { backgroundColor: '#F1F6F4' },
  nota: {
    width: 52,
    height: 52,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.brand,
  },
  notaNum: { fontSize: 18, fontWeight: '800', color: '#fff' },
  especialidade: { fontSize: 15, fontWeight: '700', color: Brand.ink },
  meta: { fontSize: 12.5, color: Brand.muted, marginTop: 3 },
});
