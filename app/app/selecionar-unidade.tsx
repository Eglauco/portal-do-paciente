import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { alpha, type Tema, useTema } from '@/hooks/use-tema';
import { useSessao } from '@/hooks/use-sessao';
import { type ClinicaLogin } from '@/services/sessao';

/**
 * Tela "Trocar unidade" (app compartilhado): lista as clínicas do CPF logado com a mesma apresentação
 * da seleção de clínica do login e da tela "Selecionar perfil". Ao escolher, troca a clínica da sessão
 * (reemite o token com o novo inquilino); como a sessão volta a "perfil não escolhido", o guard de
 * navegação (_layout) leva para "Selecionar perfil" da nova unidade. É opcional: "Cancelar" volta ao perfil.
 */
export default function SelecionarUnidadeScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { minhasClinicas, trocarClinica } = useSessao();

  const [clinicas, setClinicas] = useState<ClinicaLogin[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [trocandoId, setTrocandoId] = useState<number | null>(null);

  function carregar() {
    setCarregando(true);
    setErro(false);
    minhasClinicas()
      .then((lista) => setClinicas(lista))
      .catch(() => setErro(true))
      .finally(() => setCarregando(false));
  }

  useEffect(() => {
    let vivo = true;
    minhasClinicas()
      .then((lista) => {
        if (vivo) setClinicas(lista);
      })
      .catch(() => {
        if (vivo) setErro(true);
      })
      .finally(() => {
        if (vivo) setCarregando(false);
      });
    return () => {
      vivo = false;
    };
    // Carrega uma vez ao abrir a tela.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function escolher(inquilinoId: number) {
    if (trocandoId != null) return;
    setTrocandoId(inquilinoId);
    try {
      await trocarClinica(inquilinoId);
      // Não navega aqui: o guard de navegação (_layout) vê perfilSelecionado=false e leva para
      // "Selecionar perfil" da nova unidade, substituindo esta tela.
    } catch (e) {
      setTrocandoId(null);
      Alert.alert('Não foi possível trocar de unidade', e instanceof Error ? e.message : 'Tente novamente.');
    }
  }

  return (
    <View style={styles.root}>
      <SafeAreaView edges={['top']} style={styles.brand}>
        <View style={styles.mark}>
          <Ionicons name="business" size={22} color={t.glow} />
        </View>
        <Text style={styles.titulo}>Trocar unidade</Text>
        <Text style={styles.subtitulo}>Escolha a unidade de saúde que deseja acessar.</Text>
      </SafeAreaView>

      <View style={styles.sheet}>
        {carregando ? (
          <View style={styles.estado}>
            <ActivityIndicator color={t.brand} />
            <Text style={styles.estadoTxt}>Carregando unidades…</Text>
          </View>
        ) : erro ? (
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="cloud-offline-outline" size={26} color={t.muted} />
            </View>
            <Text style={styles.estadoTitulo}>Não foi possível carregar</Text>
            <Text style={styles.estadoTxt}>Verifique sua conexão e tente novamente.</Text>
            <Pressable
              style={styles.estadoBtn}
              accessibilityRole="button"
              accessibilityLabel="Tentar novamente"
              onPress={carregar}>
              <Ionicons name="refresh" size={16} color="#fff" />
              <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
            </Pressable>
          </View>
        ) : clinicas.length === 0 ? (
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="business-outline" size={26} color={t.muted} />
            </View>
            <Text style={styles.estadoTitulo}>Nenhuma unidade disponível</Text>
            <Text style={styles.estadoTxt}>Seu CPF não está vinculado a outra unidade.</Text>
          </View>
        ) : (
          <ScrollView
            style={styles.listaScroll}
            contentContainerStyle={styles.lista}
            showsVerticalScrollIndicator={false}>
            {clinicas.map((c) => {
              const trocando = trocandoId === c.inquilinoId;
              return (
                <Pressable
                  key={c.inquilinoId}
                  style={({ pressed }) => [styles.card, pressed && styles.cardPressed, trocando && styles.cardBusy]}
                  onPress={() => escolher(c.inquilinoId)}
                  disabled={trocandoId != null}
                  accessibilityRole="button"
                  accessibilityLabel={`Entrar na ${c.nome}`}
                  accessibilityState={{ busy: trocando, disabled: trocandoId != null }}>
                  <View style={styles.icone}>
                    <Ionicons name="business-outline" size={20} color={t.brandDeep} />
                  </View>
                  <Text style={styles.cardNome} numberOfLines={2}>
                    {c.nome}
                  </Text>
                  {trocando ? (
                    <ActivityIndicator size="small" color={t.brand} />
                  ) : (
                    <Ionicons name="chevron-forward" size={20} color={t.muted} />
                  )}
                </Pressable>
              );
            })}
          </ScrollView>
        )}

        <View style={[styles.cancelarWrap, { paddingBottom: insets.bottom + 12 }]}>
          <Pressable
            style={({ pressed }) => [styles.cancelar, pressed && styles.cancelarPressed]}
            onPress={() => router.back()}
            disabled={trocandoId != null}
            accessibilityRole="button"
            accessibilityLabel="Cancelar">
            <Ionicons name="close" size={20} color={t.brandDeep} />
            <Text style={styles.cancelarTxt}>Cancelar</Text>
          </Pressable>
        </View>
      </View>
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    root: { flex: 1, backgroundColor: t.brandDeep },
    brand: { paddingHorizontal: 28, paddingTop: 8, paddingBottom: 28 },
    mark: {
      width: 44,
      height: 44,
      borderRadius: 14,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: 'rgba(255,255,255,0.10)',
      borderWidth: 1,
      borderColor: alpha(t.onBrand, 0.28),
      marginBottom: 16,
    },
    titulo: { color: t.onBrand, fontSize: 26, fontWeight: '700', letterSpacing: -0.3 },
    subtitulo: { color: alpha(t.onBrand, 0.72), fontSize: 14.5, marginTop: 6, lineHeight: 20 },
    sheet: {
      flex: 1,
      backgroundColor: t.surface,
      borderTopLeftRadius: 28,
      borderTopRightRadius: 28,
      marginTop: -8,
      paddingTop: 22,
    },
    listaScroll: { flex: 1 },
    lista: { paddingHorizontal: 20, paddingBottom: 24, gap: 12 },
    card: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 14,
      backgroundColor: t.surface,
      borderRadius: 18,
      borderWidth: 1,
      borderColor: t.line,
      paddingHorizontal: 14,
      paddingVertical: 16,
    },
    cardPressed: { backgroundColor: t.brandTint },
    cardBusy: { opacity: 0.7 },
    icone: {
      width: 48,
      height: 48,
      borderRadius: 24,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: t.brandTint,
    },
    cardNome: { flex: 1, minWidth: 0, fontSize: 16.5, fontWeight: '700', color: t.ink },
    estado: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32, gap: 10 },
    estadoIcone: {
      width: 56,
      height: 56,
      borderRadius: 18,
      backgroundColor: t.bg,
      borderWidth: 1,
      borderColor: t.line,
      alignItems: 'center',
      justifyContent: 'center',
    },
    estadoTitulo: { fontSize: 16, fontWeight: '800', color: t.ink, marginTop: 2 },
    estadoTxt: { fontSize: 13.5, color: t.muted, textAlign: 'center', lineHeight: 19 },
    estadoBtn: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 7,
      marginTop: 6,
      height: 44,
      paddingHorizontal: 18,
      borderRadius: 14,
      backgroundColor: t.brand,
    },
    estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
    cancelarWrap: { paddingHorizontal: 20, paddingTop: 12 },
    cancelar: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 8,
      backgroundColor: t.surface,
      borderRadius: 14,
      borderWidth: 1,
      borderColor: t.line,
      paddingVertical: 15,
    },
    cancelarPressed: { backgroundColor: t.brandTint },
    cancelarTxt: { fontSize: 15, fontWeight: '700', color: t.brandDeep },
  });
