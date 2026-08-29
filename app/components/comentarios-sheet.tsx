import { Ionicons } from '@expo/vector-icons';
import { useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Brand } from '@/constants/theme';
import { useSessao } from '@/hooks/use-sessao';
import { comentar, Comentario, listarComentarios, responder } from '@/services/feed';

const TAMANHO = 20;

function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/);
  const a = partes[0]?.charAt(0) ?? '';
  const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
  return (a + b).toUpperCase();
}

function haQuanto(iso: string): string {
  const min = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (min < 1) return 'agora';
  if (min < 60) return `há ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `há ${h} h`;
  return `há ${Math.floor(h / 24)} d`;
}

interface Props {
  visivel: boolean;
  postagemId: number | null;
  onFechar: () => void;
  onNovoComentario?: (postagemId: number) => void;
}

export function ComentariosSheet({ visivel, postagemId, onFechar, onNovoComentario }: Props) {
  const insets = useSafeAreaInsets();
  const { sessao } = useSessao();
  const AUTOR = sessao?.nome ?? 'Paciente';
  const [comentarios, setComentarios] = useState<Comentario[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [carregandoMais, setCarregandoMais] = useState(false);
  const [erro, setErro] = useState(false);
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [respondendo, setRespondendo] = useState<{ raizId: number; autor: string } | null>(null);
  const campoRef = useRef<TextInput>(null);
  const page = useRef(0);
  const temMais = useRef(false);

  useEffect(() => {
    if (!visivel || postagemId == null) return;
    let ativo = true;
    setCarregando(true);
    setErro(false);
    setComentarios([]);
    setRespondendo(null);
    setTexto('');
    page.current = 0;
    temMais.current = false;
    (async () => {
      try {
        const pagina = await listarComentarios(postagemId, 0, TAMANHO);
        if (!ativo) return;
        setComentarios(Array.isArray(pagina.content) ? pagina.content : []);
        temMais.current = pagina.last === false;
      } catch {
        if (ativo) setErro(true);
      } finally {
        if (ativo) setCarregando(false);
      }
    })();
    return () => {
      ativo = false;
    };
  }, [visivel, postagemId]);

  const carregarMais = async () => {
    if (postagemId == null || carregandoMais || !temMais.current) return;
    setCarregandoMais(true);
    try {
      const proxima = page.current + 1;
      const pagina = await listarComentarios(postagemId, proxima, TAMANHO);
      const conteudo = Array.isArray(pagina.content) ? pagina.content : [];
      setComentarios((lista) => [...lista, ...conteudo]);
      page.current = proxima;
      temMais.current = pagina.last === false;
    } catch {
      // silencioso
    } finally {
      setCarregandoMais(false);
    }
  };

  const iniciarResposta = (raizId: number, autor: string) => {
    setRespondendo({ raizId, autor });
    campoRef.current?.focus();
  };

  const enviar = async () => {
    const conteudo = texto.trim();
    if (!conteudo || postagemId == null || enviando) return;
    setEnviando(true);
    try {
      if (respondendo) {
        const resposta = await responder(postagemId, respondendo.raizId, AUTOR, conteudo);
        setComentarios((lista) =>
          lista.map((c) =>
            c.id === respondendo.raizId ? { ...c, respostas: [...(c.respostas ?? []), resposta] } : c,
          ),
        );
        setRespondendo(null);
      } else {
        const novo = await comentar(postagemId, AUTOR, conteudo);
        setComentarios((lista) => [novo, ...lista]); // mais recente no topo
      }
      setTexto('');
      onNovoComentario?.(postagemId);
    } catch {
      // mantém o texto
    } finally {
      setEnviando(false);
    }
  };

  const podeEnviar = !!texto.trim() && !enviando;

  const renderItem = ({ item }: { item: Comentario }) => (
    <View>
      <View style={styles.item}>
        <View style={styles.itemAvatar}>
          <Text style={styles.itemAvatarTxt}>{iniciais(item.autor)}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.itemTexto}>
            <Text style={styles.itemAutor}>{item.autor} </Text>
            {item.texto}
          </Text>
          <View style={styles.itemMeta}>
            <Text style={styles.itemTempo}>{haQuanto(item.criadoEm)}</Text>
            <Pressable onPress={() => iniciarResposta(item.id, item.autor)} hitSlop={6}>
              <Text style={styles.responder}>Responder</Text>
            </Pressable>
          </View>
        </View>
      </View>

      {item.respostas?.length > 0 && (
        <View style={styles.respostas}>
          {item.respostas.map((r) => (
            <View key={r.id} style={styles.itemResposta}>
              <View style={styles.itemAvatarSm}>
                <Text style={styles.itemAvatarTxtSm}>{iniciais(r.autor)}</Text>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.itemTexto}>
                  <Text style={styles.itemAutor}>{r.autor} </Text>
                  {r.texto}
                </Text>
                <View style={styles.itemMeta}>
                  <Text style={styles.itemTempo}>{haQuanto(r.criadoEm)}</Text>
                  <Pressable onPress={() => iniciarResposta(item.id, r.autor)} hitSlop={6}>
                    <Text style={styles.responder}>Responder</Text>
                  </Pressable>
                </View>
              </View>
            </View>
          ))}
        </View>
      )}
    </View>
  );

  return (
    <Modal visible={visivel} transparent animationType="slide" statusBarTranslucent onRequestClose={onFechar}>
      <KeyboardAvoidingView
        style={styles.overlay}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <Pressable style={styles.overlayTopo} onPress={onFechar} />
        <View style={styles.sheet}>
          <View style={styles.puxador} />
          <View style={styles.cabecalho}>
            <Text style={styles.titulo}>Comentários</Text>
            <Pressable onPress={onFechar} hitSlop={10} accessibilityLabel="Fechar">
              <Ionicons name="close" size={22} color={Brand.muted} />
            </Pressable>
          </View>
          <View style={styles.divisor} />

          {carregando ? (
            <View style={styles.estado}>
              <ActivityIndicator color={Brand.brand} />
            </View>
          ) : erro ? (
            <View style={styles.estado}>
              <Text style={styles.estadoTxt}>Não foi possível carregar os comentários.</Text>
            </View>
          ) : (
            <FlatList
              data={comentarios}
              keyExtractor={(c) => String(c.id)}
              renderItem={renderItem}
              style={styles.lista}
              contentContainerStyle={styles.listaConteudo}
              onEndReached={carregarMais}
              onEndReachedThreshold={0.4}
              keyboardShouldPersistTaps="handled"
              ListFooterComponent={
                carregandoMais ? <ActivityIndicator style={styles.maisSpinner} color={Brand.brand} /> : null
              }
              ListEmptyComponent={
                <View style={styles.vazio}>
                  <Ionicons name="chatbubbles-outline" size={30} color={Brand.muted} />
                  <Text style={styles.vazioTxt}>Seja o primeiro a comentar.</Text>
                </View>
              }
            />
          )}

          {respondendo ? (
            <View style={styles.faixa}>
              <Text style={styles.faixaTxt} numberOfLines={1}>
                Respondendo a <Text style={styles.faixaNome}>{respondendo.autor}</Text>
              </Text>
              <Pressable onPress={() => setRespondendo(null)} hitSlop={8} accessibilityLabel="Cancelar resposta">
                <Ionicons name="close" size={18} color={Brand.muted} />
              </Pressable>
            </View>
          ) : null}
          <View style={[styles.input, { paddingBottom: Math.max(insets.bottom, 10) }]}>
            <View style={styles.inputAvatar}>
              <Text style={styles.inputAvatarTxt}>{iniciais(AUTOR)}</Text>
            </View>
            <TextInput
              ref={campoRef}
              style={styles.campo}
              value={texto}
              onChangeText={setTexto}
              placeholder={respondendo ? 'Escreva uma resposta…' : 'Deixe um comentário…'}
              placeholderTextColor="#9AAAA5"
              multiline
            />
            <Pressable
              style={[styles.enviar, !podeEnviar && styles.enviarDesativado]}
              onPress={enviar}
              disabled={!podeEnviar}
              accessibilityLabel="Enviar comentário">
              {enviando ? (
                <ActivityIndicator color="#fff" size="small" />
              ) : (
                <Ionicons name="send" size={18} color="#fff" />
              )}
            </Pressable>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(7,46,43,0.45)',
  },
  overlayTopo: { flex: 1 },
  sheet: {
    height: '82%',
    backgroundColor: Brand.surface,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    overflow: 'hidden',
  },
  puxador: { alignSelf: 'center', width: 40, height: 4, borderRadius: 2, backgroundColor: '#D4DEDA', marginTop: 8 },
  cabecalho: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, paddingVertical: 12 },
  titulo: { fontSize: 16, fontWeight: '800', color: Brand.ink },
  divisor: { height: 1, backgroundColor: Brand.line },

  estado: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center' },

  lista: { flex: 1 },
  listaConteudo: { padding: 16, gap: 16, flexGrow: 1 },
  item: { flexDirection: 'row', gap: 10 },
  itemAvatar: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#E7F3EF',
  },
  itemAvatarTxt: { color: Brand.brandDeep, fontSize: 12, fontWeight: '800' },
  itemTexto: { fontSize: 14, color: Brand.ink, lineHeight: 19 },
  itemAutor: { fontWeight: '700' },
  itemMeta: { flexDirection: 'row', alignItems: 'center', gap: 14, marginTop: 3 },
  itemTempo: { fontSize: 11.5, color: Brand.muted },
  responder: { fontSize: 11.5, fontWeight: '700', color: Brand.brandDeep },
  respostas: { paddingLeft: 44, gap: 12, marginTop: 12 },
  itemResposta: { flexDirection: 'row', gap: 10 },
  itemAvatarSm: { width: 28, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center', backgroundColor: '#E7F3EF' },
  itemAvatarTxtSm: { color: Brand.brandDeep, fontSize: 10.5, fontWeight: '800' },
  maisSpinner: { marginVertical: 12 },

  faixa: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderTopWidth: 1,
    borderTopColor: Brand.line,
    backgroundColor: '#F1F6F4',
  },
  faixaTxt: { flex: 1, fontSize: 12.5, color: Brand.muted },
  faixaNome: { fontWeight: '700', color: Brand.ink },

  vazio: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8, paddingVertical: 60 },
  vazioTxt: { fontSize: 13.5, color: Brand.muted },

  input: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
    paddingHorizontal: 12,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: Brand.line,
    backgroundColor: Brand.surface,
  },
  inputAvatar: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.brandDeep,
    marginBottom: 4,
  },
  inputAvatarTxt: { color: Brand.onBrand, fontSize: 12, fontWeight: '800' },
  campo: {
    flex: 1,
    minHeight: 42,
    maxHeight: 110,
    backgroundColor: Brand.bg,
    borderWidth: 1,
    borderColor: Brand.line,
    borderRadius: 22,
    paddingHorizontal: 16,
    paddingVertical: Platform.OS === 'ios' ? 10 : 6,
    fontSize: 15,
    color: Brand.ink,
  },
  enviar: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.brand,
  },
  enviarDesativado: { opacity: 0.5 },
});
