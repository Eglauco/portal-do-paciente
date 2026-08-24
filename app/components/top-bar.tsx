import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Brand } from '@/constants/theme';

const FOTO_PACIENTE = 'https://i.pravatar.cc/160?img=47';

export function TopBar() {
  const insets = useSafeAreaInsets();
  const router = useRouter();

  return (
    <View style={[styles.container, { paddingTop: insets.top + 8 }]}>
      <Pressable
        style={({ pressed }) => [styles.left, pressed && styles.leftPressed]}
        onPress={() => router.push('/perfil')}
        accessibilityRole="button"
        accessibilityLabel="Abrir perfil">
        <View style={styles.avatarRing}>
          <Image
            source={FOTO_PACIENTE}
            style={styles.avatar}
            contentFit="cover"
            transition={200}
            placeholder={{ blurhash: 'L6Pj0^i_.AyE_3t7t7R**0o#DgR4' }}
          />
        </View>
        <View>
          <Text style={styles.hello}>Olá,</Text>
          <Text style={styles.name}>Mariana Duarte</Text>
        </View>
      </Pressable>

      <Pressable
        style={({ pressed }) => [styles.bell, pressed && styles.bellPressed]}
        onPress={() => router.push('/notificacoes')}
        accessibilityRole="button"
        accessibilityLabel="Notificações">
        <Ionicons name="notifications-outline" size={22} color={Brand.ink} />
        <View style={styles.badge} />
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
  avatarRing: {
    width: 46,
    height: 46,
    borderRadius: 23,
    padding: 2,
    backgroundColor: Brand.surface,
    borderWidth: 2,
    borderColor: Brand.glow,
  },
  avatar: {
    width: '100%',
    height: '100%',
    borderRadius: 20,
    backgroundColor: Brand.line,
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
    top: 10,
    right: 11,
    width: 9,
    height: 9,
    borderRadius: 5,
    backgroundColor: '#E0952A',
    borderWidth: 2,
    borderColor: Brand.surface,
  },
});
