import { Ionicons } from '@expo/vector-icons';
import Constants from 'expo-constants';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ScreenHeader } from '@/components/screen-header';
import { type Tema, useTema } from '@/hooks/use-tema';
import { buscarVersaoBack, formatarData, type VersaoInfo } from '@/services/versao';

/**
 * Versão + data do PRÓPRIO app, lidas em runtime do config embutido (app.json → "version" e
 * "extra.dataApp"). Cada componente é dono só da SUA versão: estas vêm do app.json, NÃO do back —
 * refletem o binário que o paciente realmente tem instalado.
 */
const VERSAO_APP = Constants.expoConfig?.version ?? '-';
const DATA_APP = (Constants.expoConfig?.extra?.dataApp as string | undefined) ?? undefined;

interface Bloco {
  icon: string;
  rotulo: string;
  versao: string;
  data: string;
}

export default function SobreScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const insets = useSafeAreaInsets();
  const [back, setBack] = useState<VersaoInfo | null>(null);
  const [carregando, setCarregando] = useState(true);

  // Busca SÓ a versão do back (GET /versao). Fail-soft: se falhar, a tela ainda mostra a versão
  // instalada do app (que vem do próprio app.json, não do back).
  useEffect(() => {
    let vivo = true;
    buscarVersaoBack()
      .then((v) => {
        if (vivo) setBack(v);
      })
      .finally(() => {
        if (vivo) setCarregando(false);
      });
    return () => {
      vivo = false;
    };
  }, []);

  // Cada um mostra a sua versão real: o App vem do app.json (Constants); o Back vem do /versao.
  const blocos: Bloco[] = [
    {
      icon: 'phone-portrait-outline',
      rotulo: 'Aplicativo',
      versao: VERSAO_APP,
      data: formatarData(DATA_APP),
    },
    {
      icon: 'server-outline',
      rotulo: 'Servidor',
      versao: back?.versao ?? '-',
      data: formatarData(back?.data),
    },
  ];

  return (
    <View style={styles.screen}>
      <ScreenHeader title="Sobre" />
      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
        <Text style={styles.titulo}>Versões</Text>
        <Text style={styles.subtitulo}>Informações da versão instalada do aplicativo e do servidor.</Text>

        <View style={styles.card}>
          {blocos.map((b, i) => {
            // Só o Servidor depende do fetch; o Aplicativo já vem do app.json (sem espera).
            const esperandoFetch = b.rotulo === 'Servidor' && carregando;
            return (
              <View key={b.rotulo} style={[styles.linha, i > 0 && styles.linhaBorda]}>
                <View style={styles.linhaIcon}>
                  <Ionicons name={b.icon as never} size={20} color={t.brandDeep} />
                </View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.rotulo}>{b.rotulo}</Text>
                  <Text style={styles.versao}>Versão {b.versao}</Text>
                </View>
                <View style={styles.dataBox}>
                  {esperandoFetch ? (
                    <ActivityIndicator size="small" color={t.brand} />
                  ) : (
                    <>
                      <Text style={styles.dataRotulo}>Publicada em</Text>
                      <Text style={styles.data}>{b.data}</Text>
                    </>
                  )}
                </View>
              </View>
            );
          })}
        </View>
      </ScrollView>
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    screen: { flex: 1, backgroundColor: t.bg },
    content: { padding: 20, paddingBottom: 40 },
    titulo: { fontSize: 22, fontWeight: '800', color: t.ink, letterSpacing: -0.4 },
    subtitulo: { fontSize: 13.5, color: t.muted, lineHeight: 19, marginTop: 6, marginBottom: 18 },
    card: {
      backgroundColor: t.surface,
      borderRadius: 18,
      borderWidth: 1,
      borderColor: t.line,
      paddingHorizontal: 14,
    },
    linha: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 15 },
    linhaBorda: { borderTopWidth: 1, borderTopColor: t.brandTint },
    linhaIcon: {
      width: 40,
      height: 40,
      borderRadius: 12,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: t.brandTint,
    },
    rotulo: { fontSize: 12, color: t.muted },
    versao: { fontSize: 15.5, fontWeight: '700', color: t.ink, marginTop: 1 },
    dataBox: { alignItems: 'flex-end', minWidth: 72 },
    dataRotulo: { fontSize: 11, color: t.muted },
    data: { fontSize: 14, fontWeight: '700', color: t.brandDeep, marginTop: 1 },
  });
