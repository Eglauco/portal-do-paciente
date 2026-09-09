import { Ionicons } from '@expo/vector-icons';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
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

import { AvatarFoto, AvatarPaciente } from '@/components/avatar-paciente';
import { EdicaoComentario } from '@/components/comentario-edicao';
import { alpha, type Tema, useTema } from '@/hooks/use-tema';
import { useSessao } from '@/hooks/use-sessao';
import {
  comentar,
  Comentario,
  editarComentario,
  excluirComentario,
  listarComentarios,
  responder,
} from '@/services/feed';
import { podeLancar } from '@/services/sessao';

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
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const { sessao } = useSessao();
  const AUTOR = sessao?.nome ?? 'Paciente';
  // Responsável só-leitura na Rede Social: lê os comentários, mas não comenta/responde.
  const podeComentar = podeLancar(sessao, 'REDE_SOCIAL');
  const [comentarios, setComentarios] = useState<Comentario[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [carregandoMais, setCarregandoMais] = useState(false);
  const [erro, setErro] = useState(false);
  const [texto, setTexto] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [respondendo, setRespondendo] = useState<{ raizId: number; autor: string } | null>(null);
  const [editando, setEditando] = useState<number | null>(null);
  const [salvandoEdicao, setSalvandoEdicao] = useState(false);
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
    setEditando(null);
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
      let criado: Comentario;
      if (respondendo) {
        criado = await responder(postagemId, respondendo.raizId, conteudo);
        setComentarios((lista) =>
          lista.map((c) =>
            c.id === respondendo.raizId ? { ...c, respostas: [...(c.respostas ?? []), criado] } : c,
          ),
        );
        setRespondendo(null);
      } else {
        criado = await comentar(postagemId, conteudo);
        setComentarios((lista) => [criado, ...lista]); // mais recente no topo
      }
      setTexto('');
      onNovoComentario?.(postagemId);
      // Moderação por IA: se o comentário caiu em análise, explica que só ele o vê por ora.
      if (criado.statusModeracao === 'PENDENTE') {
        Alert.alert(
          'Comentário em análise',
          'Para manter um ambiente seguro, seu comentário passará por uma análise rápida antes de aparecer para todos. Por enquanto, só você o vê aqui.',
        );
      }
    } catch (e) {
      // Mantém o texto e mostra o motivo do backend (ex.: idade mínima para comentar).
      Alert.alert('Não foi possível comentar', e instanceof Error ? e.message : 'Tente novamente.');
    } finally {
      setEnviando(false);
    }
  };

  const podeEnviar = !!texto.trim() && !enviando;

  const iniciarEdicao = (c: Comentario) => {
    setRespondendo(null);
    setEditando(c.id);
  };
  const cancelarEdicao = () => setEditando(null);

  const salvarEdicao = async (c: Comentario, raizId: number | null, novoTexto: string) => {
    if (postagemId == null) return;
    const novo = novoTexto.trim();
    if (!novo || novo === c.texto) {
      setEditando(null);
      return;
    }
    setSalvandoEdicao(true);
    try {
      const atualizado = await editarComentario(postagemId, c.id, novo);
      // A edição pode reprovar na IA (status vira PENDENTE) — reflete o novo status.
      const aplicar = (x: Comentario) => ({
        ...x,
        texto: novo,
        editado: true,
        statusModeracao: atualizado.statusModeracao,
      });
      setComentarios((lista) =>
        raizId == null
          ? lista.map((x) => (x.id === c.id ? aplicar(x) : x))
          : lista.map((x) =>
              x.id === raizId
                ? { ...x, respostas: x.respostas.map((r) => (r.id === c.id ? aplicar(r) : r)) }
                : x,
            ),
      );
      setEditando(null);
      if (atualizado.statusModeracao === 'PENDENTE') {
        Alert.alert(
          'Comentário em análise',
          'Seu comentário editado passará por uma análise rápida antes de aparecer para todos. Por enquanto, só você o vê aqui.',
        );
      }
    } catch {
      Alert.alert('Não foi possível editar', 'Talvez o prazo de edição tenha expirado. Tente novamente.');
    } finally {
      setSalvandoEdicao(false);
    }
  };

  const excluir = async (c: Comentario, raizId: number | null) => {
    if (postagemId == null) return;
    try {
      await excluirComentario(postagemId, c.id);
      setComentarios((lista) =>
        raizId == null
          ? lista.filter((x) => x.id !== c.id)
          : lista.map((x) => (x.id === raizId ? { ...x, respostas: x.respostas.filter((r) => r.id !== c.id) } : x)),
      );
      setEditando((atual) => (atual === c.id ? null : atual));
    } catch {
      Alert.alert('Não foi possível excluir', 'Tente novamente.');
    }
  };

  const confirmarExclusao = (c: Comentario, raizId: number | null) => {
    const qtd = raizId == null ? c.respostas?.length ?? 0 : 0;
    const msg =
      qtd > 0
        ? `Isso também vai excluir as ${qtd} resposta(s) abaixo. Deseja continuar?`
        : 'Deseja excluir este comentário?';
    Alert.alert('Excluir comentário', msg, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Excluir', style: 'destructive', onPress: () => excluir(c, raizId) },
    ]);
  };

  /** Corpo do comentário (raiz ou resposta): texto/edição + meta + ações. raizId nulo = raiz. */
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
          {item.responsavelNome ? (
            <View style={styles.viaResp}>
              <Ionicons name="people-outline" size={11} color="#8A5A00" />
              <Text style={styles.viaRespTxt}>Comentado por {item.responsavelNome} (responsável)</Text>
            </View>
          ) : null}
          <View style={styles.itemMeta}>
            <Text style={styles.itemTempo}>
              {haQuanto(item.criadoEm)}
              {item.editado ? ' · editado' : ''}
            </Text>
            {podeComentar && (
              <Pressable onPress={() => iniciarResposta(raizId ?? item.id, item.autor)} hitSlop={6}>
                <Text style={styles.responder}>Responder</Text>
              </Pressable>
            )}
            {podeComentar && item.meu && item.podeEditar && (
              <Pressable onPress={() => iniciarEdicao(item)} hitSlop={6}>
                <Text style={styles.acaoLink}>Editar</Text>
              </Pressable>
            )}
            {podeComentar && item.meu && (
              <Pressable
                style={styles.lixeira}
                onPress={() => confirmarExclusao(item, raizId)}
                hitSlop={8}
                accessibilityLabel="Excluir comentário">
                <Ionicons name="trash-outline" size={16} color="#C0475A" />
              </Pressable>
            )}
          </View>
          {item.statusModeracao === 'PENDENTE' && (
            <View style={styles.analise}>
              <Ionicons name="time-outline" size={12} color="#8A5A00" />
              <Text style={styles.analiseTxt}>Em análise — visível só para você até ser aprovado.</Text>
            </View>
          )}
        </>
      )}
    </View>
  );

  const renderItem = ({ item }: { item: Comentario }) => (
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
              <Ionicons name="close" size={22} color={t.muted} />
            </Pressable>
          </View>
          <View style={styles.divisor} />

          {carregando ? (
            <View style={styles.estado}>
              <ActivityIndicator color={t.brand} />
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
              extraData={`${editando}-${salvandoEdicao}`}
              style={styles.lista}
              contentContainerStyle={styles.listaConteudo}
              onEndReached={carregarMais}
              onEndReachedThreshold={0.4}
              keyboardShouldPersistTaps="handled"
              ListFooterComponent={
                carregandoMais ? <ActivityIndicator style={styles.maisSpinner} color={t.brand} /> : null
              }
              ListEmptyComponent={
                <View style={styles.vazio}>
                  <Ionicons name="chatbubbles-outline" size={30} color={t.muted} />
                  <Text style={styles.vazioTxt}>Seja o primeiro a comentar.</Text>
                </View>
              }
            />
          )}

          {podeComentar && respondendo ? (
            <View style={styles.faixa}>
              <Text style={styles.faixaTxt} numberOfLines={1}>
                Respondendo a <Text style={styles.faixaNome}>{respondendo.autor}</Text>
              </Text>
              <Pressable onPress={() => setRespondendo(null)} hitSlop={8} accessibilityLabel="Cancelar resposta">
                <Ionicons name="close" size={18} color={t.muted} />
              </Pressable>
            </View>
          ) : null}
          {podeComentar ? (
            <View style={[styles.input, { paddingBottom: Math.max(insets.bottom, 10) }]}>
              <AvatarPaciente
                iniciais={iniciais(AUTOR)}
                tamanho={34}
                estiloCirculo={styles.inputAvatar}
                estiloTexto={styles.inputAvatarTxt}
                estiloFoto={styles.inputAvatarFoto}
              />
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
          ) : (
            <View style={[styles.somenteLeitura, { paddingBottom: Math.max(insets.bottom, 10) }]}>
              <Ionicons name="eye-outline" size={15} color={t.muted} />
              <Text style={styles.somenteLeituraTxt}>Você pode ler os comentários, mas não comentar.</Text>
            </View>
          )}
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: alpha(t.brandPine, 0.45),
  },
  overlayTopo: { flex: 1 },
  sheet: {
    height: '82%',
    backgroundColor: t.surface,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    overflow: 'hidden',
  },
  puxador: { alignSelf: 'center', width: 40, height: 4, borderRadius: 2, backgroundColor: '#D4DEDA', marginTop: 8 },
  cabecalho: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 16, paddingVertical: 12 },
  titulo: { fontSize: 16, fontWeight: '800', color: t.ink },
  divisor: { height: 1, backgroundColor: t.line },

  estado: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  estadoTxt: { fontSize: 13.5, color: t.muted, textAlign: 'center' },

  lista: { flex: 1 },
  listaConteudo: { padding: 16, gap: 16, flexGrow: 1 },
  item: { flexDirection: 'row', gap: 10 },
  itemAvatar: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: t.brandTint,
  },
  itemAvatarTxt: { color: t.brandDeep, fontSize: 12, fontWeight: '800' },
  itemTexto: { fontSize: 14, color: t.ink, lineHeight: 19 },
  itemAutor: { fontWeight: '700' },
  viaResp: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 3 },
  viaRespTxt: { fontSize: 11, fontWeight: '700', color: '#8A5A00' },
  itemMeta: { flexDirection: 'row', alignItems: 'center', gap: 14, marginTop: 3 },
  itemTempo: { fontSize: 11.5, color: t.muted },
  responder: { fontSize: 11.5, fontWeight: '700', color: t.brandDeep },
  acaoLink: { fontSize: 11.5, fontWeight: '700', color: t.brandDeep },
  lixeira: { marginLeft: 'auto', padding: 2 },
  analise: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    marginTop: 6,
    paddingHorizontal: 8,
    paddingVertical: 5,
    borderRadius: 8,
    backgroundColor: '#FBEFD6',
    alignSelf: 'flex-start',
  },
  analiseTxt: { fontSize: 11, fontWeight: '700', color: '#8A5A00' },
  respostas: { paddingLeft: 44, gap: 12, marginTop: 12 },
  itemResposta: { flexDirection: 'row', gap: 10 },
  itemAvatarSm: { width: 28, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center', backgroundColor: t.brandTint },
  itemAvatarTxtSm: { color: t.brandDeep, fontSize: 10.5, fontWeight: '800' },
  maisSpinner: { marginVertical: 12 },

  faixa: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderTopWidth: 1,
    borderTopColor: t.line,
    backgroundColor: t.brandTint,
  },
  faixaTxt: { flex: 1, fontSize: 12.5, color: t.muted },
  faixaNome: { fontWeight: '700', color: t.ink },

  vazio: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8, paddingVertical: 60 },
  vazioTxt: { fontSize: 13.5, color: t.muted },

  input: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
    paddingHorizontal: 12,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: t.line,
    backgroundColor: t.surface,
  },
  somenteLeitura: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 16,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: t.line,
    backgroundColor: t.surface,
  },
  somenteLeituraTxt: { flex: 1, fontSize: 12.5, color: t.muted, lineHeight: 17 },
  inputAvatar: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: t.brandDeep,
    marginBottom: 4,
  },
  inputAvatarTxt: { color: t.onBrand, fontSize: 12, fontWeight: '800' },
  inputAvatarFoto: { marginBottom: 4 },
  campo: {
    flex: 1,
    minHeight: 42,
    maxHeight: 110,
    backgroundColor: t.bg,
    borderWidth: 1,
    borderColor: t.line,
    borderRadius: 22,
    paddingHorizontal: 16,
    paddingVertical: Platform.OS === 'ios' ? 10 : 6,
    fontSize: 15,
    color: t.ink,
  },
  enviar: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: t.brand,
  },
  enviarDesativado: { opacity: 0.5 },
});
