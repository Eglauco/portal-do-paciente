import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Keyboard,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Brand } from '@/constants/theme';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import {
  ManifestacaoDetalhe,
  MensagemSau,
  StatusManifestacao,
  buscarManifestacao,
  responderManifestacao,
} from '@/services/sau';

const doisDigitos = (n: number) => String(n).padStart(2, '0');

function dataHora(iso: string): string {
  const d = new Date(iso);
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}/${d.getFullYear()} às ${doisDigitos(d.getHours())}:${doisDigitos(d.getMinutes())}`;
}

function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/);
  const a = partes[0]?.charAt(0) ?? '';
  const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
  return (a + b).toUpperCase();
}

const CORES_STATUS: Record<StatusManifestacao, { fg: string; bg: string }> = {
  AGUARDANDO_SAU: { fg: '#A5741A', bg: '#FBF0D6' },
  AGUARDANDO_PACIENTE: { fg: '#2F6DF6', bg: '#E9F0FE' },
  FECHADA: { fg: '#566863', bg: '#ECEEF1' },
};

export default function ManifestacaoScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [detalhe, setDetalhe] = useState<ManifestacaoDetalhe | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [erroEnvio, setErroEnvio] = useState<string | null>(null);
  const [alturaTeclado, setAlturaTeclado] = useState(0);
  const scrollRef = useRef<ScrollView>(null);
  const jaCarregou = useRef(false);

  const carregar = useCallback(async () => {
    if (!id) return;
    try {
      if (!jaCarregou.current) setCarregando(true);
      setErro(false);
      const dados = await buscarManifestacao(id);
      setDetalhe(dados);
      jaCarregou.current = true;
    } catch {
      // Só mostra a tela cheia de erro no 1º carregamento. Num recarregamento
      // (ex.: resync após enviar) mantém a thread atual — não apaga o conteúdo.
      if (!jaCarregou.current) setErro(true);
    } finally {
      setCarregando(false);
    }
  }, [id]);

  useFocusEffect(
    useCallback(() => {
      carregar();
    }, [carregar]),
  );

  // Notificação (app aberto) ou volta ao primeiro plano: atualiza sem spinner
  // (o carregar já usa o ref jaCarregou, então não pisca o spinner).
  useAtualizarComPush(() => carregar());

  // Reserva a altura do teclado no fim (edge-to-edge não redimensiona a tela) e
  // rola até o campo de resposta para ele não ficar coberto.
  useEffect(() => {
    const aoMostrar = Keyboard.addListener('keyboardDidShow', (e) => setAlturaTeclado(e.endCoordinates.height));
    const aoEsconder = Keyboard.addListener('keyboardDidHide', () => setAlturaTeclado(0));
    return () => {
      aoMostrar.remove();
      aoEsconder.remove();
    };
  }, []);

  useEffect(() => {
    if (alturaTeclado > 0) {
      const t = setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 50);
      return () => clearTimeout(t);
    }
  }, [alturaTeclado]);

  const enviar = async () => {
    const limpo = texto.trim();
    if (!limpo || !id || enviando) return;
    try {
      setEnviando(true);
      setErroEnvio(null);
      const atualizado = await responderManifestacao(id, limpo);
      setDetalhe(atualizado);
      setTexto('');
      Keyboard.dismiss();
    } catch {
      // Falha de rede ou 409 (fora de vez): avisa o paciente e preserva o texto.
      // Recarrega para sincronizar o status — sem apagar a thread se o resync falhar.
      setErroEnvio('Não foi possível enviar agora. Tente novamente.');
      carregar();
    } finally {
      setEnviando(false);
    }
  };

  const mensagens = detalhe?.mensagens ?? [];
  const status = detalhe ? CORES_STATUS[detalhe.status] : null;
  const fechada = detalhe?.status === 'FECHADA';
  // Fluxo alternado: é a vez do paciente quando aguarda o paciente ou está fechada (responder reabre).
  const podeResponder = detalhe?.status === 'AGUARDANDO_PACIENTE' || fechada;

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
        <View style={{ flex: 1 }}>
          <Text style={styles.headerTitulo} numberOfLines={1}>
            {detalhe ? `${detalhe.tipo.nome} · ${detalhe.unidadeSaude.nome}` : 'Manifestação'}
          </Text>
          {status && detalhe && (
            <View style={[styles.pill, { backgroundColor: status.bg, alignSelf: 'flex-start', marginTop: 3 }]}>
              <Text style={[styles.pillTxt, { color: status.fg }]}>{detalhe.statusDescricao}</Text>
            </View>
          )}
        </View>
      </View>

      {carregando ? (
        <View style={styles.estado}>
          <ActivityIndicator color={Brand.brand} />
          <Text style={styles.estadoTxt}>Carregando…</Text>
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
          style={styles.corpo}
          contentContainerStyle={{
            padding: 14,
            paddingBottom: 24 + (alturaTeclado > 0 ? alturaTeclado + insets.bottom : insets.bottom),
          }}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive">
          {/* Histórico estilo e-mail */}
          {mensagens.map((m: MensagemSau) => {
            const doSau = m.autor === 'SAU';
            return (
              <View key={m.id} style={[styles.card, doSau && styles.cardSau]}>
                <View style={styles.cardCab}>
                  <View style={[styles.avatar, doSau && styles.avatarSau]}>
                    <Text style={[styles.avatarTxt, doSau && styles.avatarTxtSau]}>{iniciais(m.autorNome)}</Text>
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.autorNome} numberOfLines={1}>{m.autorNome}</Text>
                    <Text style={styles.autorPapel}>{doSau ? 'Atendimento SAU' : 'Você'}</Text>
                  </View>
                </View>
                <Text style={styles.cardData}>{dataHora(m.criadoEm)}</Text>
                <Text style={styles.cardCorpo}>{m.texto}</Text>
              </View>
            );
          })}

          {/* Responder (só quando é a vez do paciente) ou aviso de estado */}
          {podeResponder ? (
            <View style={styles.responder}>
              <Text style={styles.responderLabel}>
                {fechada ? 'Responder (reabre a manifestação)' : 'Responder'}
              </Text>
              <TextInput
                style={styles.textarea}
                value={texto}
                onChangeText={(t) => {
                  setTexto(t);
                  if (erroEnvio) setErroEnvio(null);
                }}
                placeholder="Escreva sua resposta ao SAU"
                placeholderTextColor="#9AAAA5"
                multiline
                textAlignVertical="top"
                maxLength={4000}
              />
              {erroEnvio && (
                <View style={styles.erroBox}>
                  <Ionicons name="alert-circle" size={16} color="#B23B4E" />
                  <Text style={styles.erroTxt}>{erroEnvio}</Text>
                </View>
              )}
              <Pressable
                style={[styles.enviar, (!texto.trim() || enviando) && styles.enviarDesativado]}
                onPress={enviar}
                disabled={!texto.trim() || enviando}>
                {enviando ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <>
                    <Ionicons name="send" size={17} color="#fff" />
                    <Text style={styles.enviarTxt}>Enviar resposta</Text>
                  </>
                )}
              </Pressable>
            </View>
          ) : (
            <View style={styles.aguardando}>
              <Ionicons name="time-outline" size={20} color={Brand.muted} />
              <Text style={styles.aguardandoTxt}>
                Aguardando a resposta do SAU. Você poderá responder novamente quando o atendimento retornar.
              </Text>
            </View>
          )}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 8,
    paddingBottom: 10,
    backgroundColor: Brand.surface,
    borderBottomWidth: 1,
    borderBottomColor: Brand.line,
  },
  back: { width: 38, height: 38, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  backPressed: { backgroundColor: '#EAF2EF' },
  headerTitulo: { fontSize: 15.5, fontWeight: '700', color: Brand.ink },
  pill: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 20 },
  pillTxt: { fontSize: 11, fontWeight: '700' },

  corpo: { flex: 1 },

  // Cartão de mensagem (estilo e-mail)
  card: {
    backgroundColor: Brand.surface,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 14,
    marginBottom: 12,
  },
  cardSau: { backgroundColor: '#F1FAF7', borderColor: '#CDEAE1' },
  cardCab: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  avatar: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center', backgroundColor: '#E2EAE6' },
  avatarSau: { backgroundColor: Brand.brand },
  avatarTxt: { color: '#40514C', fontSize: 14, fontWeight: '800' },
  avatarTxtSau: { color: '#fff' },
  autorNome: { fontSize: 14.5, fontWeight: '700', color: Brand.ink },
  autorPapel: { fontSize: 12, color: Brand.muted },
  cardData: { fontSize: 11.5, color: Brand.muted, marginTop: 8 },
  cardCorpo: { fontSize: 14.5, color: Brand.ink, lineHeight: 21, marginTop: 6 },

  // Responder
  responder: { marginTop: 4 },
  responderLabel: { fontSize: 13, fontWeight: '700', color: Brand.muted, marginBottom: 8 },
  textarea: {
    minHeight: 130,
    borderWidth: 1,
    borderColor: Brand.line,
    backgroundColor: Brand.surface,
    borderRadius: 14,
    padding: 14,
    fontSize: 15,
    color: Brand.ink,
    lineHeight: 21,
  },
  enviar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginTop: 12,
    height: 52,
    borderRadius: 14,
    backgroundColor: Brand.brand,
  },
  enviarDesativado: { opacity: 0.5 },
  enviarTxt: { color: '#fff', fontSize: 15, fontWeight: '800' },
  erroBox: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 10 },
  erroTxt: { flex: 1, fontSize: 13, color: '#B23B4E' },

  // Aguardando (não é a vez do paciente)
  aguardando: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: Brand.line,
    backgroundColor: Brand.surface,
  },
  aguardandoTxt: { flex: 1, fontSize: 13.5, color: Brand.muted, lineHeight: 19 },

  estado: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, padding: 24 },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: Brand.ink },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center' },
  estadoBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 7, marginTop: 4, height: 44,
    paddingHorizontal: 18, borderRadius: 14, backgroundColor: Brand.brand,
  },
  estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
});
