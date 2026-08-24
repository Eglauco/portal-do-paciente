import { Ionicons } from '@expo/vector-icons';
import { Tabs } from 'expo-router';
import React, { useState } from 'react';

import { AgendamentoModal } from '@/components/agendamento-modal';
import { HapticTab } from '@/components/haptic-tab';
import { TopBar } from '@/components/top-bar';
import { AGENDAMENTOS } from '@/constants/agendamentos';
import { Brand } from '@/constants/theme';

export default function TabLayout() {
  // Pop-up de entrada: primeiro agendamento aguardando confirmação.
  const pendente = AGENDAMENTOS.find((a) => a.status === 'aguardando') ?? null;
  const [entradaVisivel, setEntradaVisivel] = useState(true);

  return (
    <>
    <Tabs
      screenOptions={{
        header: () => <TopBar />,
        tabBarButton: HapticTab,
        tabBarActiveTintColor: Brand.brandDeep,
        tabBarInactiveTintColor: Brand.muted,
        tabBarLabelStyle: { fontSize: 11, fontWeight: '600' },
        tabBarStyle: {
          backgroundColor: Brand.surface,
          borderTopColor: Brand.line,
          height: 62,
          paddingTop: 6,
        },
      }}>
      <Tabs.Screen
        name="agendamentos"
        options={{
          title: 'Agenda',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'calendar' : 'calendar-outline'} size={24} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="chat"
        options={{
          title: 'Chat',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'chatbubbles' : 'chatbubbles-outline'} size={24} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="prontuario"
        options={{
          title: 'Prontuário',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'document-text' : 'document-text-outline'} size={24} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="nps"
        options={{
          title: 'NPS',
          tabBarIcon: ({ color, focused }) => (
            <Ionicons name={focused ? 'star' : 'star-outline'} size={24} color={color} />
          ),
        }}
      />
    </Tabs>

    <AgendamentoModal
      visivel={entradaVisivel}
      agendamento={pendente}
      onConfirmar={() => setEntradaVisivel(false)}
      onCancelar={() => setEntradaVisivel(false)}
      onFechar={() => setEntradaVisivel(false)}
    />
    </>
  );
}
