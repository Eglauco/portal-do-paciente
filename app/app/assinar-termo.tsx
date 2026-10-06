import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ScreenHeader } from '@/components/screen-header';
import { type Tema, useTema } from '@/hooks/use-tema';
import { marcarEmConfirmacao } from '@/services/prontuario';

// Encaminha ao RN os eventos "zs-*" que a página da ZapSign dispara (fim da cerimônia).
// A Autentique NÃO emite esses eventos — lá o avanço é pelo botão manual ("Assinei").
const INJECT = `
  (function(){
    function fwd(d){ try{ if(typeof d==='string' && d.indexOf('zs-')===0){ window.ReactNativeWebView.postMessage(d);} }catch(e){} }
    window.addEventListener('message', function(e){ fwd(e && e.data); }, false);
    var _pm = window.postMessage;
    try{ window.postMessage = function(m){ fwd(m); return _pm.apply(window, arguments); }; }catch(e){}
    true;
  })();
`;

/**
 * Cerimônia de assinatura de termos (TCLE). Abre o(s) link(s) da cerimônia num WebView. A ZapSign entrega
 * um link só e dispara os eventos zs-* ao concluir (avanço automático); a Autentique pode entregar vários
 * links (modo separado) e não dispara eventos, então o paciente confirma cada termo no botão "Assinei". Ao
 * final, marca os termos como "em confirmação" e volta ao Prontuário — a confirmação real vem pelo webhook.
 */
export default function AssinarTermoScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const params = useLocalSearchParams<{
    signUrls?: string;
    signUrl?: string;
    nome?: string;
    provedor?: string;
    prontuarioId?: string;
  }>();

  // Aceita signUrls (JSON, novo) ou signUrl (compat).
  const links = useMemo(() => {
    if (typeof params.signUrls === 'string') {
      try {
        const arr = JSON.parse(params.signUrls);
        if (Array.isArray(arr)) return arr.filter((x): x is string => typeof x === 'string' && x.length > 0);
      } catch {
        // ignora e cai no fallback
      }
    }
    return typeof params.signUrl === 'string' && params.signUrl ? [params.signUrl] : [];
  }, [params.signUrls, params.signUrl]);

  const provedor = typeof params.provedor === 'string' ? params.provedor : 'ZAPSIGN';
  const prontuarioId = Number(params.prontuarioId);
  const ehZapsign = provedor === 'ZAPSIGN';
  const total = links.length;

  const [indice, setIndice] = useState(0);
  const [assinado, setAssinado] = useState(false);
  const marcou = useRef(false);

  function concluir() {
    // Marca "em confirmação" no back (uma vez) para o botão sumir e mostrar o loop de conferência.
    if (!marcou.current && Number.isFinite(prontuarioId) && prontuarioId > 0) {
      marcou.current = true;
      marcarEmConfirmacao(prontuarioId).catch(() => {});
    }
    setAssinado(true);
  }

  function avancar() {
    if (indice < total - 1) {
      setIndice((i) => i + 1);
    } else {
      concluir();
    }
  }

  function aoReceberMensagem(ev: WebViewMessageEvent) {
    // Só a ZapSign emite estes eventos; a Autentique avança pelo botão manual.
    const data = ev.nativeEvent.data;
    if (data.includes('zs-doc-signed') || data.includes('zs-signed-file-ready')) {
      avancar();
    }
  }

  return (
    <View style={[styles.screen, { paddingBottom: insets.bottom }]}>
      <ScreenHeader title="Assinar termo" />

      {assinado ? (
        <View style={styles.estado}>
          <Ionicons name="checkmark-circle" size={64} color="#1E9E5A" />
          <Text style={styles.okTitulo}>{total > 1 ? 'Termos assinados!' : 'Termo assinado!'}</Text>
          <Text style={styles.okTxt}>
            Sua assinatura foi registrada. Em instantes o documento assinado aparece no seu prontuário.
          </Text>
          <Pressable style={styles.btn} onPress={() => router.back()} accessibilityRole="button">
            <Text style={styles.btnTxt}>Concluir</Text>
          </Pressable>
        </View>
      ) : total > 0 ? (
        <View style={{ flex: 1 }}>
          <WebView
            key={indice}
            source={{ uri: links[indice] }}
            style={styles.web}
            injectedJavaScript={INJECT}
            onMessage={aoReceberMensagem}
            javaScriptEnabled
            domStorageEnabled
            originWhitelist={['*']}
            startInLoadingState
            renderLoading={() => (
              <View style={styles.estado}>
                <ActivityIndicator color={t.brand} />
                <Text style={styles.okTxt}>Carregando o termo…</Text>
              </View>
            )}
          />
          {/* Rodapé de avanço: só na Autentique (sem eventos) ou quando há vários termos. */}
          {(!ehZapsign || total > 1) && (
            <View style={styles.footer}>
              {total > 1 && (
                <Text style={styles.footerInfo}>
                  Termo {indice + 1} de {total}
                </Text>
              )}
              <Pressable style={styles.footerBtn} onPress={avancar} accessibilityRole="button">
                <Text style={styles.footerBtnTxt}>
                  {indice < total - 1 ? 'Assinei — próximo termo' : 'Assinei — concluir'}
                </Text>
              </Pressable>
            </View>
          )}
        </View>
      ) : (
        <View style={styles.estado}>
          <Ionicons name="alert-circle" size={48} color="#B23B4E" />
          <Text style={styles.okTitulo}>Não foi possível abrir</Text>
          <Text style={styles.okTxt}>Volte e tente iniciar a assinatura novamente.</Text>
          <Pressable style={styles.btn} onPress={() => router.back()} accessibilityRole="button">
            <Text style={styles.btnTxt}>Voltar</Text>
          </Pressable>
        </View>
      )}
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    screen: { flex: 1, backgroundColor: t.bg },
    web: { flex: 1, backgroundColor: '#fff' },
    estado: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 28, gap: 10 },
    okTitulo: { fontSize: 20, fontWeight: '800', color: t.ink },
    okTxt: { fontSize: 14, color: t.muted, textAlign: 'center', lineHeight: 20 },
    btn: {
      marginTop: 10,
      height: 46,
      paddingHorizontal: 24,
      borderRadius: 14,
      backgroundColor: t.brand,
      alignItems: 'center',
      justifyContent: 'center',
    },
    btnTxt: { color: '#fff', fontSize: 15, fontWeight: '700' },
    footer: {
      borderTopWidth: 1,
      borderTopColor: t.line,
      backgroundColor: t.surface,
      paddingHorizontal: 16,
      paddingTop: 10,
      paddingBottom: 12,
      gap: 8,
    },
    footerInfo: { fontSize: 12.5, fontWeight: '700', color: t.muted, textAlign: 'center' },
    footerBtn: {
      height: 48,
      borderRadius: 14,
      backgroundColor: t.brand,
      alignItems: 'center',
      justifyContent: 'center',
    },
    footerBtnTxt: { color: '#fff', fontSize: 15, fontWeight: '700' },
  });
