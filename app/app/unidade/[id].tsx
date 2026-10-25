import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Linking,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { MapaUnidade } from '@/components/mapa-unidade';
import { alpha, type Tema, useTema } from '@/hooks/use-tema';
import { buscarPerfilUnidade, DiaSemana, HorarioUnidade, PerfilUnidade } from '@/services/unidade';

/** Rótulo pt-BR de cada dia (na ordem de exibição segunda → domingo). */
const DIAS: { dia: DiaSemana; label: string }[] = [
  { dia: 'MONDAY', label: 'Segunda-feira' },
  { dia: 'TUESDAY', label: 'Terça-feira' },
  { dia: 'WEDNESDAY', label: 'Quarta-feira' },
  { dia: 'THURSDAY', label: 'Quinta-feira' },
  { dia: 'FRIDAY', label: 'Sexta-feira' },
  { dia: 'SATURDAY', label: 'Sábado' },
  { dia: 'SUNDAY', label: 'Domingo' },
];
const LABEL = new Map(DIAS.map((d) => [d.dia, d.label]));
/** new Date().getDay() (0=dom … 6=sáb) → DiaSemana do backend. */
const POR_GETDAY: DiaSemana[] = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/);
  const a = partes[0]?.charAt(0) ?? '';
  const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
  return (a + b).toUpperCase();
}

/** "09:00:00" → "09:00". */
function hhmm(t?: string | null): string {
  return t ? t.slice(0, 5) : '';
}

/** Faixa do dia: "Fechada", "Aberta 24 horas" ou "09:00 – 18:00". */
function faixa(h?: HorarioUnidade): string {
  if (!h || h.fechado) return 'Fechada';
  if (h.vinteQuatroHoras) return 'Aberta 24 horas';
  if (!h.abertura || !h.fechamento) return 'Fechada';
  return `${hhmm(h.abertura)} – ${hhmm(h.fechamento)}`;
}

/** Está aberto AGORA? (compara a hora atual com a faixa do dia de hoje; 24h = sempre aberto.) */
function abertoAgora(h?: HorarioUnidade): boolean {
  if (!h || h.fechado) return false;
  if (h.vinteQuatroHoras) return true;
  if (!h.abertura || !h.fechamento) return false;
  const agora = new Date();
  const hm = `${String(agora.getHours()).padStart(2, '0')}:${String(agora.getMinutes()).padStart(2, '0')}`;
  return hhmm(h.abertura) <= hm && hm <= hhmm(h.fechamento);
}

