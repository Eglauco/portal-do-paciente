import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import {
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

export default function LoginScreen() {
  const router = useRouter();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(false);

  // Sem regra de negócio nesta etapa — apenas a experiência visual.
  const handleEnter = () => router.replace('/(tabs)/agendamentos');

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
          <Text style={styles.title}>Bem-vindo de volta</Text>
          <Text style={styles.subtitle}>Entre para acompanhar sua saúde.</Text>

          {/* E-mail */}
          <Text style={styles.label}>E-mail</Text>
          <View style={styles.inputWrap}>
            <Ionicons name="mail-outline" size={20} color={Brand.muted} style={styles.inputIcon} />
            <TextInput
              style={styles.input}
              value={email}
              onChangeText={setEmail}
              placeholder="voce@email.com"
              placeholderTextColor="#9AAAA5"
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
              inputMode="email"
            />
          </View>

          {/* Senha */}
          <Text style={styles.label}>Senha</Text>
          <View style={styles.inputWrap}>
            <Ionicons
              name="lock-closed-outline"
              size={20}
              color={Brand.muted}
              style={styles.inputIcon}
            />
            <TextInput
              style={styles.input}
              value={password}
              onChangeText={setPassword}
              placeholder="••••••••"
              placeholderTextColor="#9AAAA5"
              secureTextEntry={!showPassword}
              autoCapitalize="none"
              autoComplete="password"
            />
            <Pressable
              onPress={() => setShowPassword((v) => !v)}
              hitSlop={8}
              style={styles.eyeBtn}
              accessibilityRole="button"
              accessibilityLabel={showPassword ? 'Ocultar senha' : 'Mostrar senha'}>
              <Ionicons
                name={showPassword ? 'eye-off-outline' : 'eye-outline'}
                size={20}
                color={Brand.muted}
              />
            </Pressable>
          </View>

          {/* Lembrar + esqueci */}
          <View style={styles.row}>
            <Pressable
              style={styles.remember}
              onPress={() => setRemember((v) => !v)}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: remember }}>
              <View style={[styles.checkbox, remember && styles.checkboxOn]}>
                {remember && <Ionicons name="checkmark" size={14} color="#fff" />}
              </View>
              <Text style={styles.rememberText}>Lembrar de mim</Text>
            </Pressable>
            <Pressable hitSlop={8}>
              <Text style={styles.link}>Esqueci minha senha</Text>
            </Pressable>
          </View>

          {/* Entrar */}
          <Pressable
            style={({ pressed }) => [styles.primaryBtn, pressed && styles.primaryBtnPressed]}
            onPress={handleEnter}
            accessibilityRole="button">
            <Text style={styles.primaryBtnText}>Entrar</Text>
          </Pressable>

          {/* Divisor */}
          <View style={styles.divider}>
            <View style={styles.dividerLine} />
            <Text style={styles.dividerText}>ou continue com</Text>
            <View style={styles.dividerLine} />
          </View>

          {/* gov.br */}
          <Pressable
            style={({ pressed }) => [styles.govBtn, pressed && styles.govBtnPressed]}
            accessibilityRole="button">
            <Text style={styles.govText}>
              Entrar com <Text style={styles.govBlue}>gov</Text>
              <Text style={styles.govGold}>.br</Text>
            </Text>
          </Pressable>

          {/* Rodapé */}
          <View style={styles.foot}>
            <Text style={styles.footText}>Ainda não tem acesso? </Text>
            <Pressable hitSlop={6}>
              <Text style={styles.link}>Solicitar cadastro</Text>
            </Pressable>
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
    height: 52,
    backgroundColor: '#F7FAF9',
    borderWidth: 1,
    borderColor: Brand.line,
    borderRadius: 14,
    paddingHorizontal: 14,
    marginBottom: 18,
  },
  inputIcon: {
    marginRight: 10,
  },
  input: {
    flex: 1,
    fontSize: 15,
    color: Brand.ink,
    height: '100%',
  },
  eyeBtn: {
    padding: 4,
    marginLeft: 4,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 2,
    marginBottom: 24,
  },
  remember: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  checkbox: {
    width: 20,
    height: 20,
    borderRadius: 6,
    borderWidth: 1.5,
    borderColor: Brand.line,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  checkboxOn: {
    backgroundColor: Brand.brand,
    borderColor: Brand.brand,
  },
  rememberText: {
    fontSize: 14,
    color: Brand.muted,
  },
  link: {
    fontSize: 14,
    fontWeight: '600',
    color: Brand.brandDeep,
  },
  primaryBtn: {
    height: 54,
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
  primaryBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
  divider: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    marginVertical: 24,
  },
  dividerLine: {
    flex: 1,
    height: 1,
    backgroundColor: Brand.line,
  },
  dividerText: {
    fontSize: 12,
    color: Brand.muted,
  },
  govBtn: {
    height: 54,
    borderRadius: 15,
    borderWidth: 1,
    borderColor: Brand.line,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
  },
  govBtnPressed: {
    backgroundColor: '#F7FAF9',
  },
  govText: {
    fontSize: 15,
    color: Brand.ink,
    fontWeight: '600',
  },
  govBlue: {
    color: '#1351B4',
    fontWeight: '800',
  },
  govGold: {
    color: '#F2B705',
    fontWeight: '800',
  },
  foot: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 28,
  },
  footText: {
    fontSize: 14,
    color: Brand.muted,
  },
});
