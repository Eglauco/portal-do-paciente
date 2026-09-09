import { Ionicons } from '@expo/vector-icons';
import { Tabs } from 'expo-router';
import React from 'react';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { HapticTab } from '@/components/haptic-tab';
import { TopBar } from '@/components/top-bar';
import { useSessao } from '@/hooks/use-sessao';
import { useTema } from '@/hooks/use-tema';
import { podeVer } from '@/services/sessao';

export default function TabLayout() {
  const insets = useSafeAreaInsets();
  const t = useTema();
  const { sessao } = useSessao();
  // Perfil dependente sem acesso a uma funcionalidade → a aba some (href: null).
  // Prontuário e NPS não são controlados: sempre visíveis.
  const abaOculta = (func: Parameters<typeof podeVer>[1]) => (podeVer(sessao, func) ? undefined : null);

  return (
    <Tabs
      screenOptions={{
        header: () => <TopBar />,
        tabBarButton: HapticTab,
        tabBarActiveTintColor: t.brandDeep,
        tabBarInactiveTintColor: t.muted,
        tabBarLabelStyle: { fontSize: 11, fontWeight: '600' },
        tabBarStyle: {
          backgroundColor: t.surface,
          borderTopColor: t.line,
          height: 62 + insets.bottom,
          paddingTop: 6,
          paddingBottom: insets.bottom,
        },
      }}>
      <Tabs.Screen
        name="novidades"
        options={{
          title: 'Novidades',
          href: abaOculta('REDE_SOCIAL'),
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'home' : 'home-outline'} size={24} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="agendamentos"
        options={{
          title: 'Agenda',
          href: abaOculta('AGENDAMENTOS'),
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'calendar' : 'calendar-outline'} size={24} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="chat"
        options={{
          title: 'Chat',
          href: abaOculta('CHAT'),
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'chatbubbles' : 'chatbubbles-outline'} size={24} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="sau"
        options={{
          title: 'SAU',
          href: abaOculta('SAU'),
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'megaphone' : 'megaphone-outline'} size={24} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="prontuario"
        options={{
          title: 'Prontuário',
          href: abaOculta('PRONTUARIO'),
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'document-text' : 'document-text-outline'} size={24} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="nps"
        options={{
          title: 'NPS',
          href: abaOculta('NPS'),
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'star' : 'star-outline'} size={24} color={color} />
          ),
        }}
      />
    </Tabs>
  );
}
