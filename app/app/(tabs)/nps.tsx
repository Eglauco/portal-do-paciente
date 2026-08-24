import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { Brand, Nps } from '@/constants/theme';

interface Avaliacao {
  id: string;
  data: string;
  especialidade: string;
  profissional: string;
  unidade: string;
  nota: number;
}

const AVALIACOES: Avaliacao[] = [
  { id: '1', data: '18 ago 2026', especialidade: 'Clínico Geral', profissional: 'Dr. Paulo Nunes', unidade: 'Unidade 01', nota: 10 },
  { id: '2', data: '11 ago 2026', especialidade: 'Ortopedia', profissional: 'Dra. Mariana Duarte', unidade: 'Unidade 02', nota: 9 },
  { id: '3', data: '29 jul 2026', especialidade: 'Cardiologia', profissional: 'Dr. Rafael Lima', unidade: 'Unidade 01', nota: 8 },
  { id: '4', data: '15 jul 2026', especialidade: 'Dermatologia', profissional: 'Dra. Helena Costa', unidade: 'Unidade 03', nota: 7 },
  { id: '5', data: '02 jul 2026', especialidade: 'Oftalmologia', profissional: 'Dr. Carlos Mendes', unidade: 'Unidade 01', nota: 5 },
  { id: '6', data: '20 jun 2026', especialidade: 'Exame — Sangue', profissional: 'Laboratório central', unidade: 'Unidade 01', nota: 9 },
];

type Categoria = keyof typeof Nps;
const classificar = (nota: number): Categoria =>
  nota >= 9 ? 'promotor' : nota >= 7 ? 'neutro' : 'detrator';
const ROTULO: Record<Categoria, string> = {
  promotor: 'Promotor',
  neutro: 'Neutro',
  detrator: 'Detrator',
};

export default function NpsScreen() {
  const media = (AVALIACOES.reduce((s, a) => s + a.nota, 0) / AVALIACOES.length).toFixed(1);
  const contagem = AVALIACOES.reduce(
    (acc, a) => {
      acc[classificar(a.nota)] += 1;
      return acc;
    },
    { promotor: 0, neutro: 0, detrator: 0 } as Record<Categoria, number>,
  );

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Text style={styles.title}>NPS</Text>
      <Text style={styles.subtitle}>Sua avaliação dos atendimentos.</Text>

      {/* Resumo */}
      <View style={styles.resumo}>
        <View style={styles.mediaBox}>
          <Text style={styles.mediaNum}>{media}</Text>
          <Text style={styles.mediaLabel}>nota média</Text>
        </View>
        <View style={styles.divisor} />
        <View style={styles.dist}>
          {(['promotor', 'neutro', 'detrator'] as Categoria[]).map((c) => (
            <View key={c} style={styles.distRow}>
              <View style={[styles.distDot, { backgroundColor: Nps[c].fg }]} />
              <Text style={styles.distLabel}>{ROTULO[c]}</Text>
              <Text style={styles.distNum}>{contagem[c]}</Text>
            </View>
          ))}
        </View>
      </View>

      <Text style={styles.secao}>Atendimentos avaliados</Text>

      {AVALIACOES.map((a) => {
        const cat = classificar(a.nota);
        const cor = Nps[cat];
        return (
          <View key={a.id} style={styles.card}>
            <View style={[styles.nota, { backgroundColor: cor.bg }]}>
              <Text style={[styles.notaNum, { color: cor.fg }]}>{a.nota}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.especialidade}>{a.especialidade}</Text>
              <Text style={styles.profissional}>{a.profissional}</Text>
              <Text style={styles.meta}>
                {a.data} · {a.unidade}
              </Text>
            </View>
            <View style={[styles.catPill, { backgroundColor: cor.bg }]}>
              <Text style={[styles.catTxt, { color: cor.fg }]}>{ROTULO[cat]}</Text>
            </View>
          </View>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  content: { padding: 20, paddingBottom: 32 },
  title: { fontSize: 26, fontWeight: '800', color: Brand.ink, letterSpacing: -0.4 },
  subtitle: { fontSize: 14, color: Brand.muted, marginTop: 4, marginBottom: 18 },
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
  mediaLabel: { fontSize: 12, color: Brand.muted, marginTop: 2 },
  divisor: { width: 1, alignSelf: 'stretch', backgroundColor: Brand.line, marginHorizontal: 16 },
  dist: { flex: 1, gap: 8 },
  distRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  distDot: { width: 9, height: 9, borderRadius: 5 },
  distLabel: { flex: 1, fontSize: 13.5, color: '#40514C' },
  distNum: { fontSize: 14, fontWeight: '700', color: Brand.ink },
  secao: { fontSize: 13, fontWeight: '700', color: Brand.muted, textTransform: 'uppercase', letterSpacing: 0.6, marginBottom: 12 },
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
  nota: {
    width: 52,
    height: 52,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  notaNum: { fontSize: 22, fontWeight: '800' },
  especialidade: { fontSize: 15, fontWeight: '700', color: Brand.ink },
  profissional: { fontSize: 13, color: '#40514C', marginTop: 1 },
  meta: { fontSize: 12, color: Brand.muted, marginTop: 3 },
  catPill: { paddingHorizontal: 9, paddingVertical: 4, borderRadius: 20 },
  catTxt: { fontSize: 11, fontWeight: '700' },
});
