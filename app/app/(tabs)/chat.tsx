import { Ionicons } from '@expo/vector-icons';
import { useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { Brand } from '@/constants/theme';

type De = 'paciente' | 'unidade';
interface Mensagem {
  id: string;
  de: De;
  texto: string;
  hora: string;
  dia: string;
}

const INICIAIS: Mensagem[] = [
  { id: '1', de: 'paciente', dia: 'Ontem', hora: '09:12', texto: 'Bom dia! Gostaria de remarcar minha consulta de cardiologia.' },
  { id: '2', de: 'unidade', dia: 'Ontem', hora: '09:15', texto: 'Bom dia, Mariana! Claro, temos horário no dia 02/09 às 10:15. Fica bom para você?' },
  { id: '3', de: 'paciente', dia: 'Ontem', hora: '09:16', texto: 'Perfeito, pode confirmar 😊' },
  { id: '4', de: 'unidade', dia: 'Ontem', hora: '09:17', texto: 'Consulta remarcada com sucesso! Enviamos a confirmação para o seu app.' },
  { id: '5', de: 'paciente', dia: 'Hoje', hora: '08:02', texto: 'Meu exame de sangue já está disponível?' },
  { id: '6', de: 'unidade', dia: 'Hoje', hora: '08:05', texto: 'Sim! O laudo já está no seu Prontuário, na aba de documentos. Qualquer dúvida estamos por aqui. 🩺' },
];

export default function ChatScreen() {
  const [mensagens, setMensagens] = useState<Mensagem[]>(INICIAIS);
  const [texto, setTexto] = useState('');
  const scrollRef = useRef<ScrollView>(null);

  const enviar = () => {
    const limpo = texto.trim();
    if (!limpo) return;
    setMensagens((atual) => [
      ...atual,
      { id: String(atual.length + 1), de: 'paciente', dia: 'Hoje', hora: 'agora', texto: limpo },
    ]);
    setTexto('');
    requestAnimationFrame(() => scrollRef.current?.scrollToEnd({ animated: true }));
  };

  return (
    <View style={styles.screen}>
      {/* Cabeçalho do contato (unidade) */}
      <View style={styles.contato}>
        <View style={styles.contatoAvatar}>
          <Ionicons name="medical" size={20} color={Brand.glow} />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.contatoNome}>Unidade de Saúde 01</Text>
          <View style={styles.online}>
            <View style={styles.onlineDot} />
            <Text style={styles.contatoStatus}>Atendimento • responde em minutos</Text>
          </View>
        </View>
        <Ionicons name="call-outline" size={20} color={Brand.brandDeep} />
      </View>

      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          ref={scrollRef}
          style={styles.mensagens}
          contentContainerStyle={styles.mensagensContent}
          onContentSizeChange={() => scrollRef.current?.scrollToEnd({ animated: false })}>
          <View style={styles.avisoWrap}>
            <Text style={styles.aviso}>
              🔒 Conversa protegida com a sua unidade de saúde
            </Text>
          </View>

          {mensagens.map((m, i) => {
            const mostrarDia = i === 0 || mensagens[i - 1].dia !== m.dia;
            const daUnidade = m.de === 'unidade';
            return (
              <View key={m.id}>
                {mostrarDia && (
                  <View style={styles.diaWrap}>
                    <Text style={styles.dia}>{m.dia}</Text>
                  </View>
                )}
                <View style={[styles.bolhaWrap, daUnidade ? styles.esquerda : styles.direita]}>
                  <View style={[styles.bolha, daUnidade ? styles.bolhaUnidade : styles.bolhaPaciente]}>
                    <Text style={styles.texto}>{m.texto}</Text>
                    <View style={styles.rodape}>
                      <Text style={styles.hora}>{m.hora}</Text>
                      {!daUnidade && <Ionicons name="checkmark-done" size={14} color="#3FA9F5" />}
                    </View>
                  </View>
                </View>
              </View>
            );
          })}
        </ScrollView>

        {/* Barra de digitação */}
        <View style={styles.inputBar}>
          <View style={styles.inputWrap}>
            <Ionicons name="happy-outline" size={22} color={Brand.muted} />
            <TextInput
              style={styles.input}
              value={texto}
              onChangeText={setTexto}
              placeholder="Mensagem"
              placeholderTextColor="#9AAAA5"
              multiline
            />
            <Ionicons name="attach-outline" size={22} color={Brand.muted} />
          </View>
          <Pressable style={styles.enviar} onPress={enviar} accessibilityLabel="Enviar mensagem">
            <Ionicons name="send" size={19} color="#fff" />
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EEF3F1' },
  contato: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: Brand.surface,
    borderBottomWidth: 1,
    borderBottomColor: Brand.line,
  },
  contatoAvatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.brandDeep,
  },
  contatoNome: { fontSize: 15, fontWeight: '700', color: Brand.ink },
  online: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 2 },
  onlineDot: { width: 7, height: 7, borderRadius: 4, backgroundColor: '#2FBF71' },
  contatoStatus: { fontSize: 12, color: Brand.muted },
  mensagens: { flex: 1 },
  mensagensContent: { padding: 14, paddingBottom: 8 },
  avisoWrap: { alignItems: 'center', marginBottom: 12 },
  aviso: {
    fontSize: 11.5,
    color: '#6A7B76',
    backgroundColor: '#FCF6E3',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 10,
    overflow: 'hidden',
  },
  diaWrap: { alignItems: 'center', marginVertical: 10 },
  dia: {
    fontSize: 11.5,
    fontWeight: '600',
    color: '#6A7B76',
    backgroundColor: '#DDE7E3',
    paddingHorizontal: 12,
    paddingVertical: 4,
    borderRadius: 10,
    overflow: 'hidden',
  },
  bolhaWrap: { marginBottom: 8, maxWidth: '82%' },
  esquerda: { alignSelf: 'flex-start' },
  direita: { alignSelf: 'flex-end' },
  bolha: { borderRadius: 16, paddingHorizontal: 12, paddingVertical: 8 },
  bolhaUnidade: {
    backgroundColor: Brand.surface,
    borderTopLeftRadius: 4,
    borderWidth: 1,
    borderColor: Brand.line,
  },
  bolhaPaciente: { backgroundColor: '#D6F0E7', borderTopRightRadius: 4 },
  texto: { fontSize: 14.5, color: Brand.ink, lineHeight: 20 },
  rodape: { flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: 3, marginTop: 3 },
  hora: { fontSize: 10.5, color: '#7C8C87' },
  inputBar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    backgroundColor: '#EEF3F1',
  },
  inputWrap: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: Brand.surface,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: Brand.line,
    paddingHorizontal: 14,
    paddingVertical: Platform.OS === 'ios' ? 10 : 4,
    minHeight: 46,
  },
  input: { flex: 1, fontSize: 15, color: Brand.ink, maxHeight: 100 },
  enviar: {
    width: 46,
    height: 46,
    borderRadius: 23,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.brand,
  },
});
