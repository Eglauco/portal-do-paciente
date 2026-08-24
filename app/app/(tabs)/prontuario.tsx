import { Ionicons } from '@expo/vector-icons';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { Brand, DocTipo } from '@/constants/theme';

interface Documento {
  tipo: keyof typeof DocTipo;
  titulo: string;
}
interface Atendimento {
  id: string;
  data: string;
  especialidade: string;
  profissional: string;
  unidade: string;
  documentos: Documento[];
}

const ROTULO_TIPO: Record<keyof typeof DocTipo, string> = {
  exame: 'Resultado de exame',
  receita: 'Receita médica',
  atestado: 'Atestado',
  ficha: 'Ficha de atendimento',
  laudo: 'Laudo',
};

// Ordenado por data de atendimento — o primeiro é o mais recente.
const ATENDIMENTOS: Atendimento[] = [
  {
    id: '1',
    data: '18 ago 2026',
    especialidade: 'Clínico Geral',
    profissional: 'Dr. Paulo Nunes',
    unidade: 'Unidade de Saúde 01',
    documentos: [
      { tipo: 'ficha', titulo: 'Ficha de atendimento clínico' },
      { tipo: 'exame', titulo: 'Hemograma completo' },
      { tipo: 'receita', titulo: 'Receita — Losartana 50mg' },
    ],
  },
  {
    id: '2',
    data: '11 ago 2026',
    especialidade: 'Ortopedia',
    profissional: 'Dra. Mariana Duarte',
    unidade: 'Unidade de Saúde 02',
    documentos: [
      { tipo: 'laudo', titulo: 'Laudo de Raio-X — Joelho D.' },
      { tipo: 'atestado', titulo: 'Atestado de afastamento (3 dias)' },
    ],
  },
  {
    id: '3',
    data: '29 jul 2026',
    especialidade: 'Cardiologia',
    profissional: 'Dr. Rafael Lima',
    unidade: 'Unidade de Saúde 01',
    documentos: [
      { tipo: 'ficha', titulo: 'Ficha de atendimento' },
      { tipo: 'exame', titulo: 'Eletrocardiograma (ECG)' },
      { tipo: 'laudo', titulo: 'Laudo cardiológico' },
      { tipo: 'receita', titulo: 'Receita — AAS 100mg' },
    ],
  },
];

export default function ProntuarioScreen() {
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Text style={styles.title}>Prontuário</Text>
      <Text style={styles.subtitle}>Seus documentos, organizados por atendimento.</Text>

      {ATENDIMENTOS.map((at) => (
        <View key={at.id} style={styles.grupo}>
          <View style={styles.grupoHeader}>
            <View style={styles.dataTag}>
              <Ionicons name="calendar-clear-outline" size={13} color={Brand.brandDeep} />
              <Text style={styles.dataTxt}>{at.data}</Text>
            </View>
            <Text style={styles.docCount}>{at.documentos.length} docs</Text>
          </View>

          <Text style={styles.especialidade}>{at.especialidade}</Text>
          <Text style={styles.profissional}>
            {at.profissional} · {at.unidade}
          </Text>

          <View style={styles.docs}>
            {at.documentos.map((doc, i) => {
              const cor = DocTipo[doc.tipo];
              return (
                <View key={i} style={[styles.docRow, i > 0 && styles.docRowBorder]}>
                  <View style={[styles.docIcon, { backgroundColor: cor.bg }]}>
                    <Ionicons name={cor.icon as any} size={18} color={cor.fg} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.docTitulo} numberOfLines={1}>
                      {doc.titulo}
                    </Text>
                    <Text style={styles.docTipo}>{ROTULO_TIPO[doc.tipo]}</Text>
                  </View>
                  <Ionicons name="download-outline" size={20} color={Brand.muted} />
                </View>
              );
            })}
          </View>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  content: { padding: 20, paddingBottom: 32 },
  title: { fontSize: 26, fontWeight: '800', color: Brand.ink, letterSpacing: -0.4 },
  subtitle: { fontSize: 14, color: Brand.muted, marginTop: 4, marginBottom: 18 },
  grupo: {
    backgroundColor: Brand.surface,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 16,
    marginBottom: 14,
  },
  grupoHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  dataTag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#E7F3EF',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 20,
  },
  dataTxt: { fontSize: 12.5, fontWeight: '700', color: Brand.brandDeep },
  docCount: { fontSize: 12, color: Brand.muted, fontWeight: '600' },
  especialidade: { fontSize: 16, fontWeight: '700', color: Brand.ink },
  profissional: { fontSize: 13, color: Brand.muted, marginTop: 2, marginBottom: 6 },
  docs: { marginTop: 8 },
  docRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 11 },
  docRowBorder: { borderTopWidth: 1, borderTopColor: '#EEF3F1' },
  docIcon: {
    width: 38,
    height: 38,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
  },
  docTitulo: { fontSize: 14.5, fontWeight: '600', color: Brand.ink },
  docTipo: { fontSize: 12, color: Brand.muted, marginTop: 1 },
});
