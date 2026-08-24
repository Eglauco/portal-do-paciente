import { Ionicons } from '@expo/vector-icons';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { ScreenHeader } from '@/components/screen-header';
import { Brand } from '@/constants/theme';

type Tipo = 'agendamento' | 'exame' | 'chat' | 'nps' | 'sistema';

interface Notificacao {
  id: string;
  tipo: Tipo;
  titulo: string;
  descricao: string;
  hora: string;
  naoLida?: boolean;
}

const ESTILO_TIPO: Record<Tipo, { icon: string; fg: string; bg: string }> = {
  agendamento: { icon: 'calendar', fg: '#0E8C7F', bg: '#DCF1EC' },
  exame: { icon: 'flask', fg: '#2F6DF6', bg: '#E9F0FE' },
  chat: { icon: 'chatbubble-ellipses', fg: '#7A5AF5', bg: '#EFEAFE' },
  nps: { icon: 'star', fg: '#A5741A', bg: '#FBF0D6' },
  sistema: { icon: 'shield-checkmark', fg: '#0A7D5A', bg: '#E3F6EC' },
};

const GRUPOS: { titulo: string; itens: Notificacao[] }[] = [
  {
    titulo: 'Hoje',
    itens: [
      { id: '1', tipo: 'exame', titulo: 'Resultado disponível', descricao: 'Seu Hemograma completo já está no Prontuário.', hora: '08:05', naoLida: true },
      { id: '2', tipo: 'chat', titulo: 'Nova mensagem da unidade', descricao: 'Unidade de Saúde 01 respondeu sua conversa.', hora: '08:05', naoLida: true },
      { id: '3', tipo: 'agendamento', titulo: 'Lembrete de consulta', descricao: 'Cardiologia com Dr. Rafael Lima em 26/08 às 14:30.', hora: '07:30' },
    ],
  },
  {
    titulo: 'Ontem',
    itens: [
      { id: '4', tipo: 'agendamento', titulo: 'Consulta remarcada', descricao: 'Dermatologia remarcada para 02/09 às 10:15.', hora: '09:17' },
      { id: '5', tipo: 'nps', titulo: 'Avalie seu atendimento', descricao: 'Como foi sua consulta de Clínico Geral? Dê uma nota.', hora: '18:40' },
    ],
  },
  {
    titulo: 'Anteriores',
    itens: [
      { id: '6', tipo: 'exame', titulo: 'Coleta agendada', descricao: 'Exame de sangue agendado para 28/08 às 08:00.', hora: '20 ago' },
      { id: '7', tipo: 'sistema', titulo: 'Bem-vindo ao Portal do Paciente', descricao: 'Seu cadastro foi ativado com sucesso.', hora: '15 ago' },
    ],
  },
];

export default function NotificacoesScreen() {
  return (
    <View style={styles.screen}>
      <ScreenHeader
        title="Notificações"
        action={<Text style={styles.marcar}>Marcar lidas</Text>}
      />
      <ScrollView contentContainerStyle={styles.content}>
        {GRUPOS.map((grupo) => (
          <View key={grupo.titulo} style={styles.grupo}>
            <Text style={styles.grupoTitulo}>{grupo.titulo}</Text>
            {grupo.itens.map((n) => {
              const e = ESTILO_TIPO[n.tipo];
              return (
                <View key={n.id} style={[styles.item, n.naoLida && styles.itemNaoLido]}>
                  <View style={[styles.icon, { backgroundColor: e.bg }]}>
                    <Ionicons name={e.icon as any} size={18} color={e.fg} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <View style={styles.itemTop}>
                      <Text style={styles.titulo} numberOfLines={1}>
                        {n.titulo}
                      </Text>
                      <Text style={styles.hora}>{n.hora}</Text>
                    </View>
                    <Text style={styles.descricao}>{n.descricao}</Text>
                  </View>
                  {n.naoLida && <View style={styles.dot} />}
                </View>
              );
            })}
          </View>
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  marcar: { fontSize: 13, fontWeight: '600', color: Brand.brandDeep },
  content: { padding: 16, paddingBottom: 40 },
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
  icon: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  itemTop: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
  titulo: { flex: 1, fontSize: 14.5, fontWeight: '700', color: Brand.ink },
  hora: { fontSize: 11.5, color: Brand.muted },
  descricao: { fontSize: 13, color: '#40514C', marginTop: 2, lineHeight: 18 },
  dot: { width: 9, height: 9, borderRadius: 5, backgroundColor: Brand.brand },
});
