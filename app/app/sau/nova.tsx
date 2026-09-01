import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
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
import {
  TipoManifestacao,
  UnidadeRef,
  abrirManifestacao,
  listarTipos,
  listarUnidades,
} from '@/services/sau';

export default function NovaManifestacaoScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const scrollRef = useRef<ScrollView>(null);
  const [alturaTeclado, setAlturaTeclado] = useState(0);

  const [tipos, setTipos] = useState<TipoManifestacao[]>([]);
  const [tipoId, setTipoId] = useState<number | null>(null);
  const [unidades, setUnidades] = useState<UnidadeRef[]>([]);
  const [unidadeId, setUnidadeId] = useState<number | null>(null);
  const [texto, setTexto] = useState('');
  const [carregando, setCarregando] = useState(true);
  const [erroCarregar, setErroCarregar] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [erroEnvio, setErroEnvio] = useState<string | null>(null);

  const carregar = async () => {
    try {
      setCarregando(true);
      setErroCarregar(false);
      const [unids, tps] = await Promise.all([listarUnidades(), listarTipos()]);
      setUnidades(unids);
      setTipos(tps);
      if (unids.length === 1) setUnidadeId(unids[0].id);
      if (tps.length === 1) setTipoId(tps[0].id);
    } catch {
      setErroCarregar(true);
    } finally {
      setCarregando(false);
    }
  };

  useEffect(() => {
    carregar();
  }, []);

  // O campo de mensagem fica no rodapé de um formulário longo. Sob edge-to-edge
  // (padrão do Expo SDK 54) o teclado sobrepõe a tela sem redimensioná-la, então
  // reservamos manualmente a altura do teclado como espaço no fim e rolamos até o
  // campo — garante que a área de escrita nunca fique atrás do teclado.
  useEffect(() => {
    const aoMostrar = Keyboard.addListener('keyboardDidShow', (e) => setAlturaTeclado(e.endCoordinates.height));
    const aoEsconder = Keyboard.addListener('keyboardDidHide', () => setAlturaTeclado(0));
    return () => {
      aoMostrar.remove();
      aoEsconder.remove();
    };
  }, []);

  // Rola até o fim SÓ depois que o espaço reservado (padding) já entrou no layout,
  // senão a rolagem aconteceria com a altura antiga e o botão ficaria coberto.
  useEffect(() => {
    if (alturaTeclado > 0) {
      const t = setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 50);
      return () => clearTimeout(t);
    }
  }, [alturaTeclado]);

  const podeEnviar = tipoId != null && unidadeId != null && texto.trim().length > 0 && !enviando;

  const enviar = async () => {
    if (!podeEnviar || tipoId == null || unidadeId == null) return;
    try {
      setEnviando(true);
      setErroEnvio(null);
      const criada = await abrirManifestacao(tipoId, unidadeId, texto.trim());
      router.replace({ pathname: '/sau/[id]', params: { id: String(criada.id) } });
    } catch {
      setErroEnvio('Não foi possível enviar agora. Tente novamente.');
      setEnviando(false);
    }
  };

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
        <Text style={styles.headerTitulo}>Nova manifestação</Text>
      </View>

      <ScrollView
        ref={scrollRef}
        style={styles.corpo}
        contentContainerStyle={{
          padding: 16,
          // Reserva a altura do teclado + a inset inferior (barra de navegação), que
          // o edge-to-edge não inclui na altura reportada — garante folga no botão.
          // Com teclado fechado ainda reserva a inset para o botão não ficar sob a barra.
          paddingBottom: 40 + (alturaTeclado > 0 ? alturaTeclado + insets.bottom : insets.bottom),
        }}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="interactive">
        {carregando ? (
          <View style={styles.carregando}>
            <ActivityIndicator color={Brand.brand} />
            <Text style={styles.carregandoTxt}>Carregando opções…</Text>
          </View>
        ) : erroCarregar ? (
          <Pressable style={styles.unidadesErro} onPress={carregar}>
            <Ionicons name="refresh" size={16} color={Brand.brand} />
            <Text style={styles.unidadesErroTxt}>Não foi possível carregar. Toque para tentar novamente.</Text>
          </Pressable>
        ) : (
          <>
            {/* Tipo */}
            <Text style={styles.label}>Tipo de manifestação</Text>
            {tipos.length === 0 ? (
              <Text style={styles.vazioTxt}>Nenhum tipo disponível no momento. Fale com a unidade de saúde.</Text>
            ) : (
              <View style={styles.tipos}>
                {tipos.map((t) => {
                  const ativo = tipoId === t.id;
                  return (
                    <Pressable
                      key={t.id}
                      onPress={() => {
                        setTipoId(t.id);
                        if (erroEnvio) setErroEnvio(null);
                      }}
                      style={[styles.tipoCard, ativo && styles.tipoCardAtivo]}
                      accessibilityRole="radio"
                      accessibilityState={{ selected: ativo }}>
                      <View style={styles.tipoCabecalho}>
                        <Ionicons
                          name="chatbox-ellipses-outline"
                          size={22}
                          color={ativo ? Brand.brand : Brand.muted}
                        />
                        <Text style={[styles.tipoRotulo, ativo && styles.tipoRotuloAtivo]}>{t.nome}</Text>
                      </View>
                      {t.descricao ? <Text style={styles.tipoDescricao}>{t.descricao}</Text> : null}
                    </Pressable>
                  );
                })}
              </View>
            )}

            {/* Unidade */}
            <Text style={[styles.label, { marginTop: 22 }]}>Unidade</Text>
            <View style={styles.unidades}>
              {unidades.map((u) => {
                const ativo = unidadeId === u.id;
                return (
                  <Pressable
                    key={u.id}
                    onPress={() => setUnidadeId(u.id)}
                    style={[styles.unidadeRow, ativo && styles.unidadeRowAtiva]}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: ativo }}>
                    <Ionicons
                      name={ativo ? 'radio-button-on' : 'radio-button-off'}
                      size={20}
                      color={ativo ? Brand.brand : Brand.muted}
                    />
                    <Text style={[styles.unidadeNome, ativo && styles.unidadeNomeAtiva]}>{u.nome}</Text>
                  </Pressable>
                );
              })}
            </View>
          </>
        )}

        {/* Mensagem + envio: só depois que as opções carregam. */}
        {!carregando && !erroCarregar && (
          <>
            <Text style={[styles.label, { marginTop: 22 }]}>Mensagem</Text>
            <TextInput
              style={styles.textarea}
              value={texto}
              onChangeText={(t) => {
                setTexto(t);
                if (erroEnvio) setErroEnvio(null);
              }}
              placeholder="Escreva sua mensagem para o SAU"
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
              style={[styles.enviar, !podeEnviar && styles.enviarDesativado]}
              onPress={enviar}
              disabled={!podeEnviar}>
              {enviando ? (
                <ActivityIndicator color="#fff" />
              ) : (
                <>
                  <Ionicons name="send" size={17} color="#fff" />
                  <Text style={styles.enviarTxt}>Enviar manifestação</Text>
                </>
              )}
            </Pressable>
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
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

  corpo: { flex: 1 },
  label: { fontSize: 13, fontWeight: '700', color: Brand.muted, marginBottom: 10 },

  carregando: { paddingVertical: 30, alignItems: 'center', gap: 8 },
  carregandoTxt: { fontSize: 13, color: Brand.muted },
  vazioTxt: { fontSize: 13.5, color: Brand.muted, lineHeight: 19 },

  tipos: { gap: 10 },
  tipoCard: {
    borderWidth: 1.5,
    borderColor: Brand.line,
    backgroundColor: Brand.surface,
    borderRadius: 14,
    padding: 14,
    gap: 6,
  },
  tipoCardAtivo: { borderColor: Brand.brand, backgroundColor: '#F1FAF7' },
  tipoCabecalho: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  tipoRotulo: { fontSize: 15, fontWeight: '800', color: Brand.ink },
  tipoRotuloAtivo: { color: Brand.brandDeep },
  tipoDescricao: { fontSize: 12.5, color: Brand.muted },

  unidadesEstado: { paddingVertical: 20, alignItems: 'center' },
  unidadesErro: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 14, backgroundColor: Brand.surface, borderRadius: 12, borderWidth: 1, borderColor: Brand.line },
  unidadesErroTxt: { flex: 1, fontSize: 13, color: Brand.muted },
  unidades: { gap: 8 },
  unidadeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 14,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: Brand.line,
    backgroundColor: Brand.surface,
  },
  unidadeRowAtiva: { borderColor: Brand.brand, backgroundColor: '#F1FAF7' },
  unidadeNome: { flex: 1, fontSize: 14.5, color: Brand.ink },
  unidadeNomeAtiva: { fontWeight: '700', color: Brand.brandDeep },

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

  erroBox: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 14 },
  erroTxt: { flex: 1, fontSize: 13, color: '#B23B4E' },

  enviar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginTop: 22,
    height: 52,
    borderRadius: 14,
    backgroundColor: Brand.brand,
  },
  enviarDesativado: { opacity: 0.5 },
  enviarTxt: { color: '#fff', fontSize: 15, fontWeight: '800' },
});
