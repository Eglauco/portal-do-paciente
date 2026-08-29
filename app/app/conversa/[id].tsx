import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Brand } from '@/constants/theme';
import {
  buscarConversa,
  ChatDetalhe,
  confirmarEntrega,
  enviarMensagemPaciente,
  Mensagem,
} from '@/services/chat';
import { limparChatAtivo, setChatAtivo } from '@/services/chat-ativo';
import { observarDigitando, observarMensagens, sinalizarDigitando } from '@/services/chat-realtime';

const doisDigitos = (n: number) => String(n).padStart(2, '0');

function rotuloDia(iso: string): string {
  const d = new Date(iso);
  const agora = new Date();
  if (d.toDateString() === agora.toDateString()) return 'Hoje';
  const ontem = new Date(agora);
  ontem.setDate(agora.getDate() - 1);
  if (d.toDateString() === ontem.toDateString()) return 'Ontem';
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}/${d.getFullYear()}`;
}

function horaMsg(iso: string): string {
  const d = new Date(iso);
  return `${doisDigitos(d.getHours())}:${doisDigitos(d.getMinutes())}`;
}

function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/);
  const a = partes[0]?.charAt(0) ?? '';
  const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
  return (a + b).toUpperCase();
}

export default function ConversaScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [detalhe, setDetalhe] = useState<ChatDetalhe | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [digitando, setDigitando] = useState(false);
  const scrollRef = useRef<ScrollView>(null);
  const jaCarregou = useRef(false);
  const ultimoSinalDigitando = useRef(0);
  const timerDigitando = useRef<ReturnType<typeof setTimeout> | null>(null);

  const rolarParaFim = (animado = true) =>
    requestAnimationFrame(() => scrollRef.current?.scrollToEnd({ animated: animado }));

  const carregar = useCallback(async () => {
    if (!id) return;
    try {
      if (!jaCarregou.current) setCarregando(true);
      setErro(false);
      const dados = await buscarConversa(id);
      setDetalhe(dados);
      jaCarregou.current = true;
      rolarParaFim(false);
      // Confirma a entrega das mensagens da unidade que chegaram neste aparelho.
      confirmarEntrega(id);
    } catch {
      setErro(true);
    } finally {
      setCarregando(false);
    }
  }, [id]);

  useFocusEffect(
    useCallback(() => {
      carregar();
      if (id) setChatAtivo(id);
      return () => {
        if (id) limparChatAtivo(id);
      };
    }, [carregar, id]),
  );

  // Tempo real (WebSocket/STOMP): mensagens, "digitando…" e recibo de leitura.
  useEffect(() => {
    if (!id) return;
    const cancelarMsg = observarMensagens(id, (m) => {
      setDigitando(false); // chegou mensagem: parou de digitar
      setDetalhe((d) => {
        if (!d) return d;
        if (d.mensagens.some((x) => x.id === m.id)) return d; // evita duplicar
        // Reconcilia o eco da própria mensagem otimista (mesmo texto, ainda pendente).
        const base =
          m.remetente === 'PACIENTE'
            ? d.mensagens.filter((x) => !(x.pendente && x.texto === m.texto))
            : d.mensagens;
        return { ...d, mensagens: [...base, m] };
      });
      // Recibo de entrega: a mensagem da unidade chegou neste aparelho.
      if (m.remetente === 'UNIDADE') confirmarEntrega(id);
      rolarParaFim();
    });
    const cancelarDig = observarDigitando(id, (e) => {
      if (e.de !== 'UNIDADE') return; // só mostra quando o outro lado digita
      setDigitando(true);
      if (timerDigitando.current) clearTimeout(timerDigitando.current);
      timerDigitando.current = setTimeout(() => setDigitando(false), 3000);
    });
    return () => {
      cancelarMsg();
      cancelarDig();
      if (timerDigitando.current) clearTimeout(timerDigitando.current);
    };
  }, [id]);

  const aoDigitar = (valor: string) => {
    setTexto(valor);
    if (!id) return;
    const agora = Date.now();
    if (agora - ultimoSinalDigitando.current > 1500) {
      ultimoSinalDigitando.current = agora;
      sinalizarDigitando(id, 'PACIENTE');
    }
  };

  const enviar = async () => {
    const limpo = texto.trim();
    if (!limpo || !id || enviando) return;
    setEnviando(true);

    // Otimista: mostra a mensagem na hora com o "relógio" (pendente) até o servidor confirmar.
    const otimista: Mensagem = {
      id: -Date.now(),
      remetente: 'PACIENTE',
      texto: limpo,
      enviadaEm: new Date().toISOString(),
      lida: false,
      entregue: false,
      pendente: true,
    };
    setDetalhe((d) => (d ? { ...d, mensagens: [...d.mensagens, otimista] } : d));
    setTexto('');
    rolarParaFim();

    try {
      const atualizado = await enviarMensagemPaciente(id, limpo);
      // Servidor confirmou: substitui a lista pela do servidor (mensagem já persistida = 2 checks).
      setDetalhe(atualizado);
      rolarParaFim();
    } catch {
      // Falha (conexão ruim): a mensagem otimista continua com o relógio até chegar ao servidor.
    } finally {
      setEnviando(false);
    }
  };

  const mensagens = detalhe?.mensagens ?? [];

  return (
    <KeyboardAvoidingView
      style={styles.screen}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      {/* Cabeçalho da conversa */}
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Pressable
          style={({ pressed }) => [styles.back, pressed && styles.backPressed]}
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Voltar">
          <Ionicons name="chevron-back" size={26} color={Brand.ink} />
        </Pressable>
        <View style={styles.avatar}>
          {detalhe ? (
            <Text style={styles.avatarTxt}>{iniciais(detalhe.unidadeSaude.nome)}</Text>
          ) : (
            <Ionicons name="medical" size={18} color={Brand.glow} />
          )}
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.contatoNome} numberOfLines={1}>
            {detalhe?.unidadeSaude.nome ?? 'Conversa'}
          </Text>
          {digitando ? (
            <Text style={styles.digitando} numberOfLines={1}>
              digitando…
            </Text>
          ) : (
            <Text style={styles.contatoStatus} numberOfLines={1}>
              {detalhe?.statusDescricao ?? 'Carregando…'}
            </Text>
          )}
        </View>
      </View>

      {carregando ? (
          <View style={styles.estado}>
            <ActivityIndicator color={Brand.brand} />
            <Text style={styles.estadoTxt}>Carregando conversa…</Text>
          </View>
        ) : erro ? (
          <View style={styles.estado}>
            <Ionicons name="cloud-offline-outline" size={28} color={Brand.muted} />
            <Text style={styles.estadoTitulo}>Não foi possível abrir</Text>
            <Pressable style={styles.estadoBtn} onPress={carregar}>
              <Ionicons name="refresh" size={16} color="#fff" />
              <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
            </Pressable>
          </View>
        ) : (
          <ScrollView
            ref={scrollRef}
            style={styles.mensagens}
            contentContainerStyle={styles.mensagensContent}
            onContentSizeChange={() => rolarParaFim(false)}>
            <View style={styles.avisoWrap}>
              <Text style={styles.aviso}>🔒 Conversa protegida com a sua unidade de saúde</Text>
            </View>

            {mensagens.map((m: Mensagem, i) => {
              const mostrarDia =
                i === 0 || rotuloDia(mensagens[i - 1].enviadaEm) !== rotuloDia(m.enviadaEm);
              const daUnidade = m.remetente === 'UNIDADE';
              return (
                <View key={m.id}>
                  {mostrarDia && (
                    <View style={styles.diaWrap}>
                      <Text style={styles.dia}>{rotuloDia(m.enviadaEm)}</Text>
                    </View>
                  )}
                  <View style={[styles.bolhaWrap, daUnidade ? styles.esquerda : styles.direita]}>
                    <View style={[styles.bolha, daUnidade ? styles.bolhaUnidade : styles.bolhaPaciente]}>
                      <Text style={styles.texto}>{m.texto}</Text>
                      <View style={styles.rodape}>
                        <Text style={styles.hora}>{horaMsg(m.enviadaEm)}</Text>
                        {!daUnidade &&
                          (m.pendente ? (
                            <Ionicons name="time-outline" size={13} color="#7C8C87" />
                          ) : (
                            <Ionicons name="checkmark-done" size={15} color="#5B6B66" />
                          ))}
                      </View>
                    </View>
                  </View>
                </View>
              );
            })}
          </ScrollView>
        )}

        {/* Barra de digitação */}
        {!carregando && !erro && (
          <View style={[styles.inputBar, { paddingBottom: Math.max(insets.bottom, 10) }]}>
            <View style={styles.inputWrap}>
              <TextInput
                style={styles.input}
                value={texto}
                onChangeText={aoDigitar}
                placeholder="Mensagem"
                placeholderTextColor="#9AAAA5"
                multiline
              />
            </View>
            <Pressable
              style={[styles.enviar, (enviando || !texto.trim()) && styles.enviarDesativado]}
              onPress={enviar}
              disabled={enviando || !texto.trim()}
              accessibilityLabel="Enviar mensagem">
              {enviando ? (
                <ActivityIndicator color="#fff" size="small" />
              ) : (
                <Ionicons name="send" size={19} color="#fff" />
              )}
            </Pressable>
          </View>
        )}
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#EEF3F1' },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 8,
    paddingBottom: 10,
    backgroundColor: Brand.surface,
    borderBottomWidth: 1,
    borderBottomColor: Brand.line,
  },
  back: { width: 38, height: 38, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  backPressed: { backgroundColor: '#EAF2EF' },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.brandDeep,
  },
  avatarTxt: { color: Brand.onBrand, fontSize: 14, fontWeight: '800' },
  contatoNome: { fontSize: 15.5, fontWeight: '700', color: Brand.ink },
  contatoStatus: { fontSize: 12, color: Brand.muted, marginTop: 1 },
  digitando: { fontSize: 12, color: Brand.brand, fontWeight: '700', fontStyle: 'italic', marginTop: 1 },

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
    paddingTop: 10,
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
  enviarDesativado: { opacity: 0.6 },

  estado: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, padding: 24 },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: Brand.ink },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center' },
  estadoBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    marginTop: 4,
    height: 44,
    paddingHorizontal: 18,
    borderRadius: 14,
    backgroundColor: Brand.brand,
  },
  estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
});
