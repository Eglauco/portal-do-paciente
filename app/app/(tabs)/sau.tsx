import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useRouter } from 'expo-router';
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
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Brand } from '@/constants/theme';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import { ManifestacaoItem, StatusManifestacao, listarManifestacoes } from '@/services/sau';

const doisDigitos = (n: number) => String(n).padStart(2, '0');

function horaLista(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  const agora = new Date();
  if (d.toDateString() === agora.toDateString()) return `${doisDigitos(d.getHours())}:${doisDigitos(d.getMinutes())}`;
  const ontem = new Date(agora);
  ontem.setDate(agora.getDate() - 1);
  if (d.toDateString() === ontem.toDateString()) return 'Ontem';
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}`;
}

// Cor neutra da marca para o tipo (o nome é livre, vem do cadastro).
const COR_TIPO = { fg: Brand.brandDeep, bg: '#E3F1EC', icone: 'chatbox-ellipses-outline' as const };

const CORES_STATUS: Record<StatusManifestacao, { fg: string; bg: string }> = {
  AGUARDANDO_SAU: { fg: '#A5741A', bg: '#FBF0D6' },
  AGUARDANDO_PACIENTE: { fg: '#2F6DF6', bg: '#E9F0FE' },
  FECHADA: { fg: '#566863', bg: '#ECEEF1' },
};

export default function SauScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [itens, setItens] = useState<ManifestacaoItem[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [atualizando, setAtualizando] = useState(false);
  const jaCarregou = useRef(false);

  const carregar = useCallback(async (mostrarSpinner: boolean) => {
    try {
      if (mostrarSpinner) setCarregando(true);
      setErro(false);
      const dados = await listarManifestacoes();
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

  const abrir = (m: ManifestacaoItem) => {
    router.push({ pathname: '/sau/[id]', params: { id: String(m.id) } });
  };

  return (
    <View style={styles.screen}>
      <ScrollView
        style={styles.lista}
        contentContainerStyle={[
          { paddingBottom: 96 + insets.bottom },
          itens.length === 0 && styles.listaVaziaContent,
        ]}
        refreshControl={
          <RefreshControl refreshing={atualizando} onRefresh={aoAtualizar} tintColor={Brand.brand} colors={[Brand.brand]} />
        }>
        {carregando ? (
          <View style={styles.estado}>
            <ActivityIndicator color={Brand.brand} />
            <Text style={styles.estadoTxt}>Carregando manifestações…</Text>
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
              <Ionicons name="chatbox-ellipses-outline" size={26} color={Brand.muted} />
            </View>
            <Text style={styles.estadoTitulo}>Nenhuma manifestação</Text>
            <Text style={styles.estadoTxt}>
              Envie um elogio, crítica ou sugestão para o Serviço de Atendimento ao Usuário.
            </Text>
            <Pressable style={styles.estadoBtn} onPress={() => router.push('/sau/nova')}>
              <Ionicons name="add" size={18} color="#fff" />
              <Text style={styles.estadoBtnTxt}>Nova manifestação</Text>
            </Pressable>
          </View>
        ) : (
          itens.map((m) => {
            const tipo = COR_TIPO;
            const status = CORES_STATUS[m.status];
            const respostaSau = m.ultimaMensagemDe === 'SAU';
            const prefixo = m.ultimaMensagemDe === 'PACIENTE' ? 'Você: ' : respostaSau ? 'SAU: ' : '';
            return (
              <Pressable
                key={m.id}
                onPress={() => abrir(m)}
                style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}>
                <View style={[styles.avatar, { backgroundColor: tipo.bg }]}>
                  <Ionicons name={tipo.icone as never} size={22} color={tipo.fg} />
                </View>
                <View style={styles.rowBody}>
                  <View style={styles.rowTop}>
                    <Text style={styles.nome} numberOfLines={1}>{m.unidadeSaude.nome}</Text>
                    <Text style={styles.hora}>{horaLista(m.atualizadoEm)}</Text>
                  </View>
                  <View style={styles.pills}>
                    <View style={[styles.pill, { backgroundColor: tipo.bg }]}>
                      <Text style={[styles.pillTxt, { color: tipo.fg }]}>{m.tipo.nome}</Text>
                    </View>
                    <View style={[styles.pill, { backgroundColor: status.bg }]}>
                      <Text style={[styles.pillTxt, { color: status.fg }]}>{m.statusDescricao}</Text>
                    </View>
                  </View>
                  <Text style={[styles.previa, respostaSau && styles.previaForte]} numberOfLines={1}>
                    {prefixo}
                    {m.ultimaMensagem ?? ''}
                  </Text>
                </View>
              </Pressable>
            );
          })
        )}
      </ScrollView>

      {/* Botão flutuante: nova manifestação */}
      <Pressable
        style={({ pressed }) => [styles.fab, pressed && styles.fabPressed]}
        onPress={() => router.push('/sau/nova')}
        accessibilityRole="button"
        accessibilityLabel="Nova manifestação">
        <Ionicons name="add" size={26} color="#fff" />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.surface },
  lista: { flex: 1 },
  listaVaziaContent: { flexGrow: 1 },

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
  avatar: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center' },
  rowBody: { flex: 1, justifyContent: 'center', gap: 5 },
  rowTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  nome: { flex: 1, fontSize: 15.5, fontWeight: '700', color: Brand.ink },
  hora: { fontSize: 12, color: Brand.muted },
  pills: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  pill: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 20 },
  pillTxt: { fontSize: 11, fontWeight: '700' },
  previa: { fontSize: 13.5, color: Brand.muted },
  previaForte: { color: '#40514C', fontWeight: '600' },

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
});
