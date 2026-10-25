import { Ionicons } from '@expo/vector-icons';
import { BlurView } from 'expo-blur';
import * as Haptics from 'expo-haptics';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { memo, useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import {
  ActivityIndicator,
  Alert,
  BackHandler,
  Image,
  Keyboard,
  Modal,
  NativeScrollEvent,
  NativeSyntheticEvent,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from 'react-native';
// Lista = FlashList (Shopify) v2: RECICLA as views em vez de montar/desmontar (como o RecyclerView do
// Android). Montar um balão aqui é CARO (árvore de views + gesto de swipe nativo + mappers do
// reanimated); o FlatList refazia isso a cada balão que cruzava a borda no fling → travava no tablet
// fraco. O FlashList monta um punhado de células UMA vez e, ao rolar, só troca os dados → fling liso.
// v2 (New Arch): sem estimatedItemSize (mede sozinho), e `maintainVisibleContentPosition` já dá o
// "começa no fim" (startRenderingFromBottom) e o "rola pro fim só se perto" (autoscroll) de graça.
import { FlashList, type FlashListRef, type ListRenderItemInfo } from '@shopify/flash-list';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { useKeyboardHandler, useReanimatedKeyboardAnimation } from 'react-native-keyboard-controller';
import Animated, {
  Extrapolation,
  interpolate,
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { alpha, type Tema, useTema } from '@/hooks/use-tema';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import { useSessao } from '@/hooks/use-sessao';
import { podeLancar } from '@/services/sessao';
import {
  buscarConversa,
  ChatDetalhe,
  Citacao,
  confirmarEntrega,
  editarMensagemPaciente,
  enviarMensagemPaciente,
  falarComHumano,
  marcarLido,
  Mensagem,
  novoClienteId,
} from '@/services/chat';
import { limparChatAtivo, setChatAtivo } from '@/services/chat-ativo';
import {
  EstadoConexao,
  observarConexao,
  observarDigitando,
  observarMensagens,
  observarResponsavel,
  observarStatus,
  sinalizarDigitando,
} from '@/services/chat-realtime';

// Tile do fundo de ícones de saúde. Tilamos manualmente numa grade (o resizeMode="repeat" do
// <Image> do RN não tila de forma confiável no Android — mostra só 1 tile).
const PATTERN_FUNDO = require('../../assets/images/chat-pattern.png');
const TILE_FUNDO = 560; // tamanho de exibição de cada tile (pt). Tile 640px/14x14 (mesma densidade do
// antigo 320/7x7 a 280), mas MENOS tiles na tela (~20 vs ~48) = menos views = mais leve no aparelho fraco.
const OPACIDADE_FUNDO = 0.16; // ver nota no styles_fundo (opacidade POR-TILE, não no container)

/** Fundo de ícones de saúde cobrindo a tela inteira (grade de tiles calculada pela dimensão).
 *  Memoizado: só re-renderiza quando a dimensão muda (rotação), não a cada estado da conversa. */
const FundoSaude = memo(function FundoSaude() {
  const { width, height } = useWindowDimensions();
  const cols = Math.ceil(width / TILE_FUNDO) + 1;
  const rows = Math.ceil(height / TILE_FUNDO) + 1;
  const tiles = [];
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      tiles.push(
        <Image
          key={`p-${r}-${c}`}
          source={PATTERN_FUNDO}
          style={{ position: 'absolute', left: c * TILE_FUNDO, top: r * TILE_FUNDO, width: TILE_FUNDO, height: TILE_FUNDO, opacity: OPACIDADE_FUNDO }}
        />,
      );
    }
  }
  return (
    <View style={styles_fundo} pointerEvents="none">
      {tiles}
    </View>
  );
});
// PERF: a opacidade (16%) fica em CADA imagem (folha), NÃO aqui no container. `opacity` num container
// com vários filhos obriga o Android a renderizar tudo num buffer offscreen e recompor a cada frame —
// era o MAIOR vilão da rolagem lenta (median 105ms→23ms ao tirar a opacity do container). Nos tiles
// (folhas, sem sobreposição) o resultado visual é idêntico e barato (blend direto, sem buffer).
const styles_fundo = { position: 'absolute' as const, top: 0, left: 0, right: 0, bottom: 0, overflow: 'hidden' as const };

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

/** Nome exibido do autor de uma mensagem/citação (mesma lógica dos rótulos dos balões). */
function nomeAutorCitacao(
  c: Pick<Mensagem, 'remetente' | 'geradaPorIa' | 'atendenteNome'>,
  nomeUnidade: string,
): string {
  if (c.remetente === 'PACIENTE') return 'Você';
  if (c.geradaPorIa) return 'Assistente virtual';
  return c.atendenteNome || nomeUnidade || 'Atendente';
}

const LIMIAR_SWIPE = 56; // px de arrasto para disparar o "responder"
const SEGUE_LIVRE = 104; // segue o dedo ~1:1 até aqui; além disso, resistência suave (igual WhatsApp)

/** Toque tátil leve ao cruzar o limiar do swipe (igual WhatsApp). Best-effort (ignora falha). */
const toqueLeve = () => {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {});
};

/** Retângulo da bolha na janela — ancora o menu de contexto (estilo iOS) sobre a mensagem. */
export type AncoraBolha = { x: number; y: number; width: number; height: number };

/**
 * Quebra o texto nas ocorrências do termo (case-insensitive) e envolve cada uma num <Text> com
 * fundo âmbar (destaque da pesquisa, estilo WhatsApp). Sem termo (ou sem ocorrência) devolve o
 * texto puro — custo zero fora do modo busca.
 */
function comDestaque(texto: string, termo: string, styles: ReturnType<typeof criarEstilos>): ReactNode {
  if (!termo) return texto;
  const alvo = texto.toLowerCase();
  const t = termo.toLowerCase();
  let pos = alvo.indexOf(t);
  if (pos < 0) return texto;
  const partes: ReactNode[] = [];
  let i = 0;
  let k = 0;
  while (pos >= 0) {
    if (pos > i) partes.push(texto.slice(i, pos));
    partes.push(
      <Text key={k++} style={styles.buscaDestaque}>
        {texto.slice(pos, pos + termo.length)}
      </Text>,
    );
    i = pos + termo.length;
    pos = alvo.indexOf(t, i);
  }
  if (i < texto.length) partes.push(texto.slice(i));
  return partes;
}

interface BolhaProps {
  m: Mensagem;
  daUnidade: boolean;
  styles: ReturnType<typeof criarEstilos>;
  t: Tema;
  nomeUnidade: string;
  podeResponder: boolean;
  onResponder: (m: Mensagem) => void;
  onMenu: (m: Mensagem, ancora: AncoraBolha) => void;
  onTocarCitacao: (c: Citacao) => void;
  destacado: boolean;
  reenviar: (m: Mensagem) => void;
  /** Termo da pesquisa a destacar no texto (vazio = sem destaque). */
  termoBusca: string;
}

/**
 * Balão de mensagem com "responder" estilo WhatsApp. O swipe roda na THREAD DE UI
 * (react-native-gesture-handler + reanimated worklets) → fluido, sem engasgo. Arrastar pro lado
 * desliza o balão e revela o ícone; ao soltar passando do limiar, dispara a resposta e volta com
 * mola. Segurar abre o menu. Memoizado para NÃO re-renderizar a lista inteira a cada estado.
 */
