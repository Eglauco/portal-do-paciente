import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Keyboard,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AvaliacaoSauModal } from '@/components/avaliacao-sau-modal';
import { Brand } from '@/constants/theme';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import {
  ManifestacaoDetalhe,
  MensagemSau,
  StatusManifestacao,
  buscarManifestacao,
  encerrarManifestacao,
  responderManifestacao,
} from '@/services/sau';

const ESTRELAS = [1, 2, 3, 4, 5];
const COR_ESTRELA = '#F2A900';

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
  const [mostrarAvaliar, setMostrarAvaliar] = useState(false);
  const [encerrando, setEncerrando] = useState(false);
  const [erroEncerrar, setErroEncerrar] = useState<string | null>(null);
  const [perguntarEncerrar, setPerguntarEncerrar] = useState(false);
  const [reabrindo, setReabrindo] = useState(false);
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
      setReabrindo(false);
      Keyboard.dismiss();
      // Pergunta se deseja encerrar ou aguardar a resposta do SAU.
      setPerguntarEncerrar(true);
    } catch {
      // Falha de rede ou 409 (fora de vez): avisa o paciente e preserva o texto.
      // Recarrega para sincronizar o status — sem apagar a thread se o resync falhar.
      setErroEnvio('Não foi possível enviar agora. Tente novamente.');
      carregar();
    } finally {
      setEnviando(false);
    }
  };

  const abrirAvaliar = () => {
    setErroEncerrar(null);
    setPerguntarEncerrar(false);
    setMostrarAvaliar(true);
  };

  const encerrar = async (nota: number, comentario: string) => {
    if (!id || encerrando) return;
    try {
      setEncerrando(true);
      setErroEncerrar(null);
      const atualizado = await encerrarManifestacao(id, nota, comentario);
      setDetalhe(atualizado);
      setMostrarAvaliar(false);
      setReabrindo(false);
      Keyboard.dismiss();
    } catch {
      // Mantém o modal aberto (preserva a nota digitada) e mostra o erro nele.
      setErroEncerrar('Não foi possível encerrar agora. Tente novamente.');
    } finally {
      setEncerrando(false);
    }
  };

  const mensagens = detalhe?.mensagens ?? [];
  const status = detalhe ? CORES_STATUS[detalhe.status] : null;
  const avaliada = detalhe?.avaliadoEm != null;
  const fechada = detalhe?.status === 'FECHADA';
  const aguardandoPaciente = detalhe?.status === 'AGUARDANDO_PACIENTE';
  // Onde estamos no fluxo (avaliada = definitiva; fechada s/ nota = avaliar ou reabrir).
  const mostrarResponder = !avaliada && (aguardandoPaciente || (fechada && reabrindo));
  const mostrarAvaliarReabrir = fechada && !avaliada && !reabrindo;
  const mostrarAguardando = !avaliada && !fechada && !aguardandoPaciente; // aguardando SAU
  const mostrarEncerrar = !avaliada && !fechada; // conversa aberta: pode encerrar a qualquer momento

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
            // Papel embaixo do nome. Para o SAU é "Atendimento SAU" com o nome do
            // atendente em negrito acima; se não houver nome (autorNome já é o papel),
            // não repete a mesma linha duas vezes.
            const papel = doSau ? 'Atendimento SAU' : 'Você';
            const mostrarPapel = m.autorNome !== papel;
            return (
              <View key={m.id} style={[styles.card, doSau && styles.cardSau]}>
                <View style={styles.cardCab}>
                  <View style={[styles.avatar, doSau && styles.avatarSau]}>
                    <Text style={[styles.avatarTxt, doSau && styles.avatarTxtSau]}>{iniciais(m.autorNome)}</Text>
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.autorNome} numberOfLines={1}>{m.autorNome}</Text>
                    {mostrarPapel && <Text style={styles.autorPapel}>{papel}</Text>}
                  </View>
                </View>
                <Text style={styles.cardData}>{dataHora(m.criadoEm)}</Text>
                <Text style={styles.cardCorpo}>{m.texto}</Text>
              </View>
            );
          })}

          {/* Encerrada e avaliada (definitiva): mostra a nota dada */}
          {avaliada && (
            <View style={styles.avaliacaoCard}>
              <View style={styles.avaliacaoTopo}>
                <Ionicons name="checkmark-circle" size={18} color={Brand.brand} />
                <Text style={styles.avaliacaoTitulo}>Atendimento encerrado e avaliado</Text>
              </View>
              <View style={styles.estrelasVer} accessibilityLabel={`Nota ${detalhe?.avaliacaoNota} de 5 estrelas`}>
                {ESTRELAS.map((n) => (
                  <Ionicons
                    key={n}
                    name={(detalhe?.avaliacaoNota ?? 0) >= n ? 'star' : 'star-outline'}
                    size={22}
                    color={(detalhe?.avaliacaoNota ?? 0) >= n ? COR_ESTRELA : '#CBD6D1'}
                  />
                ))}
              </View>
              {!!detalhe?.avaliacaoComentario && (
                <Text style={styles.avaliacaoComentario}>{detalhe.avaliacaoComentario}</Text>
              )}
            </View>
          )}

          {/* Fechada pelo SAU e ainda não avaliada: avaliar ou reabrir */}
          {mostrarAvaliarReabrir && (
            <View style={styles.encerradaBox}>
              <View style={styles.encerradaTopo}>
                <Ionicons name="lock-closed-outline" size={18} color={Brand.muted} />
                <Text style={styles.encerradaTxt}>
                  Esta conversa foi encerrada. Avalie o atendimento ou reabra para continuar.
                </Text>
              </View>
              <Pressable style={styles.enviar} onPress={abrirAvaliar}>
                <Ionicons name="star" size={17} color="#fff" />
                <Text style={styles.enviarTxt}>Avaliar atendimento</Text>
              </Pressable>
              <Pressable style={styles.btnGhost} onPress={() => setReabrindo(true)}>
                <Ionicons name="refresh" size={16} color={Brand.brandDeep} />
                <Text style={styles.btnGhostTxt}>Reabrir e continuar</Text>
              </Pressable>
            </View>
          )}

          {/* Responder (vez do paciente, ou reabrindo uma conversa fechada) */}
          {mostrarResponder && (
            <View style={styles.responder}>
              <Text style={styles.responderLabel}>
                {reabrindo ? 'Responder (reabre a manifestação)' : 'Responder'}
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
          )}

          {/* Aguardando o SAU */}
          {mostrarAguardando && (
            <View style={styles.aguardando}>
              <Ionicons name="time-outline" size={20} color={Brand.muted} />
              <Text style={styles.aguardandoTxt}>
                Aguardando a resposta do SAU. Você poderá responder novamente quando o atendimento retornar.
              </Text>
            </View>
          )}

          {/* Encerrar a qualquer momento (conversa aberta) */}
          {mostrarEncerrar && (
            <Pressable style={[styles.btnGhost, { marginTop: 14 }]} onPress={abrirAvaliar}>
              <Ionicons name="close-circle-outline" size={16} color={Brand.brandDeep} />
              <Text style={styles.btnGhostTxt}>Encerrar conversa</Text>
            </Pressable>
          )}
        </ScrollView>
      )}

      {/* Pergunta pós-envio: encerrar ou aguardar o SAU */}
      <Modal
        visible={perguntarEncerrar}
        transparent
        animationType="fade"
        statusBarTranslucent
        onRequestClose={() => setPerguntarEncerrar(false)}>
        <View style={styles.promptBackdrop}>
          <View style={styles.promptCard} accessibilityViewIsModal accessibilityRole="alert">
            <Text style={styles.promptTitulo}>Mensagem enviada</Text>
            <Text style={styles.promptTexto}>
              Deseja encerrar a conversa e avaliar o atendimento, ou aguardar a resposta do SAU?
            </Text>
            <Pressable style={styles.enviar} onPress={abrirAvaliar}>
              <Ionicons name="star" size={17} color="#fff" />
              <Text style={styles.enviarTxt}>Encerrar e avaliar</Text>
            </Pressable>
            <Pressable style={styles.btnGhost} onPress={() => setPerguntarEncerrar(false)}>
              <Text style={styles.btnGhostTxt}>Aguardar resposta do SAU</Text>
            </Pressable>
          </View>
        </View>
      </Modal>

      {/* Avaliação (encerrar) */}
      <AvaliacaoSauModal
        visivel={mostrarAvaliar}
        processando={encerrando}
        erro={erroEncerrar}
        onEnviar={encerrar}
        onFechar={() => setMostrarAvaliar(false)}
      />
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

  // Botão secundário (reabrir / encerrar / aguardar)
  btnGhost: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 7,
    marginTop: 10,
    height: 48,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Brand.line,
    backgroundColor: Brand.surface,
  },
  btnGhostTxt: { color: Brand.brandDeep, fontSize: 14.5, fontWeight: '800' },

  // Encerrada pelo SAU (avaliar ou reabrir)
  encerradaBox: {
    marginTop: 4,
    padding: 14,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: Brand.line,
    backgroundColor: Brand.surface,
  },
  encerradaTopo: { flexDirection: 'row', alignItems: 'center', gap: 10, marginBottom: 4 },
  encerradaTxt: { flex: 1, fontSize: 13.5, color: Brand.muted, lineHeight: 19 },

  // Avaliação final (definitiva)
  avaliacaoCard: {
    marginTop: 4,
    padding: 16,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#CDEAE1',
    backgroundColor: '#F1FAF7',
    alignItems: 'center',
    gap: 8,
  },
  avaliacaoTopo: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  avaliacaoTitulo: { fontSize: 14.5, fontWeight: '800', color: Brand.brandDeep },
  estrelasVer: { flexDirection: 'row', gap: 4 },
  avaliacaoComentario: { fontSize: 14, color: '#40514C', lineHeight: 20, textAlign: 'center', marginTop: 2 },

  // Pergunta pós-envio (encerrar ou aguardar)
  promptBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(7,46,43,0.55)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  promptCard: { width: '100%', maxWidth: 400, backgroundColor: Brand.surface, borderRadius: 24, padding: 22 },
  promptTitulo: { fontSize: 18, fontWeight: '800', color: Brand.ink, letterSpacing: -0.3 },
  promptTexto: { fontSize: 14, color: Brand.muted, lineHeight: 20, marginTop: 6, marginBottom: 6 },

  estado: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, padding: 24 },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: Brand.ink },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center' },
  estadoBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 7, marginTop: 4, height: 44,
    paddingHorizontal: 18, borderRadius: 14, backgroundColor: Brand.brand,
  },
  estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
});
