import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Brand } from '@/constants/theme';
import { UnidadeRef, abrirConversa, listarUnidadesChat } from '@/services/chat';

export default function NovaConversaScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [unidades, setUnidades] = useState<UnidadeRef[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erroCarregar, setErroCarregar] = useState(false);
  const [abrindoId, setAbrindoId] = useState<number | null>(null);
  const [erroAbrir, setErroAbrir] = useState<string | null>(null);

  const carregar = async () => {
    try {
      setCarregando(true);
      setErroCarregar(false);
      setUnidades(await listarUnidadesChat());
    } catch {
      setErroCarregar(true);
    } finally {
      setCarregando(false);
    }
  };

  useEffect(() => {
    carregar();
  }, []);

  const abrir = async (u: UnidadeRef) => {
    if (abrindoId != null) return;
    try {
      setAbrindoId(u.id);
      setErroAbrir(null);
      const conversa = await abrirConversa(u.id);
      // Substitui esta tela pela conversa (se já existia, o back devolveu a mesma).
      router.replace({ pathname: '/conversa/[id]', params: { id: String(conversa.id) } });
    } catch {
      setErroAbrir('Não foi possível abrir a conversa agora. Tente novamente.');
      setAbrindoId(null);
    }
  };

  return (
    <View style={styles.screen}>
      {/* Cabeçalho */}
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Pressable
          style={({ pressed }) => [styles.back, pressed && styles.backPressed]}
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Voltar">
          <Ionicons name="chevron-back" size={26} color={Brand.ink} />
        </Pressable>
        <Text style={styles.headerTitulo}>Nova conversa</Text>
      </View>

      <ScrollView
        style={styles.corpo}
        contentContainerStyle={{ padding: 16, paddingBottom: 24 + insets.bottom }}>
        <Text style={styles.label}>Escolha a unidade para conversar</Text>

        {carregando ? (
          <View style={styles.estado}>
            <ActivityIndicator color={Brand.brand} />
            <Text style={styles.estadoTxt}>Carregando unidades…</Text>
          </View>
        ) : erroCarregar ? (
          <Pressable style={styles.erroBox} onPress={carregar}>
            <Ionicons name="refresh" size={16} color={Brand.brand} />
            <Text style={styles.erroBoxTxt}>Não foi possível carregar. Toque para tentar novamente.</Text>
          </Pressable>
        ) : unidades.length === 0 ? (
          <Text style={styles.vazioTxt}>Nenhuma unidade disponível no momento.</Text>
        ) : (
          <View style={styles.lista}>
            {unidades.map((u) => {
              const abrindo = abrindoId === u.id;
              return (
                <Pressable
                  key={u.id}
                  onPress={() => abrir(u)}
                  disabled={abrindoId != null}
                  style={({ pressed }) => [
                    styles.unidade,
                    pressed && styles.unidadePressed,
                    abrindoId != null && !abrindo && styles.unidadeDesativada,
                  ]}
                  accessibilityRole="button"
                  accessibilityLabel={`Conversar com ${u.nome}`}>
                  <View style={styles.unidadeIcone}>
                    <Ionicons name="business-outline" size={20} color={Brand.brandDeep} />
                  </View>
                  <Text style={styles.unidadeNome} numberOfLines={2}>{u.nome}</Text>
                  {abrindo ? (
                    <ActivityIndicator color={Brand.brand} />
                  ) : (
                    <Ionicons name="chevron-forward" size={20} color={Brand.muted} />
                  )}
                </Pressable>
              );
            })}
          </View>
        )}

        {erroAbrir && (
          <View style={styles.erroAbrir}>
            <Ionicons name="alert-circle" size={16} color="#B23B4E" />
            <Text style={styles.erroAbrirTxt}>{erroAbrir}</Text>
          </View>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 8,
    paddingBottom: 10,
    backgroundColor: Brand.surface,
    borderBottomWidth: 1,
    borderBottomColor: Brand.line,
  },
  back: { width: 38, height: 38, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  backPressed: { backgroundColor: '#EAF2EF' },
  headerTitulo: { fontSize: 17, fontWeight: '800', color: Brand.ink },

  corpo: { flex: 1 },
  label: { fontSize: 13, fontWeight: '700', color: Brand.muted, marginBottom: 12 },

  estado: { paddingVertical: 30, alignItems: 'center', gap: 8 },
  estadoTxt: { fontSize: 13, color: Brand.muted },
  vazioTxt: { fontSize: 13.5, color: Brand.muted, lineHeight: 19 },

  erroBox: {
    flexDirection: 'row', alignItems: 'center', gap: 8, padding: 14,
    backgroundColor: Brand.surface, borderRadius: 12, borderWidth: 1, borderColor: Brand.line,
  },
  erroBoxTxt: { flex: 1, fontSize: 13, color: Brand.muted },

  lista: { gap: 10 },
  unidade: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Brand.line,
    backgroundColor: Brand.surface,
  },
  unidadePressed: { backgroundColor: '#F1FAF7' },
  unidadeDesativada: { opacity: 0.5 },
  unidadeIcone: {
    width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center',
    backgroundColor: '#E3F1EC',
  },
  unidadeNome: { flex: 1, fontSize: 15, fontWeight: '600', color: Brand.ink },

  erroAbrir: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 16 },
  erroAbrirTxt: { flex: 1, fontSize: 13, color: '#B23B4E' },
});