const BolhaMensagem = memo(function BolhaMensagem({
  m, daUnidade, styles, t, nomeUnidade, podeResponder, onResponder, onMenu, onTocarCitacao, destacado, reenviar, termoBusca,
}: BolhaProps) {
  const tx = useSharedValue(0);
  const cruzou = useSharedValue(false); // já passou do limiar nesta arrastada? (para vibrar uma vez)
  const arrastavel = podeResponder && m.id > 0 && !m.pendente && !m.falha;

  const pan = Gesture.Pan()
    .enabled(arrastavel)
    .activeOffsetX(12) // só ativa no arraste horizontal pra direita; rolar vertical continua funcionando
    .failOffsetY([-14, 14]) // movimento vertical cede para a lista rolar
    .onUpdate((e) => {
      'worklet';
      const x = e.translationX;
      // segue o dedo ~1:1 até SEGUE_LIVRE; além disso, resistência suave (não trava seco, igual WhatsApp)
      tx.value = x <= 0 ? 0 : x <= SEGUE_LIVRE ? x : SEGUE_LIVRE + (x - SEGUE_LIVRE) * 0.55;
      // vibra uma vez ao cruzar o limiar (feedback tátil igual WhatsApp)
      if (!cruzou.value && x >= LIMIAR_SWIPE) {
        cruzou.value = true;
        runOnJS(toqueLeve)();
      } else if (cruzou.value && x < LIMIAR_SWIPE) {
        cruzou.value = false;
      }
    })
    .onEnd((e) => {
      'worklet';
      if (e.translationX >= LIMIAR_SWIPE) runOnJS(onResponder)(m);
      tx.value = withSpring(0, { damping: 16, stiffness: 230, mass: 0.5 });
    })
    .onFinalize(() => {
      'worklet';
      cruzou.value = false;
    });

  const bolhaAnim = useAnimatedStyle(() => ({ transform: [{ translateX: tx.value }] }));
  const iconeAnim = useAnimatedStyle(() => ({
    opacity: interpolate(tx.value, [12, LIMIAR_SWIPE], [0, 1], Extrapolation.CLAMP),
    transform: [
      // acompanha o balão, ficando no vão entre a origem e a bolha (igual WhatsApp), e cresce até o limiar
      { translateX: tx.value * 0.45 },
      { scale: interpolate(tx.value, [10, LIMIAR_SWIPE], [0.4, 1], Extrapolation.CLAMP) },
    ],
  }));
  const podeSegurar = podeResponder && m.id > 0 && !m.pendente && !m.falha;
  const bolhaRef = useRef<View>(null);
  // Segurar: mede a bolha na janela e abre o menu de contexto ancorado sobre ela (estilo iOS).
  const abrirMenu = () => {
    if (!podeSegurar) return;
    toqueLeve();
    bolhaRef.current?.measureInWindow((x, y, width, height) => onMenu(m, { x, y, width, height }));
  };

  return (
    <View style={[styles.bolhaRow, daUnidade ? styles.esquerda : styles.direita]}>
      {/* Ícone de responder revelado ao arrastar (igual WhatsApp). */}
      <Animated.View style={[styles.swipeIcone, iconeAnim]} pointerEvents="none">
        <Ionicons name="arrow-undo" size={17} color={t.brandDeep} />
      </Animated.View>
      <GestureDetector gesture={pan}>
        <Animated.View style={bolhaAnim}>
        <Pressable
          ref={bolhaRef}
          onLongPress={abrirMenu}
          delayLongPress={280}
          style={[
            styles.bolha,
            daUnidade ? styles.bolhaUnidade : styles.bolhaPaciente,
            daUnidade && m.geradaPorIa && styles.bolhaIa,
            destacado && styles.bolhaDestacada,
          ]}>
          {m.respondeA ? (
            <Pressable style={styles.citacaoBloco} onPress={() => onTocarCitacao(m.respondeA!)}>
              <View style={styles.citacaoBarra} />
              <View style={styles.citacaoTexto}>
                <Text style={styles.citacaoNome} numberOfLines={1}>
                  {nomeAutorCitacao(m.respondeA, nomeUnidade)}
                </Text>
                <Text style={styles.citacaoTrecho} numberOfLines={2}>
                  {m.respondeA.texto}
                </Text>
              </View>
            </Pressable>
          ) : null}
          {daUnidade && m.geradaPorIa ? (
            <Text style={styles.assistenteIa}>🤖 Assistente virtual</Text>
          ) : daUnidade && m.atendenteNome ? (
            <Text style={styles.atendente}>{m.atendenteNome}</Text>
          ) : null}
          {!daUnidade && m.responsavelNome ? (
            <View style={styles.viaResp}>
              <Ionicons name="people-outline" size={12} color="#8A5A00" />
              <Text style={styles.viaRespTxt}>via {m.responsavelNome} (responsável)</Text>
            </View>
          ) : null}
          <Text style={styles.texto}>{comDestaque(m.texto, termoBusca, styles)}</Text>
          <View style={styles.rodape}>
            {m.editadaEm ? <Text style={styles.editada}>Editada</Text> : null}
            <Text style={styles.hora}>{horaMsg(m.enviadaEm)}</Text>
            {!daUnidade &&
              (m.falha ? (
                <Ionicons name="alert-circle" size={14} color="#E0524D" />
              ) : m.pendente ? (
                <Ionicons name="time-outline" size={13} color="#7C8C87" />
              ) : (
                <Ionicons name="checkmark-done" size={15} color="#5B6B66" />
              ))}
          </View>
        </Pressable>
        {!daUnidade && m.falha && (
          <Pressable
            style={styles.reenviar}
            onPress={() => reenviar(m)}
            hitSlop={6}
            accessibilityRole="button"
            accessibilityLabel="Reenviar mensagem">
            <Ionicons name="refresh" size={12} color="#E0524D" />
            <Text style={styles.reenviarTxt}>Não enviada. Toque para reenviar</Text>
          </Pressable>
        )}
        </Animated.View>
      </GestureDetector>
    </View>
  );
});

