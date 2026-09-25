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
 * Cerimônia de assinatura de um termo (TCLE): abre o sign_url da ZapSign (recebido por parâmetro)
 * num WebView. Ao concluir, a assinatura é confirmada pelo webhook no servidor (fonte de verdade);
 * aqui só detectamos o fim para dar o retorno visual e voltar ao Prontuário.
 */
export default function AssinarTermoScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const params = useLocalSearchParams<{ signUrl?: string; nome?: string; prontuarioId?: string }>();
  const signUrl = typeof params.signUrl === 'string' ? params.signUrl : '';
  const nome = typeof params.nome === 'string' ? params.nome : 'Termo';
  const prontuarioId = Number(params.prontuarioId);

  const [assinado, setAssinado] = useState(false);
  const marcou = useRef(false);

  function aoReceberMensagem(ev: WebViewMessageEvent) {
    const data = ev.nativeEvent.data;
    if (data.includes('zs-doc-signed') || data.includes('zs-signed-file-ready')) {
      setAssinado(true);
      // Marca "em confirmação" no back (uma vez) para o botão sumir ao voltar. Não bloqueia o retorno.
      if (!marcou.current && Number.isFinite(prontuarioId) && prontuarioId > 0) {
        marcou.current = true;
        marcarEmConfirmacao(prontuarioId).catch(() => {});
      }
    }
  }

  return (
    <View style={[styles.screen, { paddingBottom: insets.bottom }]}>
      <ScreenHeader title="Assinar termo" />

      {assinado ? (
        <View style={styles.estado}>
          <Ionicons name="checkmark-circle" size={64} color="#1E9E5A" />
          <Text style={styles.okTitulo}>Termo assinado!</Text>
          <Text style={styles.okTxt}>
            Sua assinatura de “{nome}” foi registrada. Em instantes o documento assinado aparece no seu prontuário.
          </Text>
          <Pressable style={styles.btn} onPress={() => router.back()} accessibilityRole="button">
            <Text style={styles.btnTxt}>Concluir</Text>
          </Pressable>
        </View>
      ) : signUrl ? (
        <WebView
          source={{ uri: signUrl }}
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
  });
