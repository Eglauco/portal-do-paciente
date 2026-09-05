import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Brand } from '@/constants/theme';
import { useAtualizarComPush } from '@/hooks/use-atualizar-com-push';
import { usePerfilFoto } from '@/hooks/use-perfil-foto';
import { useSessao } from '@/hooks/use-sessao';
import { contarNaoLidas } from '@/services/notificacoes-lista';

export function TopBar() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { sessao } = useSessao();
  const { fotoUrl } = usePerfilFoto();
  const primeiroNome = sessao?.nome?.trim().split(/\s+/)[0] ?? 'Paciente';
  const [naoLidas, setNaoLidas] = useState(0);

  const atualizarContagem = useCallback(() => {
    contarNaoLidas()
      .then(setNaoLidas)
      .catch(() => {}); // silencioso: badge some se falhar
  }, []);

  // Recarrega ao focar a aba e quando chega um push (app aberto) ou volta ao foco.
  // (o wrapper evita devolver a Promise como "cleanup" para o useFocusEffect)
  useFocusEffect(
    useCallback(() => {
      atualizarContagem();
    }, [atualizarContagem]),
  );
  useAtualizarComPush(atualizarContagem);

  return (
    <View style={[styles.container, { paddingTop: insets.top + 8 }]}>
      <Pressable
        style={({ pressed }) => [styles.left, pressed && styles.leftPressed]}
        onPress={() => router.push('/perfil')}
        accessibilityRole="button"
        accessibilityLabel="Abrir perfil">
        {fotoUrl ? (
          <Image source={fotoUrl} style={styles.avatar} contentFit="cover" transition={200} />
        ) : (
          <View style={[styles.avatar, styles.avatarVazio]}>
            <Ionicons name="person" size={24} color={Brand.muted} />
          </View>
        )}
        <View>
          <Text style={styles.hello}>Olá,</Text>
          <Text style={styles.name}>{primeiroNome}</Text>
        </View>
      </Pressable>

      <Pressable
        style={({ pressed }) => [styles.bell, pressed && styles.bellPressed]}
        onPress={() => router.push('/notificacoes')}
        accessibilityRole="button"
        accessibilityLabel={
          naoLidas > 0 ? `Notificações, ${naoLidas} não lidas` : 'Notificações'
        }>
        <Ionicons name="notifications-outline" size={22} color={Brand.ink} />
        {naoLidas > 0 && (
          <View style={styles.badge}>
            <Text style={styles.badgeTxt}>{naoLidas > 99 ? '99+' : naoLidas}</Text>
          </View>
        )}
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingBottom: 14,
    backgroundColor: Brand.surface,
    borderBottomWidth: 1,
    borderBottomColor: Brand.line,
  },
  left: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    borderRadius: 14,
    padding: 4,
    marginLeft: -4,
  },
  leftPressed: {
    backgroundColor: '#EAF2EF',
  },
  avatar: {
    width: 46,
    height: 46,
    borderRadius: 23,
    backgroundColor: Brand.line,
  },
  avatarVazio: {
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#E7F3EF',
  },
  hello: {
    fontSize: 12,
    color: Brand.muted,
  },
  name: {
    fontSize: 16,
    fontWeight: '700',
    color: Brand.ink,
    letterSpacing: -0.2,
  },
  bell: {
    width: 44,
    height: 44,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.bg,
    borderWidth: 1,
    borderColor: Brand.line,
  },
  bellPressed: {
    backgroundColor: '#EAF2EF',
  },
  badge: {
    position: 'absolute',
    top: 4,
    right: 4,
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    paddingHorizontal: 4,
    backgroundColor: '#E0952A',
    borderWidth: 2,
    borderColor: Brand.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  badgeTxt: {
    fontSize: 10,
    fontWeight: '800',
    color: '#fff',
    lineHeight: 12,
  },
});
