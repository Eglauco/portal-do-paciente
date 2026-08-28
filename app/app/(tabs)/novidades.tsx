import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Image,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { ComentariosSheet } from '@/components/comentarios-sheet';
import { Brand } from '@/constants/theme';
import { curtir, listarFeed, Postagem } from '@/services/feed';
import { obterDispositivoId } from '@/services/identidade';

function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/);
  const a = partes[0]?.charAt(0) ?? '';
  const b = partes.length > 1 ? partes[partes.length - 1].charAt(0) : '';
  return (a + b).toUpperCase();
}

function haQuanto(iso: string): string {
  const d = new Date(iso).getTime();
  const min = Math.floor((Date.now() - d) / 60000);
  if (min < 1) return 'agora';
  if (min < 60) return `há ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `há ${h} h`;
  const dias = Math.floor(h / 24);
  if (dias < 7) return `há ${dias} d`;
  const dt = new Date(iso);
  const dd = String(dt.getDate()).padStart(2, '0');
  const mm = String(dt.getMonth() + 1).padStart(2, '0');
  return `${dd}/${mm}/${dt.getFullYear()}`;
}

export default function NovidadesScreen() {
  const [posts, setPosts] = useState<Postagem[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [atualizando, setAtualizando] = useState(false);
  const [postComentarios, setPostComentarios] = useState<number | null>(null);
  const jaCarregou = useRef(false);
  const dispositivoId = useRef('');

  const carregar = useCallback(async (mostrarSpinner: boolean) => {
    try {
      if (mostrarSpinner) setCarregando(true);
      setErro(false);
      if (!dispositivoId.current) dispositivoId.current = await obterDispositivoId();
      const dados = await listarFeed(dispositivoId.current);
      setPosts(dados);
      jaCarregou.current = true;
    } catch {
      setErro(true);
    } finally {
      setCarregando(false);
      setAtualizando(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      carregar(!jaCarregou.current);
    }, [carregar]),
  );

  const aoAtualizar = () => {
    setAtualizando(true);
    carregar(false);
  };

  const curtirPost = async (post: Postagem) => {
    // Atualização otimista.
    setPosts((lista) =>
      lista.map((p) =>
        p.id === post.id
          ? { ...p, curtidoPorMim: !p.curtidoPorMim, totalCurtidas: p.totalCurtidas + (p.curtidoPorMim ? -1 : 1) }
          : p,
      ),
    );
    try {
      const r = await curtir(post.id, dispositivoId.current);
      setPosts((lista) =>
        lista.map((p) => (p.id === post.id ? { ...p, curtidoPorMim: r.curtido, totalCurtidas: r.totalCurtidas } : p)),
      );
    } catch {
      carregar(false); // desfaz a otimista recarregando
    }
  };

  const abrirComentarios = (post: Postagem) => {
    setPostComentarios(post.id);
  };

  const aoNovoComentario = (postagemId: number) => {
    setPosts((lista) =>
      lista.map((p) => (p.id === postagemId ? { ...p, totalComentarios: p.totalComentarios + 1 } : p)),
    );
  };

  const renderPost = ({ item: post }: { item: Postagem }) => (
    <View style={styles.card}>
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
        <Pressable onPress={() => curtirPost(post)} hitSlop={8} accessibilityLabel="Curtir">
          <Ionicons
            name={post.curtidoPorMim ? 'heart' : 'heart-outline'}
            size={27}
            color={post.curtidoPorMim ? '#E0245E' : Brand.ink}
          />
        </Pressable>
        {post.habilitarComentarios && (
          <Pressable style={styles.acaoComentar} onPress={() => abrirComentarios(post)} hitSlop={8} accessibilityLabel="Comentar">
            <Ionicons name="chatbubble-outline" size={24} color={Brand.ink} />
            {post.totalComentarios > 0 && <Text style={styles.acaoContagem}>{post.totalComentarios}</Text>}
          </Pressable>
        )}
      </View>

      {post.mostrarTotalCurtidas && (
        <Text style={styles.curtidas}>
          {post.totalCurtidas} {post.totalCurtidas === 1 ? 'curtida' : 'curtidas'}
        </Text>
      )}

      <Text style={styles.legenda}>
        <Text style={styles.legendaUnidade}>{post.unidadeSaude.nome} </Text>
        <Text style={styles.legendaTitulo}>{post.titulo}</Text>
      </Text>
      {!!post.descricao && <Text style={styles.descricao}>{post.descricao}</Text>}
    </View>
  );

  if (carregando) {
    return (
      <View style={styles.estado}>
        <ActivityIndicator color={Brand.brand} />
        <Text style={styles.estadoTxt}>Carregando novidades…</Text>
      </View>
    );
  }

  if (erro) {
    return (
      <View style={styles.estado}>
        <View style={styles.estadoIcone}>
          <Ionicons name="cloud-offline-outline" size={26} color={Brand.muted} />
        </View>
        <Text style={styles.estadoTitulo}>Não foi possível carregar</Text>
        <Text style={styles.estadoTxt}>Verifique sua conexão e tente novamente.</Text>
        <Pressable style={styles.estadoBtn} onPress={() => carregar(true)}>
          <Ionicons name="refresh" size={16} color="#fff" />
          <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
        </Pressable>
      </View>
    );
  }

  return (
    <>
      <FlatList
        style={styles.screen}
        data={posts}
        keyExtractor={(p) => String(p.id)}
        renderItem={renderPost}
        refreshControl={
          <RefreshControl refreshing={atualizando} onRefresh={aoAtualizar} tintColor={Brand.brand} colors={[Brand.brand]} />
        }
        ListHeaderComponent={
          <View style={styles.topo}>
            <Text style={styles.titulo}>Novidades</Text>
            <Text style={styles.subtitulo}>Fique por dentro das suas unidades de saúde.</Text>
          </View>
        }
        ListEmptyComponent={
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="images-outline" size={26} color={Brand.muted} />
            </View>
            <Text style={styles.estadoTitulo}>Nenhuma novidade ainda</Text>
            <Text style={styles.estadoTxt}>As postagens das unidades aparecerão aqui.</Text>
          </View>
        }
        contentContainerStyle={styles.conteudo}
      />

      <ComentariosSheet
        visivel={postComentarios !== null}
        postagemId={postComentarios}
        onFechar={() => setPostComentarios(null)}
        onNovoComentario={aoNovoComentario}
      />
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  conteudo: { paddingBottom: 24 },
  topo: { paddingHorizontal: 18, paddingTop: 14, paddingBottom: 6 },
  titulo: { fontSize: 24, fontWeight: '800', color: Brand.ink, letterSpacing: -0.4 },
  subtitulo: { fontSize: 13.5, color: Brand.muted, marginTop: 2 },

  card: {
    backgroundColor: Brand.surface,
    marginTop: 12,
    borderTopWidth: 1,
    borderBottomWidth: 1,
    borderColor: Brand.line,
  },
  cardHeader: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingHorizontal: 14, paddingVertical: 10 },
  avatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.brandDeep,
  },
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
  legendaTitulo: { fontWeight: '400' },
  descricao: { paddingHorizontal: 14, marginTop: 3, fontSize: 13.5, color: '#40514C', lineHeight: 19 },

  estado: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingVertical: 64, gap: 10, backgroundColor: Brand.bg },
  estadoIcone: {
    width: 56,
    height: 56,
    borderRadius: 18,
    backgroundColor: Brand.surface,
    borderWidth: 1,
    borderColor: Brand.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: Brand.ink, marginTop: 2 },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center', paddingHorizontal: 32, lineHeight: 19 },
  estadoBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    marginTop: 6,
    height: 44,
    paddingHorizontal: 18,
    borderRadius: 14,
    backgroundColor: Brand.brand,
  },
  estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
});
