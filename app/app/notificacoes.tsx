import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
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
import { Brand } from '@/constants/theme';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import {
  Notificacao,
  TipoNotificacao,
  listarNotificacoes,
  marcarNotificacaoLida,
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
  const [itens, setItens] = useState<Notificacao[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [atualizando, setAtualizando] = useState(false);
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

  // Agrupa preservando a ordem (backend já devolve do mais recente ao mais antigo).
  const grupos = ORDEM_GRUPOS.map((titulo) => ({
    titulo,
    itens: itens.filter((n) => grupoDe(n.criadoEm) === titulo),
  })).filter((g) => g.itens.length > 0);

  return (
    <View style={styles.screen}>
      <ScreenHeader title="Notificações" />
      <ScrollView
        contentContainerStyle={[styles.content, itens.length === 0 && styles.vazioContent]}
        refreshControl={
          <RefreshControl refreshing={atualizando} onRefresh={aoAtualizar} tintColor={Brand.brand} colors={[Brand.brand]} />
        }>
        {carregando ? (
          <View style={styles.estado}>
            <ActivityIndicator color={Brand.brand} />
            <Text style={styles.estadoTxt}>Carregando notificações…</Text>
          </View>
        ) : erro ? (
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
        ) : itens.length === 0 ? (
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="notifications-off-outline" size={26} color={Brand.muted} />
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
                    <View style={[styles.icon, { backgroundColor: e.bg }]}>
                      <Ionicons name={e.icon as never} size={18} color={e.fg} />
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

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  content: { padding: 16, paddingBottom: 40 },
  vazioContent: { flexGrow: 1 },
  grupo: { marginBottom: 20 },
  grupoTitulo: {
    fontSize: 12,
    fontWeight: '700',
    color: Brand.muted,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginBottom: 10,
    marginLeft: 4,
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    backgroundColor: Brand.surface,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 14,
    marginBottom: 10,
  },
  itemNaoLido: { borderColor: '#CDE9E1', backgroundColor: '#F6FCFA' },
  itemPressed: { backgroundColor: '#EEF3F1' },
  icon: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  itemTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  titulo: { flex: 1, fontSize: 14.5, fontWeight: '700', color: Brand.ink },
  tituloForte: { fontWeight: '800' },
  hora: { fontSize: 11.5, color: Brand.muted },
  descricao: { fontSize: 13, color: '#40514C', marginTop: 2, lineHeight: 18 },
  dot: { width: 9, height: 9, borderRadius: 5, backgroundColor: Brand.brand },

  estado: { alignItems: 'center', justifyContent: 'center', paddingVertical: 60, gap: 10 },
  estadoIcone: {
    width: 56, height: 56, borderRadius: 18, backgroundColor: Brand.bg,
    borderWidth: 1, borderColor: Brand.line, alignItems: 'center', justifyContent: 'center',
  },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: Brand.ink, marginTop: 2 },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center', paddingHorizontal: 32, lineHeight: 19 },
  estadoBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 7, marginTop: 6, height: 44,
    paddingHorizontal: 18, borderRadius: 14, backgroundColor: Brand.brand,
  },
  estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
});