export default function UnidadePerfilScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const insets = useSafeAreaInsets();
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();

  const [unidade, setUnidade] = useState<PerfilUnidade | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);

  const carregar = useCallback(async () => {
    if (!id) return;
    try {
      setErro(false);
      setUnidade(await buscarPerfilUnidade(id));
    } catch {
      setErro(true);
    } finally {
      setCarregando(false);
    }
  }, [id]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  const abrir = (url: string) => Linking.openURL(url).catch(() => {});
  const ligar = () => unidade?.telefone && abrir(`tel:${unidade.telefone.replace(/\s+/g, '')}`);
  const comoChegar = () =>
    unidade?.endereco &&
    abrir(`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(unidade.endereco)}`);

  // Horário de hoje em destaque (resumo + "aberto agora").
  const hoje = POR_GETDAY[new Date().getDay()];
  const horarioHoje = unidade?.horarios?.find((h) => h.diaSemana === hoje);
  const aberto = abertoAgora(horarioHoje);
  // Lista ordenada segunda→domingo (o backend já manda assim, mas garantimos).
  const horariosOrdenados = useMemo(
    () =>
      DIAS.map((d) => unidade?.horarios?.find((h) => h.diaSemana === d.dia)).filter(
        (h): h is HorarioUnidade => !!h,
      ),
    [unidade],
  );
  const temHorario = horariosOrdenados.length > 0;
  // Sempre aberta = todos os dias 24h (resumo mostra "sempre aberta" no lugar da faixa de hoje).
  const sempreAberta = temHorario && horariosOrdenados.every((h) => h.vinteQuatroHoras);
  // Mostra o mapa só quando o backend geocodificou o endereço.
  const temMapa = typeof unidade?.latitude === 'number' && typeof unidade?.longitude === 'number';

  const header = (
    <View style={[styles.header, { paddingTop: insets.top + 6 }]}>
      <Pressable
        style={({ pressed }) => [styles.back, pressed && styles.backPressed]}
        onPress={() => router.back()}
        hitSlop={10}
        accessibilityRole="button"
        accessibilityLabel="Voltar">
        <Ionicons name="chevron-back" size={26} color={t.ink} />
      </Pressable>
      <Text style={styles.headerTitulo} numberOfLines={1}>
        Dados da unidade
      </Text>
    </View>
  );

  if (carregando) {
    return (
      <View style={styles.screen}>
        {header}
        <View style={styles.estado}>
          <ActivityIndicator color={t.brand} />
        </View>
      </View>
    );
  }

  if (erro || !unidade) {
    return (
      <View style={styles.screen}>
        {header}
        <View style={styles.estado}>
          <Ionicons name="cloud-offline-outline" size={28} color={t.muted} />
          <Text style={styles.estadoTxt}>Não foi possível abrir a unidade</Text>
          <Pressable style={styles.estadoBtn} onPress={carregar}>
            <Ionicons name="refresh" size={16} color="#fff" />
            <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
          </Pressable>
        </View>
      </View>
    );
  }

  return (
    <View style={styles.screen}>
      {header}
      <ScrollView contentContainerStyle={{ paddingBottom: insets.bottom + 28 }} showsVerticalScrollIndicator={false}>
        {/* Capa + logo sobreposta (estilo perfil de empresa do WhatsApp). */}
        <View style={styles.capaWrap}>
          {unidade.capaUrl ? (
            <Image source={{ uri: unidade.capaUrl }} style={styles.capa} resizeMode="cover" />
          ) : (
            <View style={[styles.capa, styles.capaVazia]} />
          )}
          <View style={styles.logoWrap}>
            {unidade.logoUrl ? (
              <Image source={{ uri: unidade.logoUrl }} style={styles.logo} resizeMode="cover" />
            ) : (
              <View style={[styles.logo, styles.logoVazia]}>
                <Text style={styles.logoIniciais}>{iniciais(unidade.nome)}</Text>
              </View>
            )}
          </View>
        </View>

        <Text style={styles.nome}>{unidade.nome}</Text>

        {/* Ações: Ligar / Como chegar. */}
        {(unidade.telefone || unidade.endereco) && (
          <View style={styles.acoes}>
            {unidade.telefone ? (
              <Pressable
                style={({ pressed }) => [styles.acao, pressed && styles.acaoPressed]}
                onPress={ligar}
                accessibilityRole="button"
                accessibilityLabel="Ligar para a unidade">
                <Ionicons name="call" size={22} color={t.brandDeep} />
                <Text style={styles.acaoTxt}>Ligar</Text>
              </Pressable>
            ) : null}
            {unidade.endereco ? (
              <Pressable
                style={({ pressed }) => [styles.acao, pressed && styles.acaoPressed]}
                onPress={comoChegar}
                accessibilityRole="button"
                accessibilityLabel="Como chegar à unidade">
                <Ionicons name="navigate" size={22} color={t.brandDeep} />
                <Text style={styles.acaoTxt}>Como chegar</Text>
              </Pressable>
            ) : null}
          </View>
        )}

        {/* Sobre. */}
        {unidade.sobre ? (
          <View style={styles.card}>
            <Text style={styles.sobre}>{unidade.sobre}</Text>
          </View>
        ) : null}

        {/* Horário de funcionamento: sempre aberto, todos os dias à vista (sem precisar tocar). */}
        {temHorario ? (
          <View style={styles.card}>
            <View style={styles.horarioHead}>
              <Ionicons name="time-outline" size={20} color={t.muted} style={styles.horarioIcone} />
              <View style={styles.horarioResumoLinha}>
                <Text style={[styles.horarioStatus, { color: aberto ? t.brandDeep : '#B4453E' }]}>
                  {aberto ? 'Aberto agora' : 'Fechado agora'}
                </Text>
                {sempreAberta ? <Text style={styles.horarioHoje}>· sempre aberta</Text> : null}
              </View>
            </View>

            <View style={styles.horarioLista}>
              {horariosOrdenados.map((h) => {
                const ehHoje = h.diaSemana === hoje;
                return (
                  <View key={h.diaSemana} style={styles.horarioLinha}>
                    <Text style={[styles.horarioDia, ehHoje && styles.horarioDiaHoje]}>
                      {LABEL.get(h.diaSemana)}
                    </Text>
                    <Text style={[styles.horarioFaixa, ehHoje && styles.horarioDiaHoje]}>{faixa(h)}</Text>
                  </View>
                );
              })}
            </View>
          </View>
        ) : null}

        {/* Endereço: miniatura do mapa (estilo WhatsApp) + linha do endereço; toca → Como chegar. */}
        {unidade.endereco ? (
          <Pressable
            style={({ pressed }) => [styles.enderecoCard, pressed && styles.acaoPressed]}
            onPress={comoChegar}
            accessibilityRole="button"
            accessibilityLabel="Abrir endereço no mapa">
            {temMapa ? <MapaUnidade latitude={unidade.latitude!} longitude={unidade.longitude!} /> : null}
            <View style={styles.enderecoRow}>
              <Ionicons name="location-outline" size={20} color={t.brandDeep} style={styles.horarioIcone} />
              <Text style={styles.enderecoTxt}>{unidade.endereco}</Text>
              <Ionicons name="chevron-forward" size={18} color={t.muted} />
            </View>
          </Pressable>
        ) : null}
      </ScrollView>
    </View>
  );
}

