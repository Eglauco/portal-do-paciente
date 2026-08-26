import { Ionicons } from '@expo/vector-icons';
import { Directory, File, Paths } from 'expo-file-system';
import { useFocusEffect } from 'expo-router';
import * as Sharing from 'expo-sharing';
import * as WebBrowser from 'expo-web-browser';
import { useCallback, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { DocumentoModal } from '@/components/documento-modal';
import { Brand, DocTipo } from '@/constants/theme';
import { DocumentoApi, listarProntuarios, ProntuarioDetalhe } from '@/services/prontuario';
import { urlDownload } from '@/services/storage';

/** Deriva um nome de arquivo limpo a partir da URL (remove query e o prefixo uuid). */
function nomeArquivoDe(url: string, fallback = 'documento'): string {
  try {
    const semQuery = url.split('?')[0];
    const seg = decodeURIComponent(semQuery.substring(semQuery.lastIndexOf('/') + 1));
    return seg.replace(/^[0-9a-fA-F-]{36}-/, '') || fallback;
  } catch {
    return fallback;
  }
}

function mimeDe(nome: string): string {
  const ext = nome.split('.').pop()?.toLowerCase();
  if (ext === 'pdf') return 'application/pdf';
  if (ext === 'png') return 'image/png';
  if (ext === 'jpg' || ext === 'jpeg') return 'image/jpeg';
  return 'application/octet-stream';
}

type TipoDoc = keyof typeof DocTipo;

const ROTULO_TIPO: Record<TipoDoc, string> = {
  exame: 'Resultado de exame',
  receita: 'Receita médica',
  atestado: 'Atestado',
  ficha: 'Ficha de atendimento',
  laudo: 'Laudo',
};

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

function dataLonga(iso: string): string {
  const d = new Date(iso);
  return `${d.getDate()} ${MESES[d.getMonth()]} ${d.getFullYear()}`;
}

/** Infere o tipo do documento a partir do nome (para manter ícones/cores). */
function inferirTipo(nome: string): TipoDoc {
  const n = nome.toLowerCase();
  if (n.includes('receita')) return 'receita';
  if (n.includes('atestado')) return 'atestado';
  if (n.includes('laudo')) return 'laudo';
  if (n.includes('ficha') || n.includes('anamnese') || n.includes('solicit') || n.includes('encaminh'))
    return 'ficha';
  if (
    n.includes('exame') ||
    n.includes('hemograma') ||
    n.includes('raio') ||
    n.includes('eletro') ||
    n.includes('ecg') ||
    n.includes('ultrass') ||
    n.includes('tomografia') ||
    n.includes('resultado')
  )
    return 'exame';
  return 'ficha';
}

export default function ProntuarioScreen() {
  const [atendimentos, setAtendimentos] = useState<ProntuarioDetalhe[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [atualizando, setAtualizando] = useState(false);
  const [baixando, setBaixando] = useState<number[]>([]);
  const [menu, setMenu] = useState<{ doc: DocumentoApi; link: string } | null>(null);
  const jaCarregou = useRef(false);

  const carregar = useCallback(async (mostrarSpinner: boolean) => {
    try {
      if (mostrarSpinner) setCarregando(true);
      setErro(false);
      const dados = await listarProntuarios();
      setAtendimentos(dados);
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

  /** Abre o documento num visualizador in-app (renderiza o PDF). */
  const abrirNoVisualizador = async (link: string) => {
    try {
      await WebBrowser.openBrowserAsync(link);
    } catch {
      try {
        await Linking.openURL(link);
      } catch {
        Alert.alert('Ops', 'Não foi possível abrir o documento.');
      }
    }
  };

  /** Baixa o arquivo e abre a folha nativa de salvar/compartilhar. */
  const baixarECompartilhar = async (doc: DocumentoApi, link: string) => {
    setBaixando((l) => [...l, doc.id]);
    try {
      const nome = nomeArquivoDe(doc.url ?? '', `${doc.nome}.pdf`);
      const pasta = new Directory(Paths.cache, 'prontuarios');
      if (!pasta.exists) pasta.create();
      const destino = new File(pasta, nome);
      if (destino.exists) destino.delete();
      const arquivo = await File.downloadFileAsync(link, destino);

      if (await Sharing.isAvailableAsync()) {
        await Sharing.shareAsync(arquivo.uri, {
          mimeType: mimeDe(nome),
          dialogTitle: doc.nome,
          UTI: 'public.item',
        });
      } else {
        await Linking.openURL(link);
      }
    } catch {
      Alert.alert('Ops', 'Não foi possível baixar o documento. Tente novamente.');
    } finally {
      setBaixando((l) => l.filter((id) => id !== doc.id));
    }
  };

  /** Gera a URL assinada e abre o pop-up com as opções. */
  const baixar = async (doc: DocumentoApi) => {
    if (!doc.url || baixando.includes(doc.id)) return;
    setBaixando((l) => [...l, doc.id]);
    try {
      const link = await urlDownload(doc.url);
      setMenu({ doc, link });
    } catch {
      Alert.alert('Ops', 'Não foi possível gerar o link do documento.');
    } finally {
      setBaixando((l) => l.filter((id) => id !== doc.id));
    }
  };

  const aoAbrir = () => {
    const atual = menu;
    setMenu(null);
    if (atual) abrirNoVisualizador(atual.link);
  };

  const aoBaixar = () => {
    const atual = menu;
    setMenu(null);
    if (atual) baixarECompartilhar(atual.doc, atual.link);
  };

  return (
    <>
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl refreshing={atualizando} onRefresh={aoAtualizar} tintColor={Brand.brand} colors={[Brand.brand]} />
      }>
      <Text style={styles.title}>Prontuário</Text>
      <Text style={styles.subtitle}>Seus documentos, organizados por atendimento.</Text>

      {carregando && (
        <View style={styles.estado}>
          <ActivityIndicator color={Brand.brand} />
          <Text style={styles.estadoTxt}>Carregando prontuário…</Text>
        </View>
      )}

      {!carregando && erro && (
        <View style={styles.estado}>
          <View style={styles.estadoIcone}>
            <Ionicons name="cloud-offline-outline" size={26} color={Brand.muted} />
          </View>
          <Text style={styles.estadoTitulo}>Não foi possível carregar</Text>
          <Text style={styles.estadoTxt}>Verifique sua conexão com o servidor e tente novamente.</Text>
          <Pressable style={styles.estadoBtn} onPress={() => carregar(true)}>
            <Ionicons name="refresh" size={16} color="#fff" />
            <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
          </Pressable>
        </View>
      )}

      {!carregando && !erro && atendimentos.length === 0 && (
        <View style={styles.estado}>
          <View style={styles.estadoIcone}>
            <Ionicons name="document-text-outline" size={26} color={Brand.muted} />
          </View>
          <Text style={styles.estadoTitulo}>Nenhum documento</Text>
          <Text style={styles.estadoTxt}>Seus atendimentos e documentos aparecerão aqui.</Text>
        </View>
      )}

      {!carregando &&
        !erro &&
        atendimentos.map((at) => (
          <View key={at.id} style={styles.grupo}>
            <View style={styles.grupoHeader}>
              <View style={styles.dataTag}>
                <Ionicons name="calendar-clear-outline" size={13} color={Brand.brandDeep} />
                <Text style={styles.dataTxt}>{dataLonga(at.dataHora)}</Text>
              </View>
              <Text style={styles.docCount}>{at.documentos.length} docs</Text>
            </View>

            <Text style={styles.especialidade}>{at.especialidade.nome}</Text>
            <Text style={styles.profissional}>
              {at.profissionalSaude.nome} · {at.unidadeSaude.nome}
            </Text>

            <View style={styles.docs}>
              {at.documentos.map((doc, i) => {
                const tipo = inferirTipo(doc.nome);
                const cor = DocTipo[tipo];
                const emDownload = baixando.includes(doc.id);
                return (
                  <View key={doc.id} style={[styles.docRow, i > 0 && styles.docRowBorder]}>
                    <View style={[styles.docIcon, { backgroundColor: cor.bg }]}>
                      <Ionicons name={cor.icon as any} size={18} color={cor.fg} />
                    </View>
                    <View style={{ flex: 1 }}>
                      <Text style={styles.docTitulo} numberOfLines={1}>
                        {doc.nome}
                      </Text>
                      <Text style={styles.docTipo}>{ROTULO_TIPO[tipo]}</Text>
                    </View>
                    {doc.url ? (
                      <Pressable
                        onPress={() => baixar(doc)}
                        disabled={emDownload}
                        hitSlop={10}
                        style={styles.docBaixar}
                        accessibilityRole="button"
                        accessibilityLabel={`Baixar ${doc.nome}`}>
                        {emDownload ? (
                          <ActivityIndicator size="small" color={Brand.brand} />
                        ) : (
                          <Ionicons name="download-outline" size={20} color={Brand.brand} />
                        )}
                      </Pressable>
                    ) : (
                      <Ionicons name="document-outline" size={20} color={Brand.line} />
                    )}
                  </View>
                );
              })}
            </View>
          </View>
        ))}
    </ScrollView>

      <DocumentoModal
        visivel={menu !== null}
        nome={menu?.doc.nome ?? null}
        onAbrir={aoAbrir}
        onBaixar={aoBaixar}
        onFechar={() => setMenu(null)}
      />
    </>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  content: { padding: 20, paddingBottom: 32 },
  title: { fontSize: 26, fontWeight: '800', color: Brand.ink, letterSpacing: -0.4 },
  subtitle: { fontSize: 14, color: Brand.muted, marginTop: 4, marginBottom: 18 },
  grupo: {
    backgroundColor: Brand.surface,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: Brand.line,
    padding: 16,
    marginBottom: 14,
  },
  grupoHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  dataTag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#E7F3EF',
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 20,
  },
  dataTxt: { fontSize: 12.5, fontWeight: '700', color: Brand.brandDeep },
  docCount: { fontSize: 12, color: Brand.muted, fontWeight: '600' },
  especialidade: { fontSize: 16, fontWeight: '700', color: Brand.ink },
  profissional: { fontSize: 13, color: Brand.muted, marginTop: 2, marginBottom: 6 },
  docs: { marginTop: 8 },
  docRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 11 },
  docRowBorder: { borderTopWidth: 1, borderTopColor: '#EEF3F1' },
  docIcon: {
    width: 38,
    height: 38,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
  },
  docTitulo: { fontSize: 14.5, fontWeight: '600', color: Brand.ink },
  docTipo: { fontSize: 12, color: Brand.muted, marginTop: 1 },
  docBaixar: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },

  // Estados (carregando / erro / vazio)
  estado: { alignItems: 'center', justifyContent: 'center', paddingVertical: 48, gap: 10 },
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
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center', paddingHorizontal: 24, lineHeight: 19 },
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
