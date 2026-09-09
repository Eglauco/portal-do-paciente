import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';
import { useCallback, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { ScreenHeader } from '@/components/screen-header';
import { type Tema, useTema } from '@/hooks/use-tema';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import {
  Notificacao,
  TipoNotificacao,
  listarNotificacoes,
  marcarNotificacaoLida,
  marcarTodasLidas,
} from '@/services/notificacoes-lista';
import { navegarNotificacao } from '@/services/rota-notificacao';

const ESTILO_TIPO: Record<TipoNotificacao, { icon: string; fg: string; bg: string }> = {
  AGENDAMENTO: { icon: 'calendar', fg: '#0E8C7F', bg: '#DCF1EC' },
  FALTA: { icon: 'alert-circle', fg: '#C2410C', bg: '#FCE9DF' },
  NPS: { icon: 'star', fg: '#A5741A', bg: '#FBF0D6' },
  POSTAGEM: { icon: 'newspaper', fg: '#2F6DF6', bg: '#E9F0FE' },
  PRONTUARIO: { icon: 'document-text', fg: '#0A7D5A', bg: '#E3F6EC' },
  SAU: { icon: 'megaphone', fg: '#7A5AF5', bg: '#EFEAFE' },
  LEMBRETE: { icon: 'alarm', fg: '#0E8C7F', bg: '#DCF1EC' },
};

const doisDigitos = (n: number) => String(n).padStart(2, '0');

/** Rótulo do grupo (Hoje / Ontem / Anteriores) a partir da data. */
function grupoDe(iso: string): 'Hoje' | 'Ontem' | 'Anteriores' {
  const d = new Date(iso);
  const agora = new Date();
  if (d.toDateString() === agora.toDateString()) return 'Hoje';
  const ontem = new Date(agora);
  ontem.setDate(agora.getDate() - 1);
  if (d.toDateString() === ontem.toDateString()) return 'Ontem';
  return 'Anteriores';
}

/** Hora curta: HH:mm em Hoje/Ontem, dd/mm em Anteriores. */
function horaDe(iso: string, grupo: string): string {
  const d = new Date(iso);
  if (grupo === 'Anteriores') return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}`;
  return `${doisDigitos(d.getHours())}:${doisDigitos(d.getMinutes())}`;
}

const ORDEM_GRUPOS = ['Hoje', 'Ontem', 'Anteriores'] as const;

export default function NotificacoesScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const [itens, setItens] = useState<Notificacao[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [atualizando, setAtualizando] = useState(false);
  const [marcandoTodas, setMarcandoTodas] = useState(false);
  const jaCarregou = useRef(false);

  const carregar = useCallback(async (mostrarSpinner: boolean) => {
    try {
      if (mostrarSpinner) setCarregando(true);
      setErro(false);
      const dados = await listarNotificacoes();
      setItens(dados);
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

  const aoTocar = (n: Notificacao) => {
    // Marca como lida na hora (some o ponto) e persiste; navega para a origem.
    if (!n.lida) {
      setItens((atual) => atual.map((x) => (x.id === n.id ? { ...x, lida: true } : x)));
      void marcarNotificacaoLida(n.id).catch(() => {});
    }
    navegarNotificacao(n.tipo, n.referenciaId);
  };

  // Marca todas de uma vez: atualiza a tela na hora (otimista) e persiste; reverte se falhar.
  const marcarTodas = async () => {
    if (marcandoTodas) return;
    const anteriores = itens;
    setMarcandoTodas(true);
    setItens((atual) => atual.map((x) => (x.lida ? x : { ...x, lida: true })));
    try {
      await marcarTodasLidas();
    } catch {
      setItens(anteriores);
    } finally {
      setMarcandoTodas(false);
    }
  };

  const qtdNaoLidas = itens.filter((n) => !n.lida).length;

  // Agrupa preservando a ordem (backend já devolve do mais recente ao mais antigo).
  const grupos = ORDEM_GRUPOS.map((titulo) => ({
    titulo,
    itens: itens.filter((n) => grupoDe(n.criadoEm) === titulo),
  })).filter((g) => g.itens.length > 0);

  return (
    <View style={styles.screen}>
      <ScreenHeader title="Notificações" />
      {qtdNaoLidas > 0 && (
        <View style={styles.barra}>
          <Text style={styles.barraInfo}>
            {qtdNaoLidas} não lida{qtdNaoLidas > 1 ? 's' : ''}
          </Text>
          <Pressable
            onPress={marcarTodas}
            disabled={marcandoTodas}
            accessibilityRole="button"
            accessibilityLabel="Marcar todas como lidas"
            accessibilityState={{ busy: marcandoTodas, disabled: marcandoTodas }}
            style={({ pressed }) => [styles.barraBtn, pressed && styles.barraBtnPressed]}>
            {marcandoTodas ? (
              <ActivityIndicator size="small" color={t.brand} />
            ) : (
              <>
                <Ionicons name="checkmark-done" size={16} color={t.brand} />
                <Text style={styles.barraBtnTxt}>Marcar todas como lidas</Text>
              </>
            )}
          </Pressable>
        </View>
      )}
      <ScrollView
        contentContainerStyle={[styles.content, itens.length === 0 && styles.vazioContent]}
        refreshControl={
          <RefreshControl refreshing={atualizando} onRefresh={aoAtualizar} tintColor={t.brand} colors={[t.brand]} />
        }>
        {carregando ? (
          <View style={styles.estado}>
            <ActivityIndicator color={t.brand} />
            <Text style={styles.estadoTxt}>Carregando notificações…</Text>
          </View>
        ) : erro ? (
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="cloud-offline-outline" size={26} color={t.muted} />
            </View>
            <Text style={styles.estadoTitulo}>Não foi possível carregar</Text>
            <Text style={styles.estadoTxt}>Verifique sua conexão com o servidor e tente novamente.</Text>
            <Pressable style={styles.estadoBtn} onPress={() => carregar(true)}>
              <Ionicons name="refresh" size={16} color="#fff" />
              <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
            </Pressable>
          </View>
        ) : itens.length === 0 ? (
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="notifications-off-outline" size={26} color={t.muted} />
            </View>
            <Text style={styles.estadoTitulo}>Nenhuma notificação</Text>
            <Text style={styles.estadoTxt}>
              Quando houver novidades sobre seus atendimentos, elas aparecem aqui.
            </Text>
          </View>
        ) : (
          grupos.map((grupo) => (
            <View key={grupo.titulo} style={styles.grupo}>
              <Text style={styles.grupoTitulo}>{grupo.titulo}</Text>
              {grupo.itens.map((n) => {
                const e = ESTILO_TIPO[n.tipo];
                // Agendamento/Lembrete são da marca → seguem o tema; os demais são semânticos (fixos).
                const tipoMarca = n.tipo === 'AGENDAMENTO' || n.tipo === 'LEMBRETE';
                const iconeBg = tipoMarca ? t.brandTintStrong : e.bg;
                const iconeFg = tipoMarca ? t.brand : e.fg;
                return (
                  <Pressable
                    key={n.id}
                    onPress={() => aoTocar(n)}
                    accessibilityRole="button"
                    accessibilityLabel={`${n.titulo}${n.lida ? '' : ', não lida'}`}
                    style={({ pressed }) => [
                      styles.item,
                      !n.lida && styles.itemNaoLido,
                      pressed && styles.itemPressed,
                    ]}>
                    <View style={[styles.icon, { backgroundColor: iconeBg }]}>
                      <Ionicons name={e.icon as never} size={18} color={iconeFg} />
                    </View>
                    <View style={{ flex: 1 }}>
                      <View style={styles.itemTop}>
                        <Text style={[styles.titulo, !n.lida && styles.tituloForte]} numberOfLines={1}>
                          {n.titulo}
                        </Text>
                        <Text style={styles.hora}>{horaDe(n.criadoEm, grupo.titulo)}</Text>
                      </View>
                      <Text style={styles.descricao} numberOfLines={2}>
                        {n.corpo}
                      </Text>
                    </View>
                    {!n.lida && <View style={styles.dot} />}
                  </Pressable>
                );
              })}
            </View>
          ))
        )}
      </ScrollView>
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
  screen: { flex: 1, backgroundColor: t.bg },
  barra: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: t.surface,
    borderBottomWidth: 1,
    borderBottomColor: t.line,
  },
  barraInfo: { fontSize: 13, fontWeight: '600', color: t.muted },
  barraBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    height: 34,
    paddingHorizontal: 12,
    borderRadius: 10,
    backgroundColor: t.brandTint,
  },
  barraBtnPressed: { backgroundColor: '#DCEFE9' },
  barraBtnTxt: { fontSize: 13, fontWeight: '700', color: t.brand },
  content: { padding: 16, paddingBottom: 40 },
  vazioContent: { flexGrow: 1 },
  grupo: { marginBottom: 20 },
  grupoTitulo: {
    fontSize: 12,
    fontWeight: '700',
    color: t.muted,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginBottom: 10,
    marginLeft: 4,
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: t.surface,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: t.line,
    padding: 14,
    marginBottom: 10,
  },
  itemNaoLido: { borderColor: t.brandTintStrong, backgroundColor: t.brandTint },
  itemPressed: { backgroundColor: t.brandTint },
  icon: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  itemTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  titulo: { flex: 1, fontSize: 14.5, fontWeight: '700', color: t.ink },
  tituloForte: { fontWeight: '800' },
  hora: { fontSize: 11.5, color: t.muted },
  descricao: { fontSize: 13, color: '#40514C', marginTop: 2, lineHeight: 18 },
  dot: { width: 9, height: 9, borderRadius: 5, backgroundColor: t.brand },

  estado: { alignItems: 'center', justifyContent: 'center', paddingVertical: 60, gap: 10 },
  estadoIcone: {
    width: 56, height: 56, borderRadius: 18, backgroundColor: t.bg,
    borderWidth: 1, borderColor: t.line, alignItems: 'center', justifyContent: 'center',
  },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: t.ink, marginTop: 2 },
  estadoTxt: { fontSize: 13.5, color: t.muted, textAlign: 'center', paddingHorizontal: 32, lineHeight: 19 },
  estadoBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 7, marginTop: 6, height: 44,
    paddingHorizontal: 18, borderRadius: 14, backgroundColor: t.brand,
  },
  estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
});
