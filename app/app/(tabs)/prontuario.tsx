import { Ionicons } from '@expo/vector-icons';
import { Directory, File, Paths } from 'expo-file-system';
import { useFocusEffect, useRouter } from 'expo-router';
import * as Sharing from 'expo-sharing';
import * as WebBrowser from 'expo-web-browser';
import { useCallback, useMemo, useRef, useState } from 'react';
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
import { SemAcesso } from '@/components/sem-acesso';
import { DocTipo } from '@/constants/theme';
import { type Tema, useTema } from '@/hooks/use-tema';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import { useSessao } from '@/hooks/use-sessao';
import {
  conferirTermos,
  DocumentoApi,
  iniciarAssinaturaLote,
  listarProntuarios,
  ProntuarioDetalhe,
} from '@/services/prontuario';
import { podeVer } from '@/services/sessao';
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
  termo: 'Termo de Consentimento',
};

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

function dataLonga(iso: string): string {
  const d = new Date(iso);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${d.getDate()} ${MESES[d.getMonth()]} ${d.getFullYear()} · ${hh}:${mm}`;
}

/** Infere o tipo do documento a partir do nome (para manter ícones/cores). */
function inferirTipo(nome: string): TipoDoc {
  const n = nome.toLowerCase();
  if (n.includes('tcle') || n.includes('consentimento') || n.includes('(assinado)')) return 'termo';
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

/**
 * Termos em "Assinatura em confirmação": esconde o botão e fica num loop de conferência
 * (5..4..3..2..1..Conferindo…) batendo no endpoint leve enquanto a tela está em foco. Quando nenhum
 * termo do atendimento continua EM_CONFIRMACAO (virou ASSINADO ou TENTAR_NOVAMENTE), chama onResolvido
 * para a tela recarregar por inteiro. Pausa fora de foco (useFocusEffect limpa o timer no blur).
 */
function ConfirmacaoTermos({
  prontuarioId,
  onResolvido,
}: {
  prontuarioId: number;
  onResolvido: () => void;
}) {
  const t = useTema();
  const styles = useMemo(() => criarEstilosConfirmacao(t), [t]);
  const [segundos, setSegundos] = useState(5);
  const [conferindo, setConferindo] = useState(true);

  useFocusEffect(
    useCallback(() => {
      let cancelado = false;
      let timer: ReturnType<typeof setTimeout> | undefined;

      const conferir = async () => {
        if (cancelado) return;
        setConferindo(true);
        try {
          const termos = await conferirTermos(prontuarioId);
          if (cancelado) return;
          if (!termos.some((tm) => tm.status === 'EM_CONFIRMACAO')) {
            onResolvido(); // resolveu (assinado ou recusado): recarrega a tela e sai do loop
            return;
          }
        } catch {
          // erro de rede: ignora e tenta de novo no próximo ciclo
        }
        contar(5);
      };

      const contar = (n: number) => {
        if (cancelado) return;
        if (n <= 0) {
          conferir();
          return;
        }
        setConferindo(false);
        setSegundos(n);
        timer = setTimeout(() => contar(n - 1), 1000);
      };

      conferir(); // confere já na chegada (t=0)
      return () => {
        cancelado = true;
        if (timer) clearTimeout(timer);
      };
    }, [prontuarioId, onResolvido]),
  );

  return (
    <View style={styles.box}>
      <ActivityIndicator size="small" color={t.brandDeep} />
      <Text style={styles.txt}>
        {conferindo ? 'Conferindo…' : `Confirmando assinatura em ${segundos}…`}
      </Text>
    </View>
  );
}

export default function ProntuarioScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const { sessao } = useSessao();
  const verProntuario = podeVer(sessao, 'PRONTUARIO');
  const [atendimentos, setAtendimentos] = useState<ProntuarioDetalhe[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [atualizando, setAtualizando] = useState(false);
  const [baixando, setBaixando] = useState<number[]>([]);
  const [menu, setMenu] = useState<{ doc: DocumentoApi; link: string } | null>(null);
  const [assinandoId, setAssinandoId] = useState<number | null>(null);
  const router = useRouter();
  const jaCarregou = useRef(false);

  const carregar = useCallback(async (mostrarSpinner: boolean) => {
    try {
      if (mostrarSpinner) setCarregando(true);
      setErro(false);
      const dados = await listarProntuarios();
      setAtendimentos(dados);
      jaCarregou.current = true;
    } catch {
      if (!jaCarregou.current) setErro(true); // silencioso: nao apaga o conteudo ja exibido
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

  // Recarrega a tela inteira (F5) sem spinner — usado quando a confirmação de assinatura resolve.
  const recarregar = useCallback(() => carregar(false), [carregar]);

  // Notificação (app aberto) ou volta ao primeiro plano: atualiza sem spinner.
  useAtualizarComPush(() => carregar(false));

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

  /**
   * Cria os documentos na ZapSign (com as variáveis) e abre a cerimônia no WebView. Assina TODOS os
   * termos pendentes do atendimento numa cerimônia só (lote quando há mais de um). Chaveado pelo prontuário.
   */
  const iniciarAssinatura = async (prontuarioId: number, titulo: string) => {
    if (assinandoId != null) return;
    setAssinandoId(prontuarioId);
    try {
      const signUrl = await iniciarAssinaturaLote(prontuarioId);
      router.push({
        pathname: '/assinar-termo',
        params: { signUrl, nome: titulo, prontuarioId: String(prontuarioId) },
      });
    } catch {
      Alert.alert('Ops', 'Não foi possível iniciar a assinatura agora. Tente novamente.');
    } finally {
      setAssinandoId(null);
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

  if (!verProntuario) {
    return <SemAcesso />;
  }

  return (
    <>
    <ScrollView
      style={styles.screen}
      contentContainerStyle={styles.content}
      refreshControl={
        <RefreshControl refreshing={atualizando} onRefresh={aoAtualizar} tintColor={t.brand} colors={[t.brand]} />
      }>
      <Text style={styles.title}>Prontuário</Text>
      <Text style={styles.subtitle}>Seus documentos, organizados por atendimento.</Text>

      {carregando && (
        <View style={styles.estado}>
          <ActivityIndicator color={t.brand} />
          <Text style={styles.estadoTxt}>Carregando prontuário…</Text>
        </View>
      )}

      {!carregando && erro && (
        <View style={styles.estado}>
          <View style={styles.estadoIcone}>
            <Ionicons name="cloud-offline-outline" size={26} color={t.muted} />
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
            <Ionicons name="document-text-outline" size={26} color={t.muted} />
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
                <Ionicons name="calendar-clear-outline" size={13} color={t.brandDeep} />
                <Text style={styles.dataTxt}>{dataLonga(at.dataHora)}</Text>
              </View>
              <Text style={styles.docCount}>{at.documentos.length} docs</Text>
            </View>

            <Text style={styles.especialidade}>{at.especialidade.nome}</Text>
            <Text style={styles.profissional}>
              {at.profissionalSaude.nome} · {at.unidadeSaude.nome}
            </Text>

            {(() => {
              const emConfirmacao = at.termos.filter((tm) => tm.status === 'EM_CONFIRMACAO');
              const paraAssinar = at.termos.filter(
                (tm) => tm.status === 'PENDENTE' || tm.status === 'TENTAR_NOVAMENTE',
              );
              const visiveis = at.termos.filter(
                (tm) =>
                  tm.status === 'PENDENTE' ||
                  tm.status === 'TENTAR_NOVAMENTE' ||
                  tm.status === 'EM_CONFIRMACAO',
              );
              if (visiveis.length === 0) return null;
              const temRecusa = paraAssinar.some((tm) => tm.status === 'TENTAR_NOVAMENTE');
              return (
                <View style={styles.termos}>
                  <Text style={styles.termosTitulo}>Termos para assinar</Text>
                  {visiveis.map((termo) => (
                    <View key={termo.id} style={styles.termoRow}>
                      <View style={styles.termoIcon}>
                        <Ionicons name="create-outline" size={18} color={t.brandDeep} />
                      </View>
                      <View style={{ flex: 1 }}>
                        <Text style={styles.termoNome} numberOfLines={1}>
                          {termo.nome}
                        </Text>
                        <Text style={styles.termoStatus}>{termo.statusDescricao}</Text>
                      </View>
                    </View>
                  ))}
                  {emConfirmacao.length > 0 ? (
                    <ConfirmacaoTermos prontuarioId={at.id} onResolvido={recarregar} />
                  ) : (
                    <Pressable
                      style={styles.termoBtnFull}
                      onPress={() => iniciarAssinatura(at.id, 'Termo de consentimento')}
                      disabled={assinandoId === at.id}
                      accessibilityRole="button"
                      accessibilityLabel="Assinar termos pendentes">
                      {assinandoId === at.id ? (
                        <ActivityIndicator size="small" color="#fff" />
                      ) : (
                        <Text style={styles.termoBtnTxt}>
                          {temRecusa
                            ? 'Tentar novamente'
                            : paraAssinar.length > 1
                              ? `Assinar ${paraAssinar.length} termos`
                              : 'Iniciar assinatura'}
                        </Text>
                      )}
                    </Pressable>
                  )}
                </View>
              );
            })()}

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
                          <ActivityIndicator size="small" color={t.brand} />
                        ) : (
                          <Ionicons name="download-outline" size={20} color={t.brand} />
                        )}
                      </Pressable>
                    ) : (
                      <Ionicons name="document-outline" size={20} color={t.line} />
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

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
  screen: { flex: 1, backgroundColor: t.bg },
  content: { padding: 20, paddingBottom: 32 },
  title: { fontSize: 26, fontWeight: '800', color: t.ink, letterSpacing: -0.4 },
  subtitle: { fontSize: 14, color: t.muted, marginTop: 4, marginBottom: 18 },
  grupo: {
    backgroundColor: t.surface,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: t.line,
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
    backgroundColor: t.brandTint,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 20,
  },
  dataTxt: { fontSize: 12.5, fontWeight: '700', color: t.brandDeep },
  docCount: { fontSize: 12, color: t.muted, fontWeight: '600' },
  especialidade: { fontSize: 16, fontWeight: '700', color: t.ink },
  profissional: { fontSize: 13, color: t.muted, marginTop: 2, marginBottom: 6 },
  docs: { marginTop: 8 },
  docRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 11 },
  docRowBorder: { borderTopWidth: 1, borderTopColor: t.brandTint },
  docIcon: {
    width: 38,
    height: 38,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
  },
  docTitulo: { fontSize: 14.5, fontWeight: '600', color: t.ink },
  docTipo: { fontSize: 12, color: t.muted, marginTop: 1 },
  docBaixar: { width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },

  // Termos a assinar (TCLE)
  termos: {
    marginTop: 8,
    backgroundColor: t.brandTint,
    borderRadius: 12,
    padding: 10,
  },
  termosTitulo: {
    fontSize: 11.5,
    fontWeight: '800',
    color: t.brandDeep,
    textTransform: 'uppercase',
    letterSpacing: 0.4,
    marginBottom: 4,
  },
  termoRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 6 },
  termoIcon: {
    width: 34,
    height: 34,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: t.surface,
  },
  termoNome: { fontSize: 14, fontWeight: '700', color: t.ink },
  termoStatus: { fontSize: 12, color: t.muted, marginTop: 1 },
  termoBtn: { backgroundColor: t.brand, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 8 },
  termoBtnFull: {
    marginTop: 8,
    backgroundColor: t.brand,
    borderRadius: 10,
    paddingVertical: 11,
    alignItems: 'center',
    justifyContent: 'center',
  },
  termoBtnTxt: { color: '#fff', fontSize: 12.5, fontWeight: '700' },

  // Estados (carregando / erro / vazio)
  estado: { alignItems: 'center', justifyContent: 'center', paddingVertical: 48, gap: 10 },
  estadoIcone: {
    width: 56,
    height: 56,
    borderRadius: 18,
    backgroundColor: t.surface,
    borderWidth: 1,
    borderColor: t.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: t.ink, marginTop: 2 },
  estadoTxt: { fontSize: 13.5, color: t.muted, textAlign: 'center', paddingHorizontal: 24, lineHeight: 19 },
  estadoBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    marginTop: 6,
    height: 44,
    paddingHorizontal: 18,
    borderRadius: 14,
    backgroundColor: t.brand,
  },
  estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
});

const criarEstilosConfirmacao = (t: Tema) =>
  StyleSheet.create({
    box: {
      marginTop: 8,
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 8,
      backgroundColor: t.surface,
      borderRadius: 10,
      borderWidth: 1,
      borderColor: t.line,
      paddingVertical: 11,
    },
    txt: { fontSize: 12.5, fontWeight: '700', color: t.brandDeep },
  });
