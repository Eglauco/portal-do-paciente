import { Ionicons } from '@expo/vector-icons';
import { BlurView } from 'expo-blur';
import { useFocusEffect, useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  NativeScrollEvent,
  NativeSyntheticEvent,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  useWindowDimensions,
  View,
} from 'react-native';
import { useKeyboardHandler, useReanimatedKeyboardAnimation } from 'react-native-keyboard-controller';
import Animated, { runOnJS, useAnimatedStyle } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { alpha, type Tema, useTema } from '@/hooks/use-tema';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import { useSessao } from '@/hooks/use-sessao';
import { podeLancar } from '@/services/sessao';
import {
  buscarConversa,
  ChatDetalhe,
  confirmarEntrega,
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
const TILE_FUNDO = 280; // tamanho de exibição de cada tile (pt) — controla a densidade

/** Fundo de ícones de saúde cobrindo a tela inteira (grade de tiles calculada pela dimensão). */
function FundoSaude() {
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
          style={{ position: 'absolute', left: c * TILE_FUNDO, top: r * TILE_FUNDO, width: TILE_FUNDO, height: TILE_FUNDO }}
        />,
      );
    }
  }
  return (
    <View style={styles_fundo} pointerEvents="none">
      {tiles}
    </View>
  );
}
const styles_fundo = { position: 'absolute' as const, top: 0, left: 0, right: 0, bottom: 0, opacity: 0.16, overflow: 'hidden' as const };

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
  const scrollRef = useRef<ScrollView>(null);
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
      // Chegou mensagem da unidade: some com "digitando…" na hora e cancela o auto-ocultar.
      if (timerDigitando.current) {
        clearTimeout(timerDigitando.current);
        timerDigitando.current = null;
      }
      setDigitando(false);
      setDetalhe((d) => {
        if (!d) return d;
        if (d.mensagens.some((x) => x.id === m.id)) return d; // evita duplicar
        // Reconcilia o eco da própria mensagem otimista (temporária, id < 0) pelo clienteId
        // (1-para-1; cai no texto só se o eco não trouxer clienteId).
        const ehOtimista = (x: Mensagem) =>
          x.id < 0 && (m.clienteId ? x.clienteId === m.clienteId : x.texto === m.texto);
        const base = m.remetente === 'PACIENTE' ? d.mensagens.filter((x) => !ehOtimista(x)) : d.mensagens;
        return { ...d, mensagens: [...base, m] };
      });
      // Mensagem da unidade chegou enquanto estou na conversa: confirma entrega E marca lida.
      if (m.remetente === 'UNIDADE') {
        confirmarEntrega(id);
        marcarLido(id);
      }
      rolarParaFim();
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
    async (tempId: number, clienteId: string, conteudo: string) => {
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
          const atualizado = await enviarMensagemPaciente(id, conteudo, clienteId);
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
    // Otimista: mostra a mensagem na hora com o "relógio" (pendente) até o servidor confirmar.
    const tempId = -Date.now();
    const clienteId = novoClienteId();
    const otimista: Mensagem = {
      id: tempId,
      remetente: 'PACIENTE',
      texto: limpo,
      enviadaEm: new Date().toISOString(),
      lida: false,
      entregue: false,
      pendente: true,
      clienteId,
    };
    setDetalhe((d) => (d ? { ...d, mensagens: [...d.mensagens, otimista] } : d));
    setTexto('');
    rolarParaFim();
    tentarEnviar(tempId, clienteId, limpo);
  };

  const reenviar = (m: Mensagem) => {
    if (m.clienteId) tentarEnviar(m.id, m.clienteId, m.texto);
  };

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
        <ScrollView
          ref={scrollRef}
          style={styles.mensagens}
          contentContainerStyle={[
            styles.mensagensContent,
            { paddingTop: headerH + 6, paddingBottom: barraRowH + Math.max(insets.bottom, 10) + 8 },
          ]}
          onContentSizeChange={() => {
            // Só prende no fim se o usuário já estava lá — senão a animação do teclado (que muda o
            // tamanho do conteúdo a cada frame) travaria a rolagem para o histórico.
            if (pertoDoFim.current) rolarParaFim(false);
          }}
          onScroll={aoRolar}
          scrollEventThrottle={16}
          keyboardDismissMode="interactive"
          keyboardShouldPersistTaps="handled"
          contentInsetAdjustmentBehavior="never"
          automaticallyAdjustKeyboardInsets={false}
          showsVerticalScrollIndicator={false}>
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
                  <View
                    style={[
                      styles.bolha,
                      daUnidade ? styles.bolhaUnidade : styles.bolhaPaciente,
                      daUnidade && m.geradaPorIa && styles.bolhaIa,
                    ]}>
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
                    <Text style={styles.texto}>{m.texto}</Text>
                    <View style={styles.rodape}>
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
                  </View>
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
                </View>
              </View>
            );
          })}
          {/* Espaçador que cresce com o teclado: mantém a última mensagem acima do teclado. */}
          <Animated.View style={espacadorAnim} />
        </ScrollView>
      )}

      {/* Cabeçalho translúcido (blur no iOS, translúcido no Android). */}
      <View
        style={styles.headerWrap}
        onLayout={(e) => setHeaderH(Math.round(e.nativeEvent.layout.height))}>
        <BlurView intensity={48} tint="light" style={StyleSheet.absoluteFill} />
        <View style={styles.barOverlayHeader} />
        {conteudoHeader}
      </View>

      {/* Barra de digitação: translúcida e COLADA no teclado (sobe junto). */}
      {!carregando && !erro && podeEnviar && (
        <Animated.View style={[styles.barraFlutuante, barraInputAnim]}>
          <View
            style={styles.inputBar}
            onLayout={(e) => setBarraRowH(Math.round(e.nativeEvent.layout.height))}>
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
              style={[styles.enviar, !texto.trim() && styles.enviarDesativado]}
              onPress={enviar}
              disabled={!texto.trim()}
              accessibilityRole="button"
              accessibilityState={{ disabled: !texto.trim() }}
              accessibilityLabel="Enviar mensagem">
              <Ionicons name="send" size={19} color="#fff" />
            </Pressable>
          </View>
          <Animated.View style={safeAreaAnim} />
        </Animated.View>
      )}
      {!carregando && !erro && !podeEnviar && (
        <View style={styles.barraWrap}>
          <BlurView intensity={56} tint="light" style={StyleSheet.absoluteFill} />
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
    bolhaWrap: { marginBottom: 8, maxWidth: '82%' },
    esquerda: { alignSelf: 'flex-start' },
    direita: { alignSelf: 'flex-end' },
    bolha: { borderRadius: 16, paddingHorizontal: 12, paddingVertical: 8 },
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
