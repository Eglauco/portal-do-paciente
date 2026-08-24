import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Brand } from '@/constants/theme';

export function ScreenHeader({ title, action }: { title: string; action?: ReactNode }) {
  const insets = useSafeAreaInsets();
  const router = useRouter();

  return (
    <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
      <Pressable
        style={({ pressed }) => [styles.back, pressed && styles.backPressed]}
        onPress={() => router.back()}
        accessibilityRole="button"
        accessibilityLabel="Voltar">
        <Ionicons name="chevron-back" size={24} color={Brand.ink} />
      </Pressable>
      <Text style={styles.title} numberOfLines={1}>
        {title}
      </Text>
      <View style={styles.action}>{action}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 12,
    paddingBottom: 12,
    backgroundColor: Brand.surface,
    borderBottomWidth: 1,
    borderBottomColor: Brand.line,
  },
  back: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  backPressed: { backgroundColor: '#EAF2EF' },
  title: { flex: 1, fontSize: 18, fontWeight: '800', color: Brand.ink, letterSpacing: -0.3 },
  action: { minWidth: 40, alignItems: 'flex-end' },
});
