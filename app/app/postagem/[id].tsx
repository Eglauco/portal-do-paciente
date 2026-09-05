import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Image,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { EdicaoComentario } from '@/components/comentario-edicao';
import { AvatarFoto } from '@/components/avatar-paciente';
import { ComentarioInput, ComentarioInputHandle } from '@/components/comentario-input';
import { Brand } from '@/constants/theme';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import { useSessao } from '@/hooks/use-sessao';
import {
  buscarPostagem,
  comentar,
  Comentario,
  curtir,
  editarComentario,
  excluirComentario,
  listarComentarios,
  Postagem,
  responder,
} from '@/services/feed';
import { obterDispositivoId } from '@/services/identidade';

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

export default function PostagemDetalheScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { sessao } = useSessao();
  const AUTOR = sessao?.nome ?? 'Paciente';

  const [post, setPost] = useState<Postagem | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [comentarios, setComentarios] = useState<Comentario[]>([]);
  const [carregandoMais, setCarregandoMais] = useState(false);
  const [respondendo, setRespondendo] = useState<{ raizId: number; autor: string } | null>(null);
  const [editando, setEditando] = useState<number | null>(null);
  const [salvandoEdicao, setSalvandoEdicao] = useState(false);
  const inputRef = useRef<ComentarioInputHandle>(null);
  const dispositivoId = useRef('');
  const page = useRef(0);
  const temMais = useRef(false);
  const jaCarregou = useRef(false);

  const carregar = useCallback(async () => {
    if (!id) return;
    try {
      setErro(false);
      if (!dispositivoId.current) dispositivoId.current = await obterDispositivoId();
      const p = await buscarPostagem(id, dispositivoId.current);
      setPost(p);
      if (p.habilitarComentarios) {
        const pagina = await listarComentarios(id, 0, TAMANHO);
        setComentarios(Array.isArray(pagina.content) ? pagina.content : []);
        page.current = 0;
        temMais.current = pagina.last === false;
      } else {
        setComentarios([]);
        page.current = 0;
        temMais.current = false;
      }
      jaCarregou.current = true;
    } catch {
      // Só mostra a tela de erro no 1º carregamento; num resync silencioso
      // (push/foreground) preserva o conteúdo já exibido.
      if (!jaCarregou.current) setErro(true);
    } finally {
      setCarregando(false);
    }
  }, [id]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  // No resync silencioso (push/foreground) atualiza SÓ a postagem (curtidas/conteúdo),
  // sem recarregar os comentários — assim não colapsa as páginas que o paciente já rolou.
  const atualizarSilencioso = useCallback(async () => {
    if (!id) return;
    try {
      if (!dispositivoId.current) dispositivoId.current = await obterDispositivoId();
      setPost(await buscarPostagem(id, dispositivoId.current));
    } catch {
      // mantém o conteúdo atual
    }
  }, [id]);

  useAtualizarComPush(() => atualizarSilencioso());

  const carregarMais = async () => {
    if (!id || !post?.habilitarComentarios || carregandoMais || !temMais.current) return;
    setCarregandoMais(true);
    try {
      const proxima = page.current + 1;
      const pagina = await listarComentarios(id, proxima, TAMANHO);
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

  const curtirPost = async () => {
    if (!post) return;
    const atual = post;
    setPost({
      ...atual,
      curtidoPorMim: !atual.curtidoPorMim,
      totalCurtidas: atual.totalCurtidas + (atual.curtidoPorMim ? -1 : 1),
    });
    try {
      const r = await curtir(atual.id, dispositivoId.current);
      setPost((p) => (p ? { ...p, curtidoPorMim: r.curtido, totalCurtidas: r.totalCurtidas } : p));
    } catch {
      setPost(atual);
    }
  };

  const iniciarResposta = useCallback((raizId: number, autor: string) => {
    setRespondendo({ raizId, autor });
    inputRef.current?.focus();
  }, []);

  const enviarComentario = useCallback(
    async (conteudo: string): Promise<boolean> => {
      if (!id) return false;
      try {
        if (respondendo) {
          const resposta = await responder(id, respondendo.raizId, AUTOR, conteudo);
          setComentarios((lista) =>
            lista.map((c) =>
              c.id === respondendo.raizId ? { ...c, respostas: [...(c.respostas ?? []), resposta] } : c,
            ),
          );
          setPost((p) => (p ? { ...p, totalComentarios: p.totalComentarios + 1 } : p));
          setRespondendo(null);
          return true;
        }
        const novo = await comentar(id, AUTOR, conteudo);
        setComentarios((lista) => [novo, ...lista]);
        setPost((p) => (p ? { ...p, totalComentarios: p.totalComentarios + 1 } : p));
        return true;
      } catch {
        return false;
      }
    },
    [id, respondendo],
  );

  const iniciarEdicao = useCallback((c: Comentario) => {
    setRespondendo(null);
    setEditando(c.id);
  }, []);

  const cancelarEdicao = useCallback(() => setEditando(null), []);

  const salvarEdicao = useCallback(
    async (c: Comentario, raizId: number | null, texto: string) => {
      if (!id) return;
      const novo = texto.trim();
      if (!novo || novo === c.texto) {
        setEditando(null);
        return;
      }
      setSalvandoEdicao(true);
      try {
        await editarComentario(id, c.id, novo);
        setComentarios((lista) =>
          raizId == null
            ? lista.map((x) => (x.id === c.id ? { ...x, texto: novo, editado: true } : x))
            : lista.map((x) =>
                x.id === raizId
                  ? { ...x, respostas: x.respostas.map((r) => (r.id === c.id ? { ...r, texto: novo, editado: true } : r)) }
                  : x,
              ),
        );
        setEditando(null);
      } catch {
        Alert.alert('Não foi possível editar', 'Talvez o prazo de 15 minutos tenha expirado. Tente novamente.');
      } finally {
        setSalvandoEdicao(false);
      }
    },
    [id],
  );

  const excluir = useCallback(
    async (c: Comentario, raizId: number | null) => {
      if (!id) return;
      const removidos = raizId == null ? 1 + (c.respostas?.length ?? 0) : 1;
      try {
        await excluirComentario(id, c.id);
        setComentarios((lista) =>
          raizId == null
            ? lista.filter((x) => x.id !== c.id)
            : lista.map((x) => (x.id === raizId ? { ...x, respostas: x.respostas.filter((r) => r.id !== c.id) } : x)),
        );
        setPost((p) => (p ? { ...p, totalComentarios: Math.max(0, p.totalComentarios - removidos) } : p));
        setEditando((atual) => (atual === c.id ? null : atual));
      } catch {
        Alert.alert('Não foi possível excluir', 'Tente novamente.');
      }
    },
    [id],
  );

  const confirmarExclusao = useCallback(
    (c: Comentario, raizId: number | null) => {
      const respostas = raizId == null ? c.respostas?.length ?? 0 : 0;
      const msg =
        respostas > 0
          ? `Isso também vai excluir as ${respostas} resposta(s) abaixo. Deseja continuar?`
          : 'Deseja excluir este comentário?';
      Alert.alert('Excluir comentário', msg, [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Excluir', style: 'destructive', onPress: () => excluir(c, raizId) },
      ]);
    },
    [excluir],
  );

  /** Corpo do comentário (raiz ou resposta): texto/edição + meta + ações. raizId nulo = comentário-raiz. */
  const renderCorpo = (item: Comentario, raizId: number | null) => (
    <View style={{ flex: 1 }}>
      {editando === item.id ? (
        <EdicaoComentario
          inicial={item.texto}
          salvando={salvandoEdicao}
          onSalvar={(txt) => salvarEdicao(item, raizId, txt)}
          onCancelar={cancelarEdicao}
        />
      ) : (
        <>
          <Text style={styles.itemTexto}>
            <Text style={styles.itemAutor}>{item.autor} </Text>
            {item.texto}
          </Text>
          <View style={styles.itemMeta}>
            <Text style={styles.itemTempo}>
              {haQuanto(item.criadoEm)}
              {item.editado ? ' · editado' : ''}
            </Text>
            {post?.habilitarComentarios && (
              <Pressable onPress={() => iniciarResposta(raizId ?? item.id, item.autor)} hitSlop={6}>
                <Text style={styles.responder}>Responder</Text>
              </Pressable>
            )}
            {item.meu && item.podeEditar && (
              <Pressable onPress={() => iniciarEdicao(item)} hitSlop={6}>
                <Text style={styles.acaoLink}>Editar</Text>
              </Pressable>
            )}
            {item.meu && (
              <Pressable
                style={styles.lixeira}
                onPress={() => confirmarExclusao(item, raizId)}
                hitSlop={8}
                accessibilityLabel="Excluir comentário">
                <Ionicons name="trash-outline" size={16} color="#C0475A" />
              </Pressable>
            )}
          </View>
        </>
      )}
    </View>
  );

  const renderCabecalho = () => {
    if (!post) return null;
    return (
      <View>
        <View style={styles.cardHeader}>
          <View style={styles.avatar}>
            <Text style={styles.avatarTxt}>{iniciais(post.unidadeSaude.nome)}</Text>
          </View>
          <Text style={styles.unidade} numberOfLines={1}>
            {post.unidadeSaude.nome}
          </Text>
          <Text style={styles.tempoTopo}>{haQuanto(post.criadoEm)}</Text>
        </View>

        <Image source={{ uri: post.url }} style={styles.imagem} resizeMode="cover" />

        <View style={styles.acoes}>
          <Pressable onPress={curtirPost} hitSlop={8} accessibilityLabel="Curtir">
            <Ionicons
              name={post.curtidoPorMim ? 'heart' : 'heart-outline'}
              size={27}
              color={post.curtidoPorMim ? '#E0245E' : Brand.ink}
            />
          </Pressable>
          {post.habilitarComentarios && (
            <View style={styles.acaoComentar}>
              <Ionicons name="chatbubble-outline" size={24} color={Brand.ink} />
              {post.totalComentarios > 0 && <Text style={styles.acaoContagem}>{post.totalComentarios}</Text>}
            </View>
          )}
        </View>

        {post.mostrarTotalCurtidas && (
          <Text style={styles.curtidas}>
            {post.totalCurtidas} {post.totalCurtidas === 1 ? 'curtida' : 'curtidas'}
          </Text>
        )}

        <Text style={styles.legenda}>
          <Text style={styles.legendaUnidade}>{post.unidadeSaude.nome} </Text>
          {post.titulo}
        </Text>
        {!!post.descricao && <Text style={styles.descricao}>{post.descricao}</Text>}

        {post.habilitarComentarios ? (
          <View style={styles.comentariosCab}>
            <Text style={styles.comentariosTitulo}>Comentários</Text>
          </View>
        ) : (
          <Text style={styles.desativados}>Comentários desativados para esta publicação.</Text>
        )}
      </View>
    );
  };

  const renderComentario = ({ item }: { item: Comentario }) => (
    <View>
      <View style={styles.item}>
        <AvatarFoto
          fotoUrl={item.fotoUrl}
          iniciais={iniciais(item.autor)}
          tamanho={34}
          estiloCirculo={styles.itemAvatar}
          estiloTexto={styles.itemAvatarTxt}
        />
        {renderCorpo(item, null)}
      </View>

      {item.respostas?.length > 0 && (
        <View style={styles.respostas}>
          {item.respostas.map((r) => (
            <View key={r.id} style={styles.itemResposta}>
              <AvatarFoto
                fotoUrl={r.fotoUrl}
                iniciais={iniciais(r.autor)}
                tamanho={28}
                estiloCirculo={styles.itemAvatarSm}
                estiloTexto={styles.itemAvatarTxtSm}
              />
              {renderCorpo(r, item.id)}
            </View>
          ))}
        </View>
      )}
    </View>
  );

  return (
    <KeyboardAvoidingView style={styles.screen} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Pressable
          style={({ pressed }) => [styles.back, pressed && styles.backPressed]}
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Voltar">
          <Ionicons name="chevron-back" size={26} color={Brand.ink} />
        </Pressable>
        <Text style={styles.headerTitulo}>Publicação</Text>
      </View>

      {carregando ? (
        <View style={styles.estado}>
          <ActivityIndicator color={Brand.brand} />
          <Text style={styles.estadoTxt}>Carregando publicação…</Text>
        </View>
      ) : erro || !post ? (
        <View style={styles.estado}>
          <Ionicons name="cloud-offline-outline" size={28} color={Brand.muted} />
          <Text style={styles.estadoTitulo}>Não foi possível abrir</Text>
          <Pressable style={styles.estadoBtn} onPress={() => router.back()}>
            <Text style={styles.estadoBtnTxt}>Voltar</Text>
          </Pressable>
        </View>
      ) : (
        <>
          <FlatList
            data={comentarios}
            keyExtractor={(c) => String(c.id)}
            renderItem={renderComentario}
            extraData={`${editando}-${salvandoEdicao}`}
            ListHeaderComponent={renderCabecalho()}
            contentContainerStyle={styles.conteudo}
            onEndReached={carregarMais}
            onEndReachedThreshold={0.4}
            keyboardShouldPersistTaps="handled"
            ListFooterComponent={
              carregandoMais ? <ActivityIndicator style={styles.maisSpinner} color={Brand.brand} /> : null
            }
            ListEmptyComponent={
              post.habilitarComentarios ? (
                <Text style={styles.vazioTxt}>Seja o primeiro a comentar.</Text>
              ) : null
            }
          />

          {post.habilitarComentarios && (
            <ComentarioInput
              ref={inputRef}
              autorIniciais={iniciais(AUTOR)}
              onEnviar={enviarComentario}
              respondendoNome={respondendo?.autor ?? null}
              onCancelarResposta={() => setRespondendo(null)}
              paddingBottom={Math.max(insets.bottom, 10)}
            />
          )}
        </>
      )}
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.surface },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 8,
    paddingBottom: 10,
    backgroundColor: Brand.surface,
    borderBottomWidth: 1,
    borderBottomColor: Brand.line,
  },
  back: { width: 38, height: 38, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  backPressed: { backgroundColor: '#EAF2EF' },
  headerTitulo: { fontSize: 17, fontWeight: '800', color: Brand.ink },

  estado: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 10, padding: 24 },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: Brand.ink },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center' },
  estadoBtn: { marginTop: 4, height: 44, paddingHorizontal: 20, borderRadius: 14, backgroundColor: Brand.brand, alignItems: 'center', justifyContent: 'center' },
  estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },

  conteudo: { paddingBottom: 16 },

  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 14, paddingVertical: 10 },
  avatar: { width: 38, height: 38, borderRadius: 19, alignItems: 'center', justifyContent: 'center', backgroundColor: Brand.brandDeep },
  avatarTxt: { color: Brand.onBrand, fontSize: 13, fontWeight: '800' },
  unidade: { flex: 1, fontSize: 14.5, fontWeight: '700', color: Brand.ink },
  tempoTopo: { fontSize: 12, color: Brand.muted },

  imagem: { width: '100%', aspectRatio: 4 / 5, backgroundColor: '#E7EDEA' },

  acoes: { flexDirection: 'row', alignItems: 'center', gap: 16, paddingHorizontal: 14, paddingTop: 10, paddingBottom: 4 },
  acaoComentar: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  acaoContagem: { fontSize: 14, fontWeight: '600', color: Brand.ink },
  curtidas: { paddingHorizontal: 14, fontSize: 13.5, fontWeight: '700', color: Brand.ink, marginTop: 2 },
  legenda: { paddingHorizontal: 14, marginTop: 4, fontSize: 14, color: Brand.ink, lineHeight: 20 },
  legendaUnidade: { fontWeight: '700' },
  descricao: { paddingHorizontal: 14, marginTop: 3, fontSize: 13.5, color: '#40514C', lineHeight: 19 },

  comentariosCab: { paddingHorizontal: 14, paddingTop: 16, paddingBottom: 4 },
  comentariosTitulo: { fontSize: 14, fontWeight: '800', color: Brand.ink },
  desativados: { paddingHorizontal: 14, paddingTop: 16, fontSize: 13, color: Brand.muted, fontStyle: 'italic' },

  item: { flexDirection: 'row', gap: 10, paddingHorizontal: 14, paddingTop: 14 },
  itemAvatar: { width: 34, height: 34, borderRadius: 17, alignItems: 'center', justifyContent: 'center', backgroundColor: '#E7F3EF' },
  itemAvatarTxt: { color: Brand.brandDeep, fontSize: 12, fontWeight: '800' },
  itemTexto: { fontSize: 14, color: Brand.ink, lineHeight: 19 },
  itemAutor: { fontWeight: '700' },
  itemMeta: { flexDirection: 'row', alignItems: 'center', gap: 14, marginTop: 3 },
  itemTempo: { fontSize: 11.5, color: Brand.muted },
  responder: { fontSize: 11.5, fontWeight: '700', color: Brand.brandDeep },
  acaoLink: { fontSize: 11.5, fontWeight: '700', color: Brand.brandDeep },
  lixeira: { marginLeft: 'auto', padding: 2 },
  respostas: { paddingLeft: 44 },
  itemResposta: { flexDirection: 'row', gap: 10, paddingHorizontal: 14, paddingTop: 12 },
  itemAvatarSm: { width: 28, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center', backgroundColor: '#E7F3EF' },
  itemAvatarTxtSm: { color: Brand.brandDeep, fontSize: 10.5, fontWeight: '800' },
  maisSpinner: { marginVertical: 14 },
  vazioTxt: { paddingHorizontal: 14, paddingTop: 14, fontSize: 13.5, color: Brand.muted },
});
