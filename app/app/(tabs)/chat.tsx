import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { Brand } from '@/constants/theme';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import { ChatItem, listarChats } from '@/services/chat';
import { observarLista } from '@/services/chat-realtime';

const doisDigitos = (n: number) => String(n).padStart(2, '0');

/** Rótulo curto de tempo para a lista (estilo WhatsApp). */
function horaLista(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  const agora = new Date();
  const mesmoDia = d.toDateString() === agora.toDateString();
  if (mesmoDia) return `${doisDigitos(d.getHours())}:${doisDigitos(d.getMinutes())}`;

  const ontem = new Date(agora);
  ontem.setDate(agora.getDate() - 1);
  if (d.toDateString() === ontem.toDateString()) return 'Ontem';

  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}`;
}

function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/);
  const a = partes[0]?.charAt(0) ?? '';
  const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
  return (a + b).toUpperCase();
}

export default function ChatScreen() {
  const router = useRouter();
  const [chats, setChats] = useState<ChatItem[]>([]);
  const [busca, setBusca] = useState('');
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [atualizando, setAtualizando] = useState(false);
  const jaCarregou = useRef(false);

  const carregar = useCallback(async (mostrarSpinner: boolean) => {
    try {
      if (mostrarSpinner) setCarregando(true);
      setErro(false);
      const dados = await listarChats();
      setChats(dados);
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

  // Atualiza a lista em tempo real quando chega qualquer mensagem nova.
  useEffect(() => {
    const cancelar = observarLista(() => carregar(false));
    return cancelar;
  }, [carregar]);

  const aoAtualizar = () => {
    setAtualizando(true);
    carregar(false);
  };

  const filtrados = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    if (!termo) return chats;
    return chats.filter(
      (c) =>
        c.unidadeSaude.nome.toLowerCase().includes(termo) ||
        (c.ultimaMensagem ?? '').toLowerCase().includes(termo),
    );
  }, [chats, busca]);

  const abrir = (chat: ChatItem) => {
    router.push({ pathname: '/conversa/[id]', params: { id: String(chat.id) } });
  };

  return (
    <View style={styles.screen}>
      {/* Busca */}
      <View style={styles.buscaWrap}>
        <View style={styles.busca}>
          <Ionicons name="search" size={18} color={Brand.muted} />
          <TextInput
            style={styles.buscaInput}
            value={busca}
            onChangeText={setBusca}
            placeholder="Pesquisar conversa"
            placeholderTextColor="#9AAAA5"
            returnKeyType="search"
          />
          {busca.length > 0 && (
            <Pressable onPress={() => setBusca('')} accessibilityLabel="Limpar busca">
              <Ionicons name="close-circle" size={18} color={Brand.muted} />
            </Pressable>
          )}
        </View>
      </View>

      <ScrollView
        style={styles.lista}
        contentContainerStyle={[styles.listaContent, filtrados.length === 0 && styles.listaVaziaContent]}
        refreshControl={
          <RefreshControl refreshing={atualizando} onRefresh={aoAtualizar} tintColor={Brand.brand} colors={[Brand.brand]} />
        }>
        {carregando ? (
          <View style={styles.estado}>
            <ActivityIndicator color={Brand.brand} />
            <Text style={styles.estadoTxt}>Carregando conversas…</Text>
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
        ) : filtrados.length === 0 ? (
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="chatbubbles-outline" size={26} color={Brand.muted} />
            </View>
            <Text style={styles.estadoTitulo}>{busca ? 'Nada encontrado' : 'Nenhuma conversa'}</Text>
            <Text style={styles.estadoTxt}>
              {busca ? 'Tente outro termo de busca.' : 'Suas conversas com as unidades aparecerão aqui.'}
            </Text>
          </View>
        ) : (
          filtrados.map((c) => {
            const novaResposta = c.ultimaMensagemDe === 'UNIDADE' && c.status !== 'RESOLVIDO';
            const prefixo = c.ultimaMensagemDe === 'PACIENTE' ? 'Você: ' : '';
            return (
              <Pressable
                key={c.id}
                onPress={() => abrir(c)}
                style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}>
                <View style={styles.avatar}>
                  <Text style={styles.avatarTxt}>{iniciais(c.unidadeSaude.nome)}</Text>
                </View>
                <View style={styles.rowBody}>
                  <View style={styles.rowTop}>
                    <Text style={[styles.nome, novaResposta && styles.nomeForte]} numberOfLines={1}>
                      {c.unidadeSaude.nome}
                    </Text>
                    <Text style={[styles.hora, novaResposta && styles.horaForte]}>
                      {horaLista(c.ultimaMensagemEm ?? c.atualizadoEm)}
                    </Text>
                  </View>
                  <View style={styles.rowBottom}>
                    <Text style={[styles.previa, novaResposta && styles.previaForte]} numberOfLines={1}>
                      {prefixo}
                      {c.ultimaMensagem ?? 'Iniciar conversa'}
                    </Text>
                    {novaResposta && <View style={styles.dot} />}
                  </View>
                </View>
              </Pressable>
            );
          })
        )}
      </ScrollView>

      {/* Botão flutuante: iniciar uma nova conversa */}
      <Pressable
        style={({ pressed }) => [styles.fab, pressed && styles.fabPressed]}
        onPress={() => router.push('/conversa/nova')}
        accessibilityRole="button"
        accessibilityLabel="Nova conversa">
        <Ionicons name="add" size={26} color="#fff" />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.surface },

  buscaWrap: { paddingHorizontal: 14, paddingTop: 10, paddingBottom: 6, backgroundColor: Brand.surface },
  busca: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: Brand.bg,
    borderRadius: 22,
    borderWidth: 1,
    borderColor: Brand.line,
    paddingHorizontal: 14,
    height: 42,
  },
  buscaInput: { flex: 1, fontSize: 15, color: Brand.ink },

  lista: { flex: 1 },
  listaContent: { paddingBottom: 92 },
  listaVaziaContent: { flexGrow: 1 },

  fab: {
    position: 'absolute',
    right: 20,
    bottom: 20,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: Brand.brand,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
    elevation: 5,
  },
  fabPressed: { opacity: 0.85 },

  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F0F4F2',
  },
  rowPressed: { backgroundColor: '#EEF3F1' },
  avatar: {
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.brandDeep,
  },
  avatarTxt: { color: Brand.onBrand, fontSize: 16, fontWeight: '800' },
  rowBody: { flex: 1, justifyContent: 'center', gap: 4 },
  rowTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  nome: { flex: 1, fontSize: 15.5, fontWeight: '600', color: Brand.ink },
  nomeForte: { fontWeight: '800' },
  hora: { fontSize: 12, color: Brand.muted },
  horaForte: { color: Brand.brand, fontWeight: '700' },
  rowBottom: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  previa: { flex: 1, fontSize: 13.5, color: Brand.muted },
  previaForte: { color: '#40514C', fontWeight: '600' },
  dot: { width: 10, height: 10, borderRadius: 5, backgroundColor: Brand.brand },

  // Estados
  estado: { alignItems: 'center', justifyContent: 'center', paddingVertical: 60, gap: 10 },
  estadoIcone: {
    width: 56,
    height: 56,
    borderRadius: 18,
    backgroundColor: Brand.bg,
    borderWidth: 1,
    borderColor: Brand.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: Brand.ink, marginTop: 2 },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center', paddingHorizontal: 32, lineHeight: 19 },
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
});