export default function ConversaScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { sessao } = useSessao();
  // Responsável só-leitura no Chat: lê a conversa, mas não envia.
  const podeEnviar = podeLancar(sessao, 'CHAT');

  const [detalhe, setDetalhe] = useState<ChatDetalhe | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [texto, setTexto] = useState('');
  const [digitando, setDigitando] = useState(false);
  const [conexao, setConexao] = useState<EstadoConexao>('offline');
  const [conexaoDetalhe, setConexaoDetalhe] = useState('');
  // Pedido "Falar com humano": enviando (trava o duplo toque). O botão fica desabilitado depois,
  // porque o status deixa de ser ATENDIMENTO_IA (a ação já foi feita).
  const [pedindoHumano, setPedindoHumano] = useState(false);
  // Responder (citação): mensagem sendo respondida, menu do "segurar", e id em destaque ao tocar numa citação.
  const [respondendo, setRespondendo] = useState<Mensagem | null>(null);
  const [menuMsg, setMenuMsg] = useState<Mensagem | null>(null);
  const [menuAnchor, setMenuAnchor] = useState<AncoraBolha | null>(null);
  const [editando, setEditando] = useState<Mensagem | null>(null);
  const [destacado, setDestacado] = useState<number | null>(null);
  // Pesquisar na conversa (estilo WhatsApp iOS): modo busca, termo e ocorrência atual.
  const [buscando, setBuscando] = useState(false);
  const [termoBusca, setTermoBusca] = useState('');
  const [indiceResultado, setIndiceResultado] = useState(0); // 0 = ocorrência MAIS RECENTE
  const buscaRef = useRef<TextInput>(null);
  const idResultadoRef = useRef<number | null>(null); // id da ocorrência atual (reancora ao chegar msg nova)
  const buscandoRef = useRef(false); // espelho p/ callbacks estáveis (ex.: não rolar pro fim na busca)
  // Menu de contexto estilo iOS: a bolha "sobe" (escala) e o menu surge com mola; fundo escurece.
  const janela = useWindowDimensions();
  const ctxProg = useSharedValue(0);
  const ctxBolhaAnim = useAnimatedStyle(() => ({ transform: [{ scale: interpolate(ctxProg.value, [0, 1], [1, 1.035]) }] }));
  const ctxMenuAnim = useAnimatedStyle(() => ({
    opacity: ctxProg.value,
    transform: [{ scale: interpolate(ctxProg.value, [0, 1], [0.92, 1]) }],
  }));
  const scrollRef = useRef<FlashListRef<Mensagem>>(null);
  const inputRef = useRef<TextInput>(null);
  // Espelho das mensagens para callbacks estáveis (ex.: achar o índice da citada p/ scrollToIndex)
  // sem recriar a função a cada nova mensagem (senão re-renderiza todos os balões memoizados).
  const mensagensRef = useRef<Mensagem[]>([]);
  const timerDestaque = useRef<ReturnType<typeof setTimeout> | null>(null);
  const jaCarregou = useRef(false);
  const ultimoSinalDigitando = useRef(0);
  const timerDigitando = useRef<ReturnType<typeof setTimeout> | null>(null);

  // --- Teclado + barras translúcidas (estilo WhatsApp) -------------------------------------------
  // A lista é full-bleed; o cabeçalho e a barra de input ficam ABSOLUTOS por cima (com blur no iOS
  // e translúcido no Android), então as mensagens passam por trás deles. O teclado é seguido em
  // tempo real pelo reanimated: a barra sobe colada no teclado e a lista ganha um espaçador do
  // tamanho do teclado para a última mensagem nunca ficar escondida.
  const { height: kbAltura, progress: kbProgresso } = useReanimatedKeyboardAnimation();
  const [headerH, setHeaderH] = useState(insets.top + 56);
  // Altura só da LINHA da barra (input + enviar / aviso) — medida à parte da área de segurança
  // animada, para não oscilar durante a animação do teclado nem travar alto após um multiline.
  const [barraRowH, setBarraRowH] = useState(48);

  const barraInputAnim = useAnimatedStyle(() => ({
    transform: [{ translateY: -Math.abs(kbAltura.value) }],
  }));
  // Área de segurança (home indicator) na base da barra: insets.bottom com o teclado fechado,
  // encolhe a 0 quando aberto — a barra fica rente ao teclado, sem vão.
  const safeAreaAnim = useAnimatedStyle(
    () => ({ height: Math.max(insets.bottom, 10) * (1 - kbProgresso.value) }),
    [insets.bottom],
  );
  const espacadorAnim = useAnimatedStyle(() => ({ height: Math.abs(kbAltura.value) }));

  const rolarParaFim = (animado = true) =>
    requestAnimationFrame(() => scrollRef.current?.scrollToEnd({ animated: animado }));

  // Rola para o fim quando o teclado COMEÇA a abrir (sincronizado com a animação), mas só se o
  // usuário já estava perto do fim — não arranca quem está lendo o histórico para cima.
  // (O "começa no fim" e o "anda até o fim real no load" agora são do FlashList via
  //  maintainVisibleContentPosition/startRenderingFromBottom — sem a gambiarra de fixar no fim.)
  const pertoDoFim = useRef(true);
  const aoRolar = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const { contentOffset, contentSize, layoutMeasurement } = e.nativeEvent;
    pertoDoFim.current = contentSize.height - (contentOffset.y + layoutMeasurement.height) < 140;
  };
  const rolarSeNoFim = useCallback(() => {
    if (pertoDoFim.current) scrollRef.current?.scrollToEnd({ animated: true });
  }, []);
  useKeyboardHandler(
    {
      // onStart acompanha a abertura (sincronizado); onEnd garante o fim exato caso o espaçador
      // ainda estivesse crescendo quando o onStart rolou.
      onStart: (e) => {
        'worklet';
        if (e.height > 0) runOnJS(rolarSeNoFim)();
      },
      onEnd: (e) => {
        'worklet';
        if (e.height > 0) runOnJS(rolarSeNoFim)();
      },
    },
    [rolarSeNoFim],
  );

  const carregar = useCallback(async () => {
    if (!id) return;
    try {
      if (!jaCarregou.current) setCarregando(true);
      setErro(false);
      const dados = await buscarConversa(id);
      // Preserva mensagens otimistas em voo (pendentes/falha) ao recarregar o servidor.
      setDetalhe((prev) => {
        if (!prev || prev.id !== dados.id) return dados;
        const noServidor = new Set(dados.mensagens.map((mm) => mm.clienteId).filter(Boolean));
        const otimistas = prev.mensagens.filter(
          (mm) => mm.id < 0 && (mm.pendente || mm.falha) && (!mm.clienteId || !noServidor.has(mm.clienteId)),
        );
        return { ...dados, mensagens: [...dados.mensagens, ...otimistas] };
      });
      jaCarregou.current = true;
      rolarParaFim(false);
      // Confirma a entrega das mensagens da unidade que chegaram neste aparelho.
      confirmarEntrega(id);
      // Marca a conversa como lida por mim (limpa o indicador na lista).
      marcarLido(id);
    } catch {
      if (!jaCarregou.current) setErro(true); // silencioso: nao apaga o conteudo ja exibido
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

  // Notificação (app aberto) ou volta ao primeiro plano: re-sincroniza a conversa
  // (o carregar já usa o ref jaCarregou, então não pisca o spinner).
  useAtualizarComPush(() => carregar());

  // Tempo real (WebSocket/STOMP): mensagens, "digitando…" e recibo de leitura.
  useEffect(() => {
    if (!id) return;
    const cancelarMsg = observarMensagens(id, (m) => {
      const ehEdicao = mensagensRef.current.some((x) => x.id === m.id);
      // Mensagem NOVA (não um eco de edição de mensagem antiga): some com "digitando…" na
      // hora e cancela o auto-ocultar. Edição não deve mexer no indicador de digitação.
      if (!ehEdicao) {
        if (timerDigitando.current) {
          clearTimeout(timerDigitando.current);
          timerDigitando.current = null;
        }
        setDigitando(false);
      }
      setDetalhe((d) => {
        if (!d) return d;
        // Já existe (edição de mensagem) → troca a bolha pelo id (atualiza texto/editadaEm no lugar).
        if (ehEdicao) {
          return { ...d, mensagens: d.mensagens.map((x) => (x.id === m.id ? m : x)) };
        }
        // Reconcilia o eco da própria mensagem otimista (temporária, id < 0) pelo clienteId
        // (1-para-1; cai no texto só se o eco não trouxer clienteId).
        const ehOtimista = (x: Mensagem) =>
          x.id < 0 && (m.clienteId ? x.clienteId === m.clienteId : x.texto === m.texto);
        const base = m.remetente === 'PACIENTE' ? d.mensagens.filter((x) => !ehOtimista(x)) : d.mensagens;
        return { ...d, mensagens: [...base, m] };
      });
      // Edição não mexe em entrega/leitura nem rola pro fim (a mensagem antiga só mudou no lugar).
      if (!ehEdicao) {
        // Mensagem da unidade chegou enquanto estou na conversa: confirma entrega E marca lida.
        if (m.remetente === 'UNIDADE') {
          confirmarEntrega(id);
          marcarLido(id);
        }
        // Durante a pesquisa não puxa pro fim (mantém o usuário na ocorrência que está vendo).
        if (!buscandoRef.current) rolarParaFim();
      }
    });
    const cancelarDig = observarDigitando(id, (e) => {
      if (e.de !== 'UNIDADE') return; // só mostra quando o outro lado digita
      setDigitando(true);
      if (timerDigitando.current) clearTimeout(timerDigitando.current);
      // A IA pode levar até ~20s pensando; mantém "digitando…" visível com folga
      // (a chegada da mensagem já limpa o indicador antes disso).
      timerDigitando.current = setTimeout(() => setDigitando(false), 25000);
    });
    const cancelarConexao = observarConexao((e, d) => {
      setConexao(e);
      setConexaoDetalhe(d);
    });
    // Atendente assumiu/transferiu: atualiza o nome no cabeçalho ao vivo (sem esperar
    // um novo foco da tela). null = ninguém responsável no momento.
    const cancelarResp = observarResponsavel(id, (ev) => {
      setDetalhe((d) => (d ? { ...d, responsavelNome: ev.responsavelNome } : d));
    });
    // Mudança de status ao vivo: mantém o cabeçalho e o botão "Falar com humano" em sincronia
    // (habilitado só em ATENDIMENTO_IA) sem precisar sair e voltar na conversa.
    const cancelarStatus = observarStatus(id, (ev) => {
      setDetalhe((d) => (d ? { ...d, status: ev.status, statusDescricao: ev.statusDescricao } : d));
    });
    return () => {
      cancelarMsg();
      cancelarDig();
      cancelarConexao();
      cancelarResp();
      cancelarStatus();
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

  // Tenta enviar com retentativas (conexão ruim); após esgotar, marca "falha" (reenviar).
  const tentarEnviar = useCallback(
    async (tempId: number, clienteId: string, conteudo: string, respondeAId?: number) => {
      if (!id) return;
      const ATRASOS = [2000, 4000, 8000, 16000]; // tentativa inicial + 4 retentativas ≈ 30s
      setDetalhe((d) =>
        d
          ? {
              ...d,
              mensagens: d.mensagens.map((m) =>
                m.id === tempId ? { ...m, pendente: true, falha: false } : m,
              ),
            }
          : d,
      );
      for (let i = 0; i <= ATRASOS.length; i++) {
        try {
          // clienteId torna o reenvio idempotente (não duplica se a resposta se perdeu).
          const atualizado = await enviarMensagemPaciente(id, conteudo, clienteId, respondeAId);
          // Não substitui a lista inteira (preservaria outras mensagens ainda pendentes);
          // só marca ESTA como enviada (2 checks) e atualiza o status.
          setDetalhe((d) =>
            d
              ? {
                  ...d,
                  status: atualizado.status,
                  statusDescricao: atualizado.statusDescricao,
                  mensagens: d.mensagens.map((m) =>
                    m.id === tempId ? { ...m, pendente: false, falha: false } : m,
                  ),
                }
              : d,
          );
          rolarParaFim();
          return;
        } catch {
          if (i === ATRASOS.length) {
            setDetalhe((d) =>
              d
                ? {
                    ...d,
                    mensagens: d.mensagens.map((m) =>
                      m.id === tempId ? { ...m, pendente: false, falha: true } : m,
                    ),
                  }
                : d,
            );
            return;
          }
          await new Promise((r) => setTimeout(r, ATRASOS[i]));
        }
      }
    },
    [id],
  );

  const enviar = () => {
    const limpo = texto.trim();
    if (!limpo || !id) return;
    // Modo edição (estilo WhatsApp): troca o texto da mensagem no lugar + marca "Editada".
    if (editando) {
      const alvo = editando;
      setEditando(null);
      setTexto('');
      if (limpo !== alvo.texto) {
        // Guarda o texto/selo originais para reverter se o servidor recusar (fora dos 15 min, rede…).
        const textoOriginal = alvo.texto;
        const editadaEmOriginal = alvo.editadaEm ?? null;
        setDetalhe((d) =>
          d
            ? {
                ...d,
                mensagens: d.mensagens.map((x) =>
                  x.id === alvo.id ? { ...x, texto: limpo, editadaEm: new Date().toISOString() } : x,
                ),
              }
            : d,
        );
        editarMensagemPaciente(id, alvo.id, limpo).catch(() => {
          // Falhou: desfaz a edição otimista NESTA mensagem (por id) e avisa — nunca deixa
          // a bolha presa num texto que não chegou ao servidor nem reverte em silêncio.
          setDetalhe((d) =>
            d
              ? {
                  ...d,
                  mensagens: d.mensagens.map((x) =>
                    x.id === alvo.id ? { ...x, texto: textoOriginal, editadaEm: editadaEmOriginal } : x,
                  ),
                }
              : d,
          );
          Alert.alert('Não foi possível editar', 'A mensagem não pôde ser editada. Tente novamente.');
        });
      }
      return;
    }
    // Otimista: mostra a mensagem na hora com o "relógio" (pendente) até o servidor confirmar.
    const tempId = -Date.now();
    const clienteId = novoClienteId();
    const citada = respondendo; // mensagem citada (ou null se não é uma resposta)
    const otimista: Mensagem = {
      id: tempId,
      remetente: 'PACIENTE',
      texto: limpo,
      enviadaEm: new Date().toISOString(),
      lida: false,
      entregue: false,
      pendente: true,
      clienteId,
      respondeA: citada
        ? {
            id: citada.id,
            remetente: citada.remetente,
            texto: citada.texto,
            geradaPorIa: citada.geradaPorIa,
            atendenteNome: citada.atendenteNome,
          }
        : null,
    };
    setDetalhe((d) => (d ? { ...d, mensagens: [...d.mensagens, otimista] } : d));
    setTexto('');
    setRespondendo(null);
    rolarParaFim();
    tentarEnviar(tempId, clienteId, limpo, citada?.id);
  };

  const reenviar = useCallback(
    (m: Mensagem) => {
      if (m.clienteId) tentarEnviar(m.id, m.clienteId, m.texto, m.respondeA?.id);
    },
    [tentarEnviar],
  );

  const JANELA_EDICAO_MS = 15 * 60 * 1000;
  // Só edita a PRÓPRIA mensagem (do paciente), recente (15 min) e já confirmada pelo servidor.
  const editavel = (m: Mensagem | null) =>
    !!m &&
    m.remetente === 'PACIENTE' &&
    m.id > 0 &&
    !m.pendente &&
    !m.falha &&
    Date.now() - new Date(m.enviadaEm).getTime() < JANELA_EDICAO_MS;

  const abrirMenuMsg = useCallback(
    (m: Mensagem, ancora: AncoraBolha) => {
      setMenuMsg(m);
      setMenuAnchor(ancora);
      ctxProg.value = 0;
      ctxProg.value = withSpring(1, { damping: 18, stiffness: 240, mass: 0.6 });
    },
    [ctxProg],
  );

  const fecharMenu = useCallback(() => {
    setMenuMsg(null);
    setMenuAnchor(null);
  }, []);

  // Entra no modo "responder": guarda a mensagem citada, sai do modo editar, fecha o menu e foca.
  const responderMensagem = useCallback(
    (m: Mensagem) => {
      setEditando(null);
      setRespondendo(m);
      fecharMenu();
      requestAnimationFrame(() => inputRef.current?.focus());
    },
    [fecharMenu],
  );

  // Entra no modo "editar": preenche o campo com o texto atual, sai do modo responder, fecha o menu e foca.
  const editarMensagem = useCallback(
    (m: Mensagem) => {
      setRespondendo(null);
      setEditando(m);
      setTexto(m.texto);
      fecharMenu();
      requestAnimationFrame(() => inputRef.current?.focus());
    },
    [fecharMenu],
  );

  const cancelarEdicao = useCallback(() => {
    setEditando(null);
    setTexto('');
  }, []);

  // Toca na citação dentro de um balão: rola até a mensagem original e a destaca por um instante.
  // Com a lista virtualizada a original pode estar desmontada, então rolamos por ÍNDICE
  // (scrollToIndex) — não por posição Y guardada (que só existe para itens já renderizados).
  const tocarCitacao = useCallback((c: Citacao) => {
    const idx = mensagensRef.current.findIndex((m) => m.id === c.id);
    if (idx >= 0) {
      scrollRef.current?.scrollToIndex({ index: idx, animated: true, viewPosition: 0.35 });
    }
    setDestacado(c.id);
    if (timerDestaque.current) clearTimeout(timerDestaque.current);
    timerDestaque.current = setTimeout(() => setDestacado(null), 1400);
  }, []);

  useEffect(
    () => () => {
      if (timerDestaque.current) clearTimeout(timerDestaque.current);
    },
    [],
  );

  // "Falar com humano": tira a conversa do modo assistente virtual. Idempotente e travado
  // contra duplo toque; a mensagem de confirmação chega na conversa (retorno + tempo real).
  const pedirHumano = useCallback(async () => {
    if (!id || pedindoHumano) return;
    setPedindoHumano(true);
    try {
      const atualizado = await falarComHumano(id);
      // Mescla o estado do servidor preservando mensagens otimistas em voo (mesmo padrão do carregar).
      setDetalhe((prev) => {
        if (!prev || prev.id !== atualizado.id) return atualizado;
        const noServidor = new Set(atualizado.mensagens.map((mm) => mm.clienteId).filter(Boolean));
        const otimistas = prev.mensagens.filter(
          (mm) => mm.id < 0 && (mm.pendente || mm.falha) && (!mm.clienteId || !noServidor.has(mm.clienteId)),
        );
        return { ...atualizado, mensagens: [...atualizado.mensagens, ...otimistas] };
      });
      rolarParaFim();
    } catch {
      // best-effort; o paciente pode tocar de novo (é idempotente no backend)
    } finally {
      setPedindoHumano(false);
    }
  }, [id, pedindoHumano]);

  // A IA está atendendo agora? (habilita o botão "Falar com humano"; ao acionar, o status muda e some.)
  const iaAtendendo = detalhe?.status === 'ATENDIMENTO_IA';
  const mensagens = detalhe?.mensagens ?? [];
  mensagensRef.current = mensagens; // espelho p/ callbacks estáveis (tocarCitacao)
  buscandoRef.current = buscando; // espelho p/ o handler de mensagem nova não rolar pro fim durante a busca
  const nomeUnidade = detalhe?.unidadeSaude.nome ?? 'Atendente';

  // --- Pesquisar na conversa (estilo WhatsApp iOS) ---
  const termoNorm = termoBusca.trim().toLowerCase();
  // Índices (em `mensagens`) das mensagens que contêm o termo, da MAIS RECENTE para a mais antiga:
  // assim resultados[0] é a ocorrência mais nova (igual WhatsApp, que abre na mais recente).
  const resultados = useMemo(() => {
    if (!termoNorm) return [] as number[];
    const achados: number[] = [];
    for (let i = mensagens.length - 1; i >= 0; i--) {
      if (mensagens[i].texto?.toLowerCase().includes(termoNorm)) achados.push(i);
    }
    return achados;
  }, [mensagens, termoNorm]);
  const totalResultados = resultados.length;
  const temTermo = termoNorm.length > 0;
  const indiceSeguro = Math.min(indiceResultado, Math.max(totalResultados - 1, 0));
  const semMaisNovo = indiceSeguro <= 0; // já na ocorrência mais recente → seta "baixo" desligada
  const semMaisVelho = indiceSeguro >= totalResultados - 1; // já na mais antiga → seta "cima" desligada
  const contadorBusca = !temTermo
    ? ''
    : totalResultados === 0
      ? 'Nenhum resultado'
      : `${indiceSeguro + 1} de ${totalResultados} ${totalResultados === 1 ? 'resultado' : 'resultados'}`;

  const rolarParaResultado = useCallback((idxMensagem: number) => {
    requestAnimationFrame(() =>
      scrollRef.current?.scrollToIndex({ index: idxMensagem, animated: true, viewPosition: 0.5 }),
    );
    // A bolha da ocorrência atual "acende" por um instante (igual WhatsApp), marcando qual é.
    const id = mensagensRef.current[idxMensagem]?.id;
    if (id != null) {
      idResultadoRef.current = id;
      setDestacado(id);
      if (timerDestaque.current) clearTimeout(timerDestaque.current);
      timerDestaque.current = setTimeout(() => setDestacado(null), 1800);
    }
  }, []);

  // Ao mudar o termo (ou abrir a busca): volta para a ocorrência mais recente e rola até ela.
  // Depende só de termoNorm/buscando (não de `resultados`) para NÃO resetar a posição quando chega
  // uma mensagem nova durante a busca — `resultados` é derivado no mesmo render, então está fresco.
  useEffect(() => {
    if (!buscando) return;
    setIndiceResultado(0);
    idResultadoRef.current = null;
    if (resultados.length > 0) rolarParaResultado(resultados[0]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [termoNorm, buscando]);

  // Reancora a posição na MESMA ocorrência quando a lista muda (ex.: chega mensagem nova durante a
  // busca): sem isso o índice posicional passaria a apontar outra ocorrência e o contador divergiria.
  useEffect(() => {
    if (!buscando || idResultadoRef.current == null) return;
    const idx = mensagensRef.current.findIndex((m) => m.id === idResultadoRef.current);
    const pos = idx >= 0 ? resultados.indexOf(idx) : -1;
    if (pos >= 0) setIndiceResultado(pos);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resultados]);

  // Setas ^/v: +1 = ocorrência mais ANTIGA (sobe), -1 = mais NOVA (desce).
  const irResultado = useCallback(
    (delta: number) => {
      if (resultados.length === 0) return;
      const novo = Math.min(Math.max(indiceSeguro + delta, 0), resultados.length - 1);
      if (novo === indiceSeguro) return;
      setIndiceResultado(novo);
      rolarParaResultado(resultados[novo]);
    },
    [resultados, indiceSeguro, rolarParaResultado],
  );

  const abrirBusca = useCallback(() => {
    setRespondendo(null);
    setBuscando(true);
    setTermoBusca('');
    setIndiceResultado(0);
    requestAnimationFrame(() => buscaRef.current?.focus());
  }, []);

  const fecharBusca = useCallback(() => {
    setBuscando(false);
    setTermoBusca('');
    setIndiceResultado(0);
    idResultadoRef.current = null;
    setRespondendo(null); // descarta uma citação que um swipe acidental tenha deixado durante a busca
    Keyboard.dismiss();
  }, []);

  // Android: o "voltar" do sistema fecha a busca antes de sair da tela.
  useEffect(() => {
    if (!buscando) return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      fecharBusca();
      return true;
    });
    return () => sub.remove();
  }, [buscando, fecharBusca]);

  // Render de cada mensagem na lista. O separador de dia compara com a mensagem anterior (índice).
  const renderItem = useCallback(
    ({ item: m, index: i }: ListRenderItemInfo<Mensagem>) => {
      const anterior = mensagens[i - 1];
      const mostrarDia = i === 0 || (!!anterior && rotuloDia(anterior.enviadaEm) !== rotuloDia(m.enviadaEm));
      const daUnidade = m.remetente === 'UNIDADE';
      return (
        <View>
          {mostrarDia && (
            <View style={styles.diaWrap}>
              <Text style={styles.dia}>{rotuloDia(m.enviadaEm)}</Text>
            </View>
          )}
          <BolhaMensagem
            m={m}
            daUnidade={daUnidade}
            styles={styles}
            t={t}
            nomeUnidade={nomeUnidade}
            podeResponder={podeEnviar && !buscando}
            onResponder={responderMensagem}
            onMenu={abrirMenuMsg}
            onTocarCitacao={tocarCitacao}
            destacado={destacado === m.id}
            reenviar={reenviar}
            termoBusca={buscando ? termoNorm : ''}
          />
        </View>
      );
    },
    [mensagens, styles, t, nomeUnidade, podeEnviar, destacado, responderMensagem, abrirMenuMsg, tocarCitacao, reenviar, buscando, termoNorm],
  );
  const keyExtractor = useCallback((m: Mensagem) => String(m.id), []);

  // Conteúdo do cabeçalho (reaproveitado dentro da barra translúcida).
  const conteudoHeader = (
    <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
      <Pressable
        style={({ pressed }) => [styles.back, pressed && styles.backPressed]}
        onPress={() => router.back()}
        hitSlop={10}
        accessibilityRole="button"
        accessibilityLabel="Voltar">
        <Ionicons name="chevron-back" size={26} color={t.ink} />
      </Pressable>
      {/* Avatar + nome: toca para abrir o perfil da unidade (estilo WhatsApp). */}
      <Pressable
        style={({ pressed }) => [styles.headerInfo, pressed && styles.headerInfoPressed]}
        onPress={() => detalhe && router.push({ pathname: '/unidade/[id]', params: { id: detalhe.unidadeSaude.id } })}
        disabled={!detalhe}
        accessibilityRole="button"
        accessibilityLabel="Ver dados da unidade">
        <View style={styles.avatar}>
          {detalhe ? (
            <Text style={styles.avatarTxt}>{iniciais(detalhe.unidadeSaude.nome)}</Text>
          ) : (
            <Ionicons name="medical" size={18} color={t.glow} />
          )}
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.contatoNome} numberOfLines={1}>
            {detalhe?.unidadeSaude.nome ?? 'Conversa'}
          </Text>
          {digitando ? (
            <Text style={styles.digitando} numberOfLines={1}>
              escrevendo…
            </Text>
          ) : conexao === 'conectado' ? (
            <Text style={styles.contatoStatus} numberOfLines={1}>
              {detalhe?.responsavelNome
                ? `com ${detalhe.responsavelNome}`
                : (detalhe?.statusDescricao ?? 'Carregando…')}
            </Text>
          ) : (
            <Text style={styles.contatoStatus} numberOfLines={1}>
              {conexao === 'conectando'
                ? 'conectando ao vivo…'
                : `sem tempo real${conexaoDetalhe ? ' · ' + conexaoDetalhe : ''}`}
            </Text>
          )}
        </View>
      </Pressable>
      {/* Lupa: abre a pesquisa dentro da conversa (estilo WhatsApp). */}
      {detalhe ? (
        <Pressable
          style={({ pressed }) => [styles.headerLupa, pressed && styles.headerLupaPressed]}
          onPress={abrirBusca}
          hitSlop={10}
          accessibilityRole="button"
          accessibilityLabel="Pesquisar na conversa">
          <Ionicons name="search" size={20} color={t.ink} />
        </Pressable>
      ) : null}
      {/* Falar com humano: no topo, à direita. Só habilitado enquanto a IA está atendendo;
          depois de acionado (o status muda) fica desabilitado — a ação já foi feita. */}
      {detalhe && podeEnviar ? (
        <Pressable
          style={({ pressed }) => [
            styles.humanoHeader,
            pressed && iaAtendendo && styles.humanoHeaderPressed,
            (!iaAtendendo || pedindoHumano) && styles.humanoHeaderOff,
          ]}
          onPress={pedirHumano}
          disabled={!iaAtendendo || pedindoHumano}
          hitSlop={10}
          accessibilityRole="button"
          accessibilityState={{ disabled: !iaAtendendo || pedindoHumano }}
          accessibilityLabel="Falar com um atendente humano">
          {pedindoHumano ? (
            <ActivityIndicator size="small" color={t.brandDeep} />
          ) : (
            <Ionicons name="person-outline" size={15} color={iaAtendendo ? t.brandDeep : t.muted} />
          )}
          <Text
            style={[styles.humanoHeaderTxt, !iaAtendendo && styles.humanoHeaderTxtOff]}
            numberOfLines={1}>
            Falar com humano
          </Text>
        </Pressable>
      ) : null}
    </View>
  );

  // Campo de pesquisa que SUBSTITUI o cabeçalho no modo busca (lupa + campo + limpar + fechar).
  const conteudoBusca = (
    <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
      <View style={styles.buscaCampo}>
        <Ionicons name="search" size={18} color={t.muted} />
        <TextInput
          ref={buscaRef}
          style={styles.buscaInput}
          value={termoBusca}
          onChangeText={setTermoBusca}
          placeholder="Pesquisar"
          placeholderTextColor={t.muted}
          autoFocus
          autoCorrect={false}
          returnKeyType="search"
          accessibilityLabel="Pesquisar na conversa"
        />
        {termoBusca.length > 0 ? (
          <Pressable onPress={() => setTermoBusca('')} hitSlop={8} accessibilityRole="button" accessibilityLabel="Limpar pesquisa">
            <Ionicons name="close-circle" size={18} color={t.muted} />
          </Pressable>
        ) : null}
      </View>
      <Pressable
        style={({ pressed }) => [styles.buscaFechar, pressed && styles.headerLupaPressed]}
        onPress={fecharBusca}
        hitSlop={8}
        accessibilityRole="button"
        accessibilityLabel="Fechar pesquisa">
        <Ionicons name="close" size={22} color={t.ink} />
      </Pressable>
    </View>
  );

  return (
    <View style={styles.screen}>
      {/* Fundo de ícones de saúde cobrindo a tela inteira (igual ao padrão do WhatsApp). */}
      <FundoSaude />
      {/* Lista (full-bleed) / estados. Renderizada ANTES das barras para o blur do iOS atualizar. */}
      {carregando ? (
        <View style={[styles.estado, { paddingTop: headerH }]}>
          <ActivityIndicator color={t.brand} />
          <Text style={styles.estadoTxt}>Carregando conversa…</Text>
        </View>
      ) : erro ? (
        <View style={[styles.estado, { paddingTop: headerH }]}>
          <Ionicons name="cloud-offline-outline" size={28} color={t.muted} />
          <Text style={styles.estadoTitulo}>Não foi possível abrir</Text>
          <Pressable style={styles.estadoBtn} onPress={carregar}>
            <Ionicons name="refresh" size={16} color="#fff" />
            <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
          </Pressable>
        </View>
      ) : (
        <FlashList
          ref={scrollRef}
          style={styles.mensagens}
          data={mensagens}
          keyExtractor={keyExtractor}
          renderItem={renderItem}
          extraData={destacado}
          ListHeaderComponent={
            <View style={styles.avisoWrap}>
              <Text style={styles.aviso}>🔒 Conversa protegida com a sua unidade de saúde</Text>
            </View>
          }
          ListFooterComponent={<Animated.View style={espacadorAnim} />}
          contentContainerStyle={{
            paddingHorizontal: 14,
            paddingTop: headerH + 6,
            paddingBottom: barraRowH + Math.max(insets.bottom, 10) + 8,
          }}
          // Chat: começa no fim e, ao chegar msg nova, rola pro fim só se o usuário já estava perto
          // (não arranca quem lê o histórico). Tudo nativo do FlashList v2 — sem gambiarra.
          maintainVisibleContentPosition={{ startRenderingFromBottom: true, autoscrollToBottomThreshold: 0.2 }}
          onScrollEndDrag={aoRolar}
          onMomentumScrollEnd={aoRolar}
          keyboardDismissMode="interactive"
          keyboardShouldPersistTaps="handled"
          contentInsetAdjustmentBehavior="never"
          automaticallyAdjustKeyboardInsets={false}
          showsVerticalScrollIndicator={false}
        />
      )}

      {/* Cabeçalho translúcido (blur no iOS, translúcido no Android). */}
      <View
        style={styles.headerWrap}
        onLayout={(e) => setHeaderH(Math.round(e.nativeEvent.layout.height))}>
        {/* Blur nativo só no iOS. No Android o blur do expo-blur re-borra o conteúdo que rola atrás a
            cada frame (pesa ~15ms/frame no tablet fraco); o overlay sólido (0.78) já dá o fundo. */}
        {Platform.OS === 'ios' && <BlurView intensity={48} tint="light" style={StyleSheet.absoluteFill} />}
        <View style={styles.barOverlayHeader} />
        {buscando ? conteudoBusca : conteudoHeader}
      </View>

      {/* Modo pesquisa: navega entre as ocorrências (pílula ^/v + contador), colado no teclado. */}
      {!carregando && !erro && buscando && (
        <Animated.View style={[styles.barraFlutuante, barraInputAnim]}>
          {/* Fundo de toolbar (igual WhatsApp): a pílula ^/v e o contador ficam SOBRE a barra, não soltos. */}
          {Platform.OS === 'ios' && <BlurView intensity={56} tint="light" style={StyleSheet.absoluteFill} />}
          <View style={styles.buscaBarFundo} />
          <View style={styles.buscaNav} onLayout={(e) => setBarraRowH(Math.round(e.nativeEvent.layout.height))}>
            <View style={styles.buscaNavPilula}>
              <Pressable
                style={styles.buscaNavBtn}
                onPress={() => irResultado(1)}
                disabled={!temTermo || semMaisVelho}
                hitSlop={6}
                accessibilityRole="button"
                accessibilityState={{ disabled: !temTermo || semMaisVelho }}
                accessibilityLabel="Ocorrência anterior">
                <Ionicons name="chevron-up" size={22} color={!temTermo || semMaisVelho ? t.muted : t.ink} />
              </Pressable>
              <View style={styles.buscaNavDivisor} />
              <Pressable
                style={styles.buscaNavBtn}
                onPress={() => irResultado(-1)}
                disabled={!temTermo || semMaisNovo}
                hitSlop={6}
                accessibilityRole="button"
                accessibilityState={{ disabled: !temTermo || semMaisNovo }}
                accessibilityLabel="Próxima ocorrência">
                <Ionicons name="chevron-down" size={22} color={!temTermo || semMaisNovo ? t.muted : t.ink} />
              </Pressable>
            </View>
            <Text style={styles.buscaContador}>{contadorBusca}</Text>
          </View>
          <Animated.View style={safeAreaAnim} />
        </Animated.View>
      )}

      {/* Barra de digitação: translúcida e COLADA no teclado (sobe junto). */}
      {!carregando && !erro && !buscando && podeEnviar && (
        <Animated.View style={[styles.barraFlutuante, barraInputAnim]}>
          <View onLayout={(e) => setBarraRowH(Math.round(e.nativeEvent.layout.height))}>
            {/* Prévia: modo EDITAR (estilo WhatsApp) ou RESPONDER (citação). */}
            {editando ? (
              <View style={styles.respostaFaixa}>
                <Ionicons name="create-outline" size={18} color={t.brandDeep} style={{ marginRight: 2 }} />
                <View style={{ flex: 1 }}>
                  <Text style={styles.respostaNome} numberOfLines={1}>
                    Editar mensagem
                  </Text>
                  <Text style={styles.respostaTrecho} numberOfLines={3}>
                    {editando.texto}
                  </Text>
                </View>
                <Pressable
                  onPress={cancelarEdicao}
                  hitSlop={8}
                  accessibilityRole="button"
                  accessibilityLabel="Cancelar edição">
                  <Ionicons name="close" size={20} color={t.muted} />
                </Pressable>
              </View>
            ) : respondendo ? (
              <View style={styles.respostaFaixa}>
                <View style={styles.respostaBarra} />
                <View style={{ flex: 1 }}>
                  <Text style={styles.respostaNome} numberOfLines={1}>
                    {nomeAutorCitacao(respondendo, detalhe?.unidadeSaude.nome ?? 'Atendente')}
                  </Text>
                  <Text style={styles.respostaTrecho} numberOfLines={3}>
                    {respondendo.texto}
                  </Text>
                </View>
                <Pressable
                  onPress={() => setRespondendo(null)}
                  hitSlop={8}
                  accessibilityRole="button"
                  accessibilityLabel="Cancelar resposta">
                  <Ionicons name="close" size={20} color={t.muted} />
                </Pressable>
              </View>
            ) : null}
            <View style={styles.inputBar}>
              <View style={styles.inputWrap}>
                <TextInput
                  ref={inputRef}
                  style={styles.input}
                  value={texto}
                  onChangeText={aoDigitar}
                  placeholder="Mensagem"
                  placeholderTextColor="#9AAAA5"
                  multiline
                />
              </View>
              <Pressable
                style={[styles.enviar, !texto.trim() && styles.enviarDesativado]}
                onPress={enviar}
                disabled={!texto.trim()}
                accessibilityRole="button"
                accessibilityState={{ disabled: !texto.trim() }}
                accessibilityLabel={editando ? 'Salvar edição' : 'Enviar mensagem'}>
                <Ionicons name={editando ? 'checkmark' : 'send'} size={editando ? 23 : 19} color="#fff" />
              </Pressable>
            </View>
          </View>
          <Animated.View style={safeAreaAnim} />
        </Animated.View>
      )}
      {!carregando && !erro && !buscando && !podeEnviar && (
        <View style={styles.barraWrap}>
          {Platform.OS === 'ios' && <BlurView intensity={56} tint="light" style={StyleSheet.absoluteFill} />}
          <View style={styles.barOverlayInput} />
          <View
            style={styles.somenteLeitura}
            onLayout={(e) => setBarraRowH(Math.round(e.nativeEvent.layout.height))}>
            <Ionicons name="eye-outline" size={15} color={t.muted} />
            <Text style={styles.somenteLeituraTxt}>
              Você pode ler esta conversa, mas não enviar mensagens.
            </Text>
          </View>
          <View style={{ height: Math.max(insets.bottom, 10) }} />
        </View>
      )}

      {/* Menu de contexto estilo iOS: segurar a mensagem → fundo escurece, a bolha "sobe" e o menu surge. */}
      <Modal
        visible={!!menuMsg && !!menuAnchor}
        transparent
        animationType="fade"
        statusBarTranslucent
        onRequestClose={fecharMenu}>
        {menuMsg && menuAnchor
          ? (() => {
              const daUnidadeMenu = menuMsg.remetente === 'UNIDADE';
              const comEditar = editavel(menuMsg);
              const alturaMenu = (comEditar ? 2 : 1) * 50 + 8;
              const cabeAbaixo =
                menuAnchor.y + menuAnchor.height + 10 + alturaMenu < janela.height - (insets.bottom + 20);
              const topoMenu = cabeAbaixo
                ? menuAnchor.y + menuAnchor.height + 8
                : Math.max(insets.top + 8, menuAnchor.y - 8 - alturaMenu);
              const lado = daUnidadeMenu
                ? { left: Math.max(10, menuAnchor.x) }
                : { right: Math.max(10, janela.width - (menuAnchor.x + menuAnchor.width)) };
              return (
                <Pressable style={styles.ctxBackdrop} onPress={fecharMenu}>
                  {Platform.OS === 'ios' && <BlurView intensity={16} tint="dark" style={StyleSheet.absoluteFill} />}
                  {/* Cópia da bolha no mesmo lugar, com leve "pop" (a mensagem "sobe"). */}
                  <Animated.View
                    pointerEvents="none"
                    style={[
                      { position: 'absolute', top: menuAnchor.y, left: menuAnchor.x, width: menuAnchor.width },
                      ctxBolhaAnim,
                    ]}>
                    <View
                      style={[
                        styles.bolha,
                        daUnidadeMenu ? styles.bolhaUnidade : styles.bolhaPaciente,
                        daUnidadeMenu && menuMsg.geradaPorIa && styles.bolhaIa,
                      ]}>
                      <Text style={styles.texto}>{menuMsg.texto}</Text>
                      <View style={styles.rodape}>
                        {menuMsg.editadaEm ? <Text style={styles.editada}>Editada</Text> : null}
                        <Text style={styles.hora}>{horaMsg(menuMsg.enviadaEm)}</Text>
                      </View>
                    </View>
                  </Animated.View>
                  {/* Menu flutuante (ações), ancorado abaixo/acima da bolha. */}
                  <Animated.View style={[styles.ctxMenu, { top: topoMenu }, lado, ctxMenuAnim]}>
                    <Pressable
                      style={({ pressed }) => [styles.ctxItem, pressed && styles.ctxItemPress]}
                      onPress={() => responderMensagem(menuMsg)}
                      accessibilityRole="button"
                      accessibilityLabel="Responder a esta mensagem">
                      <Text style={styles.ctxItemTxt}>Responder</Text>
                      <Ionicons name="arrow-undo-outline" size={20} color={t.ink} />
                    </Pressable>
                    {comEditar ? (
                      <>
                        <View style={styles.ctxSep} />
                        <Pressable
                          style={({ pressed }) => [styles.ctxItem, pressed && styles.ctxItemPress]}
                          onPress={() => editarMensagem(menuMsg)}
                          accessibilityRole="button"
                          accessibilityLabel="Editar esta mensagem">
                          <Text style={styles.ctxItemTxt}>Editar</Text>
                          <Ionicons name="create-outline" size={20} color={t.ink} />
                        </Pressable>
                      </>
                    ) : null}
                  </Animated.View>
                </Pressable>
              );
            })()
          : null}
      </Modal>
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    screen: { flex: 1, backgroundColor: t.brandTint },

    mensagens: { flex: 1 },
    mensagensContent: { paddingHorizontal: 14 },
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
    bolhaRow: { position: 'relative', marginBottom: 8, maxWidth: '82%' },
    esquerda: { alignSelf: 'flex-start' },
    direita: { alignSelf: 'flex-end' },
    bolha: { borderRadius: 16, paddingHorizontal: 12, paddingVertical: 8 },
    // "Acende" âmbar: ao navegar na busca (ou tocar numa citação), a bolha atual fica com fundo
    // âmbar por um instante. Sobrepõe o fundo branco/verde (visível nas duas); some via o timer do
    // `destacado`. Sem overlay/overflow (que quebravam o texto das bolhas verdes no Android).
    bolhaDestacada: { backgroundColor: '#F6D592' },
    // Ícone de "responder" revelado ao arrastar o balão (fica atrás, à esquerda).
    swipeIcone: {
      position: 'absolute',
      left: 6,
      top: 0,
      bottom: 0,
      width: 26,
      alignItems: 'center',
      justifyContent: 'center',
    },
    // Bloco da mensagem citada dentro do balão (feature responder).
    citacaoBloco: {
      flexDirection: 'row',
      backgroundColor: alpha(t.ink, 0.06),
      borderRadius: 8,
      overflow: 'hidden',
      marginBottom: 5,
    },
    citacaoBarra: { width: 3.5, backgroundColor: t.brand },
    citacaoTexto: { flex: 1, paddingVertical: 5, paddingHorizontal: 8 },
    citacaoNome: { fontSize: 12, fontWeight: '700', color: t.brandDeep, marginBottom: 1 },
    citacaoTrecho: { fontSize: 12.5, color: t.muted, lineHeight: 16 },
    bolhaUnidade: {
      backgroundColor: t.surface,
      borderTopLeftRadius: 4,
      borderWidth: 1,
      borderColor: t.line,
    },
    bolhaPaciente: { backgroundColor: '#A6E1CE', borderTopRightRadius: 4 },
    // Bolha da IA: fundo branco como as demais + borda na cor da marca para sinalizar que é IA.
    bolhaIa: { backgroundColor: t.surface, borderColor: t.brand, borderWidth: 1.5 },
    assistenteIa: { fontSize: 11.5, fontWeight: '700', color: t.brandDeep, marginBottom: 2 },
    atendente: { fontSize: 11.5, fontWeight: '700', color: t.brandDeep, marginBottom: 2 },
    viaResp: { flexDirection: 'row', alignItems: 'center', gap: 4, marginBottom: 2 },
    viaRespTxt: { fontSize: 11.5, fontWeight: '700', color: '#8A5A00' },
    texto: { fontSize: 14.5, color: t.ink, lineHeight: 20 },
    rodape: { flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: 3, marginTop: 3 },
    hora: { fontSize: 10.5, color: '#7C8C87' },
    reenviar: { flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-end', marginTop: 3, paddingHorizontal: 2 },
    reenviarTxt: { fontSize: 11, color: '#E0524D', fontWeight: '600' },

    // Prévia da mensagem citada acima do input (feature responder): cartão branco flutuante.
    respostaFaixa: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      marginHorizontal: 12,
      marginBottom: 6,
      paddingVertical: 7,
      paddingHorizontal: 10,
      backgroundColor: t.surface,
      borderRadius: 12,
      shadowColor: '#0B2E29',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.12,
      shadowRadius: 6,
      elevation: 3,
    },
    respostaBarra: { width: 3.5, alignSelf: 'stretch', minHeight: 30, borderRadius: 2, backgroundColor: t.brand },
    respostaNome: { fontSize: 12.5, fontWeight: '700', color: t.brandDeep, marginBottom: 1 },
    respostaTrecho: { fontSize: 13, color: t.muted },

    // Rótulo "Editada" no rodapé do balão (igual à hora, estilo WhatsApp).
    editada: { fontSize: 10.5, color: '#7C8C87' },

    // Menu de contexto estilo iOS (segurar a mensagem): fundo escurece + card flutuante com ações.
    ctxBackdrop: { flex: 1, backgroundColor: alpha(t.ink, 0.28) },
    ctxMenu: {
      position: 'absolute',
      minWidth: 216,
      backgroundColor: t.surface,
      borderRadius: 14,
      paddingVertical: 4,
      shadowColor: '#000',
      shadowOpacity: 0.22,
      shadowRadius: 18,
      shadowOffset: { width: 0, height: 8 },
      elevation: 10,
    },
    ctxItem: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: 18,
      paddingHorizontal: 16,
      paddingVertical: 13,
    },
    ctxItemPress: { backgroundColor: t.brandTint },
    ctxItemTxt: { fontSize: 16, color: t.ink, fontWeight: '500' },
    ctxSep: { height: StyleSheet.hairlineWidth, backgroundColor: alpha(t.ink, 0.1), marginHorizontal: 14 },

    // Menu do "segurar" numa mensagem.
    menuBackdrop: { flex: 1, backgroundColor: alpha(t.ink, 0.35), alignItems: 'center', justifyContent: 'center' },
    menuCard: {
      backgroundColor: t.surface,
      borderRadius: 14,
      paddingVertical: 6,
      minWidth: 200,
      shadowColor: '#0B2E29',
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.2,
      shadowRadius: 16,
      elevation: 8,
    },
    menuItem: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12, paddingHorizontal: 18 },
    menuItemPress: { backgroundColor: t.brandTint },
    menuItemTxt: { fontSize: 15, color: t.ink, fontWeight: '600' },

    // Cabeçalho translúcido (absoluto, no topo; a lista rola por trás).
    headerWrap: {
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      borderBottomWidth: StyleSheet.hairlineWidth,
      borderBottomColor: alpha(t.ink, 0.1),
    },
    // iOS: overlay leve — o blur nativo já suaviza o fundo (mostra o efeito "mensagens atrás").
    // Android: sem blur real, overlay mais opaco para manter o texto do header legível sobre as bolhas.
    barOverlayHeader: {
      ...StyleSheet.absoluteFillObject,
      backgroundColor: alpha(t.surface, Platform.OS === 'ios' ? 0.5 : 0.78),
    },
    header: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 10,
      paddingHorizontal: 8,
      paddingBottom: 10,
    },
    back: { width: 38, height: 38, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
    backPressed: { backgroundColor: t.brandTint },
    headerInfo: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 10 },
    headerInfoPressed: { opacity: 0.6 },
    avatar: {
      width: 40,
      height: 40,
      borderRadius: 20,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: t.brandDeep,
    },
    avatarTxt: { color: t.onBrand, fontSize: 14, fontWeight: '800' },
    contatoNome: { fontSize: 15.5, fontWeight: '700', color: t.ink },
    contatoStatus: { fontSize: 12, color: t.muted, marginTop: 1 },
    digitando: { fontSize: 12, color: t.brand, fontWeight: '700', fontStyle: 'italic', marginTop: 1 },

    // Botão "Falar com humano" no cabeçalho (à direita do nome da unidade).
    humanoHeader: {
      flexShrink: 0,
      flexDirection: 'row',
      alignItems: 'center',
      gap: 5,
      marginLeft: 8,
      paddingHorizontal: 10,
      paddingVertical: 6,
      borderRadius: 18,
      borderWidth: 1,
      borderColor: t.brand,
      backgroundColor: alpha(t.surface, 0.9),
    },
    humanoHeaderPressed: { opacity: 0.6 },
    // Desabilitado (ação já feita, ou sem IA atendendo): esmaecido e borda neutra.
    humanoHeaderOff: { borderColor: t.line, opacity: 0.55 },
    humanoHeaderTxt: { fontSize: 12, fontWeight: '700', color: t.brandDeep },
    humanoHeaderTxtOff: { color: t.muted },

    // Barra de input / só-leitura (absoluta, no rodapé; cola no teclado).
    barraWrap: {
      position: 'absolute',
      left: 0,
      right: 0,
      bottom: 0,
      borderTopWidth: StyleSheet.hairlineWidth,
      borderTopColor: alpha(t.ink, 0.08),
    },
    // Barra de input "flutuante": sem fundo, sem borda — a pílula e o botão flutuam sobre as mensagens.
    barraFlutuante: {
      position: 'absolute',
      left: 0,
      right: 0,
      bottom: 0,
    },
    barOverlayInput: {
      ...StyleSheet.absoluteFillObject,
      backgroundColor: alpha(t.surface, Platform.OS === 'ios' ? 0.6 : 0.84),
    },
    inputBar: {
      flexDirection: 'row',
      alignItems: 'flex-end',
      gap: 8,
      paddingHorizontal: 12,
      paddingTop: 10,
      paddingBottom: 8,
    },
    somenteLeitura: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      paddingHorizontal: 16,
      paddingTop: 12,
      paddingBottom: 12,
    },
    somenteLeituraTxt: { flex: 1, fontSize: 12.5, color: t.muted, lineHeight: 17 },
    // Pílula do input: fundo 100% branco (sólido) + borda, FLUTUANDO (sem barra de fundo atrás).
    inputWrap: {
      flex: 1,
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      backgroundColor: t.surface,
      borderRadius: 24,
      borderWidth: 1,
      borderColor: alpha(t.ink, 0.14),
      paddingHorizontal: 14,
      paddingVertical: Platform.OS === 'ios' ? 10 : 4,
      minHeight: 46,
      shadowColor: '#0B2E29',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.12,
      shadowRadius: 7,
      elevation: 3,
    },
    input: { flex: 1, fontSize: 15, color: t.ink, maxHeight: 100 },
    enviar: {
      width: 46,
      height: 46,
      borderRadius: 23,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: t.brand,
      shadowColor: '#0B2E29',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.2,
      shadowRadius: 8,
      elevation: 4,
    },
    enviarDesativado: { opacity: 0.6 },

    // --- Pesquisar na conversa (estilo WhatsApp iOS) ---
    headerLupa: { width: 38, height: 38, borderRadius: 19, alignItems: 'center', justifyContent: 'center' },
    headerLupaPressed: { backgroundColor: t.brandTint },
    // Campo de pesquisa que substitui o cabeçalho no modo busca.
    buscaCampo: {
      flex: 1,
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      backgroundColor: alpha(t.ink, 0.06),
      borderRadius: 22,
      borderWidth: 1,
      borderColor: alpha(t.ink, 0.1),
      paddingHorizontal: 14,
      minHeight: 44,
    },
    buscaInput: { flex: 1, fontSize: 16, color: t.ink, paddingVertical: Platform.OS === 'ios' ? 0 : 2 },
    buscaFechar: {
      width: 40,
      height: 40,
      borderRadius: 20,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: alpha(t.ink, 0.06),
    },
    // Destaque âmbar das ocorrências dentro do texto da mensagem.
    buscaDestaque: { backgroundColor: '#E8A23C', color: '#2B2007' },
    // Fundo da barra de busca (toolbar com linha no topo, estilo WhatsApp): a pílula ^/v e o contador
    // ficam SOBRE a barra, não soltos. iOS mantém translucidez (blur por trás); Android fica sólido.
    buscaBarFundo: {
      ...StyleSheet.absoluteFillObject,
      backgroundColor: alpha(t.surface, Platform.OS === 'ios' ? 0.72 : 1),
      borderTopWidth: 1,
      borderTopColor: alpha(t.ink, 0.14),
    },
    // Barra do rodapé no modo busca: pílula com setas ^/v + contador.
    buscaNav: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 14,
      paddingHorizontal: 14,
      paddingTop: 10,
      paddingBottom: 8,
    },
    buscaNavPilula: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: t.surface,
      borderRadius: 22,
      borderWidth: 1,
      borderColor: alpha(t.ink, 0.12),
      shadowColor: '#0B2E29',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.12,
      shadowRadius: 7,
      elevation: 3,
    },
    buscaNavBtn: { width: 48, height: 40, alignItems: 'center', justifyContent: 'center' },
    buscaNavDivisor: { width: StyleSheet.hairlineWidth, height: 22, backgroundColor: alpha(t.ink, 0.12) },
    buscaContador: { fontSize: 14, color: t.muted, fontWeight: '600' },

    estado: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, padding: 24 },
    estadoTitulo: { fontSize: 16, fontWeight: '800', color: t.ink },
    estadoTxt: { fontSize: 13.5, color: t.muted, textAlign: 'center' },
    estadoBtn: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 7,
      marginTop: 4,
      height: 44,
      paddingHorizontal: 18,
      borderRadius: 14,
      backgroundColor: t.brand,
    },
    estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
  });
