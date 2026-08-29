import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Brand } from '@/constants/theme';
import { useSessao } from '@/hooks/use-sessao';

/** Máscara de telefone BR "(00) 00000-0000" enquanto o paciente digita. */
function mascararTelefone(valor: string): string {
  const d = valor.replace(/\D/g, '').slice(0, 11);
  if (d.length === 0) return '';
  if (d.length <= 2) return `(${d}`;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

export default function LoginScreen() {
  const router = useRouter();
  const { ativar } = useSessao();

  const [telefone, setTelefone] = useState('');
  const [codigo, setCodigo] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [entrando, setEntrando] = useState(false);

  const podeEntrar = telefone.replace(/\D/g, '').length >= 10 && codigo.length === 6 && !entrando;

  async function entrar() {
    if (entrando) return;
    setErro(null);
    setEntrando(true);
    try {
      await ativar(telefone, codigo);
      router.replace('/(tabs)/agendamentos');
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível entrar. Tente novamente.');
      setEntrando(false);
    }
  }

  return (
    <View style={styles.root}>
      <StatusBar style="light" />

      {/* Faixa de marca */}
      <SafeAreaView edges={['top']} style={styles.brand}>
        <View style={styles.brandGlow} pointerEvents="none" />
        <Ionicons
          name="pulse"
          size={220}
          color="rgba(127,224,195,0.10)"
          style={styles.brandWatermark}
        />

        <View style={styles.wordmark}>
          <View style={styles.mark}>
            <Ionicons name="pulse" size={22} color={Brand.glow} />
          </View>
          <View>
            <Text style={styles.wordmarkName}>PORTAL DO PACIENTE</Text>
            <Text style={styles.wordmarkSub}>Cuidado conectado</Text>
          </View>
        </View>

        <View style={styles.pitch}>
          <Text style={styles.headline}>Seu cuidado,</Text>
          <Text style={[styles.headline, styles.headlineAccent]}>sempre por perto.</Text>
        </View>
      </SafeAreaView>

      {/* Folha do formulário */}
      <KeyboardAvoidingView
        style={styles.sheetWrap}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          style={styles.sheet}
          contentContainerStyle={styles.sheetContent}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}>
          <Text style={styles.title}>Entrar no aplicativo</Text>
          <Text style={styles.subtitle}>
            Use o telefone que você informou na unidade e o código que a recepção te passou.
          </Text>

          {/* Telefone */}
          <Text style={styles.label}>Telefone</Text>
          <View style={styles.inputWrap}>
            <Ionicons name="call-outline" size={20} color={Brand.muted} style={styles.inputIcon} />
            <TextInput
              style={styles.input}
              value={telefone}
              onChangeText={(t) => {
                setTelefone(mascararTelefone(t));
                if (erro) setErro(null);
              }}
              placeholder="(11) 99999-0000"
              placeholderTextColor="#9AAAA5"
              keyboardType="phone-pad"
              autoComplete="tel"
              inputMode="tel"
              maxLength={15}
              returnKeyType="next"
            />
          </View>

          {/* Código */}
          <Text style={styles.label}>Código de acesso</Text>
          <View style={styles.inputWrap}>
            <Ionicons
              name="key-outline"
              size={20}
              color={Brand.muted}
              style={styles.inputIcon}
            />
            <TextInput
              style={[styles.input, styles.codeInput]}
              value={codigo}
              onChangeText={(t) => {
                setCodigo(t.replace(/\D/g, '').slice(0, 6));
                if (erro) setErro(null);
              }}
              placeholder="000000"
              placeholderTextColor="#9AAAA5"
              keyboardType="number-pad"
              inputMode="numeric"
              maxLength={6}
              returnKeyType="done"
              onSubmitEditing={() => {
                if (podeEntrar) entrar();
              }}
            />
          </View>
          <Text style={styles.hint}>São 6 números. O código vale por 48 horas.</Text>

          {erro && (
            <View style={styles.erroBox}>
              <Ionicons name="alert-circle" size={18} color="#B23B4E" />
              <Text style={styles.erroTxt}>{erro}</Text>
            </View>
          )}

          {/* Entrar */}
          <Pressable
            style={({ pressed }) => [
              styles.primaryBtn,
              pressed && styles.primaryBtnPressed,
              !podeEntrar && styles.primaryBtnOff,
            ]}
            onPress={entrar}
            disabled={!podeEntrar}
            accessibilityRole="button">
            {entrando ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.primaryBtnText}>Entrar</Text>
            )}
          </Pressable>

          {/* Rodapé */}
          <View style={styles.foot}>
            <Ionicons name="information-circle-outline" size={18} color={Brand.muted} />
            <Text style={styles.footText}>
              Ainda não tem código? Procure a recepção da sua unidade de saúde.
            </Text>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: Brand.brandDeep,
  },
  brand: {
    paddingHorizontal: 28,
    paddingBottom: 34,
    overflow: 'hidden',
  },
  brandGlow: {
    position: 'absolute',
    width: 360,
    height: 360,
    right: -140,
    top: -120,
    borderRadius: 180,
    backgroundColor: 'rgba(14,140,127,0.45)',
  },
  brandWatermark: {
    position: 'absolute',
    right: -30,
    bottom: -40,
  },
  wordmark: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    marginTop: 8,
  },
  mark: {
    width: 44,
    height: 44,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.10)',
    borderWidth: 1,
    borderColor: 'rgba(234,250,244,0.28)',
  },
  wordmarkName: {
    color: Brand.onBrand,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1.6,
  },
  wordmarkSub: {
    color: 'rgba(234,250,244,0.62)',
    fontSize: 11,
    letterSpacing: 2,
    marginTop: 2,
  },
  pitch: {
    marginTop: 34,
  },
  headline: {
    color: Brand.onBrand,
    fontSize: 30,
    lineHeight: 34,
    fontWeight: '600',
    letterSpacing: -0.3,
  },
  headlineAccent: {
    color: Brand.glow,
  },
  sheetWrap: {
    flex: 1,
  },
  sheet: {
    flex: 1,
    backgroundColor: Brand.surface,
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    marginTop: -8,
  },
  sheetContent: {
    paddingHorizontal: 28,
    paddingTop: 32,
    paddingBottom: 40,
  },
  title: {
    fontSize: 26,
    fontWeight: '700',
    color: Brand.ink,
    letterSpacing: -0.3,
  },
  subtitle: {
    fontSize: 15,
    color: Brand.muted,
    marginTop: 6,
    marginBottom: 26,
    lineHeight: 21,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    color: Brand.ink,
    marginBottom: 8,
  },
  inputWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 56,
    backgroundColor: '#F7FAF9',
    borderWidth: 1,
    borderColor: Brand.line,
    borderRadius: 14,
    paddingHorizontal: 14,
    marginBottom: 10,
  },
  inputIcon: {
    marginRight: 10,
  },
  input: {
    flex: 1,
    fontSize: 17,
    color: Brand.ink,
    height: '100%',
  },
  codeInput: {
    fontSize: 24,
    fontWeight: '700',
    letterSpacing: 8,
  },
  hint: {
    fontSize: 13,
    color: Brand.muted,
    marginBottom: 22,
  },
  erroBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: '#FDECEE',
    borderWidth: 1,
    borderColor: '#F3D6DB',
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 14,
    marginBottom: 18,
  },
  erroTxt: {
    flex: 1,
    fontSize: 14,
    color: '#8A2B3A',
    lineHeight: 19,
  },
  primaryBtn: {
    height: 56,
    borderRadius: 15,
    backgroundColor: Brand.brand,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: Brand.brandDeep,
    shadowOpacity: 0.35,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 10 },
    elevation: 4,
  },
  primaryBtnPressed: {
    backgroundColor: Brand.brandDeep,
  },
  primaryBtnOff: {
    backgroundColor: '#A9C9C0',
    shadowOpacity: 0,
    elevation: 0,
  },
  primaryBtnText: {
    color: '#fff',
    fontSize: 17,
    fontWeight: '700',
  },
  foot: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    marginTop: 28,
    paddingHorizontal: 6,
  },
  footText: {
    flex: 1,
    fontSize: 13.5,
    color: Brand.muted,
    lineHeight: 19,
  },
});