function criarEstilos(t: Tema) {
  return StyleSheet.create({
    screen: { flex: 1, backgroundColor: '#F2F3F5' },
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 6,
      paddingHorizontal: 10,
      paddingBottom: 10,
      backgroundColor: t.surface,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: alpha(t.ink, 0.1),
    },
    back: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
    backPressed: { backgroundColor: alpha(t.ink, 0.06) },
    headerTitulo: { flex: 1, fontSize: 18, fontWeight: '700', color: t.ink },

    estado: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, padding: 24 },
    estadoTxt: { fontSize: 14, color: t.muted },
    estadoBtn: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 6,
      backgroundColor: t.brand,
      paddingHorizontal: 16,
      paddingVertical: 10,
      borderRadius: 10,
      marginTop: 4,
    },
    estadoBtnTxt: { color: '#fff', fontWeight: '600' },

    // Capa + logo
    capaWrap: { backgroundColor: t.surface, alignItems: 'center', paddingBottom: 54 },
    capa: { width: '100%', height: 180, backgroundColor: alpha(t.brand, 0.1) },
    capaVazia: { backgroundColor: alpha(t.brand, 0.14) },
    logoWrap: {
      position: 'absolute',
      bottom: 0,
      alignSelf: 'center',
      width: 104,
      height: 104,
      borderRadius: 52,
      backgroundColor: t.surface,
      padding: 4,
    },
    logo: { width: 96, height: 96, borderRadius: 48, backgroundColor: alpha(t.brand, 0.12) },
    logoVazia: { alignItems: 'center', justifyContent: 'center' },
    logoIniciais: { fontSize: 32, fontWeight: '700', color: t.brandDeep },

    nome: { fontSize: 22, fontWeight: '700', color: t.ink, textAlign: 'center', marginTop: 14, paddingHorizontal: 24 },

    // Ações
    acoes: { flexDirection: 'row', justifyContent: 'center', gap: 12, marginTop: 18, paddingHorizontal: 16 },
    acao: {
      flex: 1,
      maxWidth: 160,
      alignItems: 'center',
      gap: 6,
      backgroundColor: t.surface,
      paddingVertical: 14,
      borderRadius: 14,
    },
    acaoPressed: { opacity: 0.6 },
    acaoTxt: { fontSize: 13, fontWeight: '600', color: t.brandDeep },

    // Cards
    card: {
      backgroundColor: t.surface,
      marginTop: 14,
      marginHorizontal: 12,
      borderRadius: 14,
      paddingHorizontal: 16,
      paddingVertical: 14,
    },
    sobre: { fontSize: 15, color: t.ink, lineHeight: 22 },

    // Horário
    horarioHead: { flexDirection: 'row', alignItems: 'center', gap: 10 },
    horarioIcone: { marginTop: 1 },
    horarioResumoLinha: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap' },
    horarioStatus: { fontSize: 15, fontWeight: '700' },
    horarioHoje: { fontSize: 14, color: t.muted, marginLeft: 5 },
    horarioLista: { marginTop: 12, gap: 8, paddingLeft: 30 },
    horarioLinha: { flexDirection: 'row', justifyContent: 'space-between' },
    horarioDia: { fontSize: 14, color: t.ink },
    horarioFaixa: { fontSize: 14, color: t.muted },
    horarioDiaHoje: { fontWeight: '700', color: t.ink },

    // Endereço (card com mapa em cima + linha do endereço)
    enderecoCard: {
      backgroundColor: t.surface,
      marginTop: 14,
      marginHorizontal: 12,
      borderRadius: 14,
      overflow: 'hidden',
    },
    enderecoRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 16, paddingVertical: 14 },
    enderecoTxt: { flex: 1, fontSize: 15, color: t.ink, lineHeight: 21 },
  });
}
