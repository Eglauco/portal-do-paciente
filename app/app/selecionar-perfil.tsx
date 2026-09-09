import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { alpha, type Tema, useTema } from '@/hooks/use-tema';
import { useSessao } from '@/hooks/use-sessao';
import { abaInicial, listarPerfis, type Perfil } from '@/services/sessao';

/** Iniciais do nome (fallback do avatar quando o perfil não tem foto). */
function iniciais(nome: string): string {
  const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) return '?';
  const a = partes[0].charAt(0);
  const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
  return (a + b).toUpperCase();
}

export default function SelecionarPerfilScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { sessao, trocarPerfil, sair } = useSessao();

  const [perfis, setPerfis] = useState<Perfil[]>(sessao?.perfis ?? []);
  const [carregando, setCarregando] = useState((sessao?.perfis?.length ?? 0) === 0);
  const [erro, setErro] = useState(false);
  const [selecionandoId, setSelecionandoId] = useState<number | null>(null);
  const [saindo, setSaindo] = useState(false);

  useEffect(() => {
    let vivo = true;
    (async () => {
      try {
        const lista = await listarPerfis();
        if (vivo) {
          setPerfis(lista);
          setErro(false);
        }
      } catch {
        // Se já temos perfis em cache, seguimos com eles; senão mostramos erro.
        if (vivo && (sessao?.perfis?.length ?? 0) === 0) setErro(true);
      } finally {
        if (vivo) setCarregando(false);
      }
    })();
    return () => {
      vivo = false;
    };
    // Executa uma vez ao abrir a tela.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function selecionar(pacienteId: number) {
    if (selecionandoId != null) return;
    setSelecionandoId(pacienteId);
    try {
      await trocarPerfil(pacienteId);
      // Limpa a pilha (ex.: veio de "Meu perfil") para não deixar telas do perfil anterior no Voltar.
      if (router.canDismiss()) router.dismissAll();
      // Aterrissa na primeira aba acessível do perfil ESCOLHIDO (o estado da sessão ainda
      // não reflete a troca aqui, então compõe o alvo com o pacienteId selecionado).
      router.replace(abaInicial(sessao ? { ...sessao, pacienteId } : null));
    } catch (e) {
      setSelecionandoId(null);
      Alert.alert('Não foi possível selecionar', e instanceof Error ? e.message : 'Tente novamente.');
    }
  }

  async function sairDaConta() {
    if (saindo || selecionandoId != null) return;
    setSaindo(true);
    await sair();
    router.replace('/');
  }

  return (
    <View style={styles.root}>
      <SafeAreaView edges={['top']} style={styles.brand}>
        <View style={styles.mark}>
          <Ionicons name="people" size={22} color={t.glow} />
        </View>
        <Text style={styles.titulo}>Selecionar perfil</Text>
        <Text style={styles.subtitulo}>Escolha por qual perfil você deseja continuar.</Text>
      </SafeAreaView>

      <View style={styles.sheet}>
        {carregando ? (
          <View style={styles.estado}>
            <ActivityIndicator color={t.brand} />
            <Text style={styles.estadoTxt}>Carregando perfis…</Text>
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
              onPress={() => {
                setCarregando(true);
                setErro(false);
                listarPerfis()
                  .then((l) => setPerfis(l))
                  .catch(() => setErro(true))
                  .finally(() => setCarregando(false));
              }}>
              <Ionicons name="refresh" size={16} color="#fff" />
              <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
            </Pressable>
          </View>
        ) : perfis.length === 0 ? (
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="people-outline" size={26} color={t.muted} />
            </View>
            <Text style={styles.estadoTitulo}>Nenhum perfil disponível</Text>
            <Text style={styles.estadoTxt}>
              Não há perfis vinculados a este telefone. Procure a sua unidade de saúde.
            </Text>
          </View>
        ) : (
          <ScrollView style={styles.listaScroll} contentContainerStyle={styles.lista} showsVerticalScrollIndicator={false}>
            {perfis.map((p) => {
              const escolhendo = selecionandoId === p.pacienteId;
              return (
                <Pressable
                  key={p.pacienteId}
                  style={({ pressed }) => [styles.card, pressed && styles.cardPressed, escolhendo && styles.cardBusy]}
                  onPress={() => selecionar(p.pacienteId)}
                  disabled={selecionandoId != null}
                  accessibilityRole="button"
                  accessibilityLabel={`Entrar como ${p.nome}${p.proprio ? ', você' : ', responsável'}`}
                  accessibilityState={{ busy: escolhendo, disabled: selecionandoId != null }}>
                  {p.fotoUrl ? (
                    <Image source={p.fotoUrl} style={styles.avatar} contentFit="cover" transition={150} />
                  ) : (
                    <View style={[styles.avatar, styles.avatarVazio]}>
                      <Text style={styles.avatarIniciais}>{iniciais(p.nome)}</Text>
                    </View>
                  )}
                  <View style={styles.cardBody}>
                    <Text style={styles.cardNome} numberOfLines={1}>
                      {p.nome}
                    </Text>
                    <View style={[styles.tag, p.proprio ? styles.tagVoce : styles.tagResp]}>
                      <Ionicons
                        name={p.proprio ? 'person' : 'people'}
                        size={11}
                        color={p.proprio ? t.brandDeep : '#8A5A00'}
                      />
                      <Text style={[styles.tagTxt, p.proprio ? styles.tagTxtVoce : styles.tagTxtResp]}>
                        {p.proprio ? 'Você' : 'Responsável'}
                      </Text>
                    </View>
                  </View>
                  {escolhendo ? (
                    <ActivityIndicator size="small" color={t.brand} />
                  ) : (
                    <Ionicons name="chevron-forward" size={20} color={t.muted} />
                  )}
                </Pressable>
              );
            })}
          </ScrollView>
        )}

        <View style={[styles.sairWrap, { paddingBottom: insets.bottom + 12 }]}>
          <Pressable
            style={({ pressed }) => [styles.sair, pressed && styles.sairPressed]}
            onPress={sairDaConta}
            disabled={saindo || selecionandoId != null}
            accessibilityRole="button"
            accessibilityLabel="Sair da conta">
            <Ionicons name="log-out-outline" size={20} color="#B23B4E" />
            <Text style={styles.sairTxt}>{saindo ? 'Saindo…' : 'Sair da conta'}</Text>
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
    paddingVertical: 14,
  },
  cardPressed: { backgroundColor: t.brandTint },
  cardBusy: { opacity: 0.7 },
  avatar: { width: 56, height: 56, borderRadius: 28, backgroundColor: t.line },
  avatarVazio: { alignItems: 'center', justifyContent: 'center', backgroundColor: t.brandTint },
  avatarIniciais: { fontSize: 18, fontWeight: '800', color: t.brandDeep },
  cardBody: { flex: 1, minWidth: 0, gap: 6 },
  cardNome: { fontSize: 16.5, fontWeight: '700', color: t.ink },
  tag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    alignSelf: 'flex-start',
    paddingHorizontal: 9,
    paddingVertical: 3,
    borderRadius: 20,
  },
  tagVoce: { backgroundColor: t.brandTint },
  tagResp: { backgroundColor: '#FBF1DE' },
  tagTxt: { fontSize: 11.5, fontWeight: '700' },
  tagTxtVoce: { color: t.brandDeep },
  tagTxtResp: { color: '#8A5A00' },
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
  sairWrap: { paddingHorizontal: 20, paddingTop: 12 },
  sair: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: t.surface,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#F3D6DB',
    paddingVertical: 15,
  },
  sairPressed: { backgroundColor: '#FDF2F3' },
  sairTxt: { fontSize: 15, fontWeight: '700', color: '#B23B4E' },
});
