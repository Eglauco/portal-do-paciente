import { Ionicons } from '@expo/vector-icons';
import { type ComponentProps, useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { type Tema, useTema } from '@/hooks/use-tema';
import { abrirEmApp, opcoesComoChegar } from '@/services/unidade';

interface Props {
  endereco: string;
  /** Coordenadas da unidade (quando geocodificada): deep link mais preciso que o endereço. */
  latitude?: number | null;
  longitude?: number | null;
  /** Chamado após abrir um app (p.ex. fechar o seletor). */
  onEscolher?: () => void;
}

/**
 * Lista de apps de mapa ("Google Maps / Waze / Apple Maps") para "Como chegar". Apenas a lista de
 * linhas — quem decide a moldura (bottom-sheet numa tela, inline dentro de um modal) é o chamador.
 */
export function OpcoesMapa({ endereco, latitude, longitude, onEscolher }: Props) {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const opcoes = useMemo(
    () => opcoesComoChegar(endereco, latitude, longitude),
    [endereco, latitude, longitude],
  );

  return (
    <View>
      {opcoes.map((o) => (
        <Pressable
          key={o.chave}
          style={({ pressed }) => [styles.linha, pressed && styles.linhaPressed]}
          onPress={() => {
            abrirEmApp(o);
            onEscolher?.();
          }}
          accessibilityRole="button"
          accessibilityLabel={`Abrir a rota no ${o.nome}`}>
          <View style={styles.icone}>
            <Ionicons name={o.icone as ComponentProps<typeof Ionicons>['name']} size={22} color={t.brandDeep} />
          </View>
          <Text style={styles.nome}>{o.nome}</Text>
          <Ionicons name="open-outline" size={18} color={t.muted} />
        </Pressable>
      ))}
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    linha: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 12,
      paddingVertical: 10,
      paddingHorizontal: 8,
      borderRadius: 14,
    },
    linhaPressed: { backgroundColor: t.brandTint },
    icone: {
      width: 40,
      height: 40,
      borderRadius: 20,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: t.brandTint,
    },
    nome: { flex: 1, fontSize: 16, fontWeight: '600', color: t.ink },
  });
