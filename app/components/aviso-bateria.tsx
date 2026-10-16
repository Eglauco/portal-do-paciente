import { Ionicons } from '@expo/vector-icons';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as IntentLauncher from 'expo-intent-launcher';
import { useEffect, useMemo, useState } from 'react';
import { Linking, Modal, Platform, Pressable, StyleSheet, Text, View } from 'react-native';

import { alpha, type Tema, useTema } from '@/hooks/use-tema';

const CHAVE = 'pop.avisoBateriaMostrado';

/**
 * Aviso ÚNICO (só Android) pedindo para liberar o app da otimização de bateria, para que as
 * mensagens da clínica cheguem na hora mesmo com o app em segundo plano por muito tempo. Mantém o
 * app alcançável pelo push (a confirmação de entrega "✓✓" roda enquanto o app está vivo); NÃO
 * resolve o app totalmente ENCERRADO (aí o 2º check sai ao reabrir), mas evita que a Samsung
 * "coloque o app pra dormir" cedo demais.
 *
 * Abre a tela de otimização de bateria do SISTEMA — sem declarar a permissão
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (restrita pela política do Google Play). Aparece uma vez;
 * tanto "Abrir configuração" quanto "Agora não" marcam como visto para não insistir.
 */
export function AvisoBateria() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const [visivel, setVisivel] = useState(false);

  useEffect(() => {
    if (Platform.OS !== 'android') return;
    AsyncStorage.getItem(CHAVE)
      .then((v) => {
        if (!v) setVisivel(true);
      })
      .catch(() => {
        // sem storage: não arrisca mostrar em loop; segue sem o aviso
      });
  }, []);

  const encerrar = () => {
    setVisivel(false);
    AsyncStorage.setItem(CHAVE, '1').catch(() => {});
  };

  const abrirConfig = async () => {
    try {
      // Lista de otimização de bateria do sistema: o usuário escolhe o app e marca "Não otimizar".
      await IntentLauncher.startActivityAsync('android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS');
    } catch {
      try {
        // Fallback: abre a página de configurações do próprio app (Bateria fica lá dentro).
        await Linking.openSettings();
      } catch {
        // sem a tela: segue; o app ainda funciona (a entrega pode só atrasar)
      }
    }
    encerrar();
  };

  if (!visivel) return null;

  return (
    <Modal visible transparent animationType="fade" statusBarTranslucent onRequestClose={encerrar}>
      <View style={styles.backdrop}>
        <View style={styles.card} accessibilityViewIsModal accessibilityRole="alert">
          <View style={styles.icone}>
            <Ionicons name="battery-charging" size={26} color={t.brandDeep} />
          </View>
          <Text style={styles.titulo}>Receber mensagens na hora</Text>
          <Text style={styles.mensagem}>
            Para você receber as mensagens da clínica rapidamente, mesmo com o aplicativo em segundo
            plano, permita que ele funcione sem restrição de bateria. Na próxima tela, escolha este
            aplicativo e marque “Não otimizar” (ou “Sem restrições”).
          </Text>
          <Pressable style={styles.btnOk} onPress={abrirConfig}>
            <Ionicons name="settings-outline" size={18} color="#fff" />
            <Text style={styles.btnOkTxt}>Abrir configuração</Text>
          </Pressable>
          <Pressable style={styles.btnDepois} onPress={encerrar}>
            <Text style={styles.btnDepoisTxt}>Agora não</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    backdrop: {
      flex: 1,
      backgroundColor: alpha(t.brandPine, 0.55),
      alignItems: 'center',
      justifyContent: 'center',
      padding: 24,
    },
    card: { width: '100%', maxWidth: 400, backgroundColor: t.surface, borderRadius: 24, padding: 22, alignItems: 'center' },
    icone: {
      width: 56,
      height: 56,
      borderRadius: 18,
      backgroundColor: t.brandTint,
      alignItems: 'center',
      justifyContent: 'center',
      marginBottom: 12,
    },
    titulo: { fontSize: 18, fontWeight: '800', color: t.ink, textAlign: 'center', letterSpacing: -0.3 },
    mensagem: { fontSize: 14.5, color: '#40514C', textAlign: 'center', lineHeight: 21, marginTop: 8 },
    btnOk: {
      width: '100%',
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 7,
      height: 50,
      borderRadius: 14,
      marginTop: 18,
      backgroundColor: t.brand,
    },
    btnOkTxt: { color: '#fff', fontSize: 15, fontWeight: '800' },
    btnDepois: { width: '100%', alignItems: 'center', justifyContent: 'center', height: 44, marginTop: 6 },
    btnDepoisTxt: { color: t.muted, fontSize: 14, fontWeight: '700' },
  });
