import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';

import { alpha, type Tema, useTema } from '@/hooks/use-tema';
import { useSessao } from '@/hooks/use-sessao';

/**
 * Definição OBRIGATÓRIA da senha (PIN de 6 dígitos) logo após o OTP, antes de escolher o perfil.
 * Com a senha, o próximo acesso entra sem SMS (reduz custo). A navegação de saída é do Navegacao:
 * ao salvar, a sessão passa a "senha definida" e o app segue para "Selecionar Perfil".
 */
export default function DefinirSenhaScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { definirSenha, sair } = useSessao();

  const [senha, setSenha] = useState('');
  const [confirmar, setConfirmar] = useState('');
  const [mostrar, setMostrar] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  const senhaValida = senha.length === 6;
  const confere = confirmar.length === 6 && confirmar === senha;
  const podeSalvar = senhaValida && confere && !salvando;

  async function salvar() {
    if (!senhaValida) {
      setErro('A senha deve ter 6 dígitos numéricos.');
      return;
    }
    if (!confere) {
      setErro('As senhas não são iguais.');
      return;
    }
    setErro(null);
    setSalvando(true);
    try {
      await definirSenha(senha);
      // Ao concluir, o Navegacao leva à tela "Selecionar Perfil" (precisaDefinirSenha = false).
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível salvar a senha. Tente novamente.');
      setSalvando(false);
    }
  }

  function sairDaConta() {
    if (salvando) return;
    Alert.alert('Sair sem definir a senha?', 'Você precisará entrar novamente da próxima vez.', [
      { text: 'Cancelar', style: 'cancel' },
      {
        text: 'Sair',
        style: 'destructive',
        onPress: async () => {
          await sair();
          router.replace('/');
        },
      },
    ]);
  }

  return (
    <View style={styles.root}>
      <StatusBar style="light" />

      <SafeAreaView edges={['top']} style={styles.brand}>
        <View style={styles.mark}>
          <Ionicons name="lock-closed" size={22} color={t.glow} />
        </View>
        <Text style={styles.titulo}>Crie sua senha</Text>
        <Text style={styles.subtitulo}>
          Defina uma senha de 6 dígitos. Na próxima vez você entra com ela, sem esperar o SMS.
        </Text>
      </SafeAreaView>

      <KeyboardAvoidingView
        style={styles.sheetWrap}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          style={styles.sheet}
          contentContainerStyle={styles.sheetContent}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}>
          <Text style={styles.label}>Nova senha</Text>
          <View style={styles.inputWrap}>
            <Ionicons name="lock-closed-outline" size={20} color={t.muted} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, styles.codeInput]}
              value={senha}
              onChangeText={(valor) => {
                setSenha(valor.replace(/\D/g, '').slice(0, 6));
                if (erro) setErro(null);
              }}
              placeholder="••••••"
              placeholderTextColor="#9AAAA5"
              keyboardType="number-pad"
              inputMode="numeric"
              maxLength={6}
              secureTextEntry={!mostrar}
              autoFocus
              returnKeyType="next"
            />
            <Pressable
              onPress={() => setMostrar((v) => !v)}
              hitSlop={10}
              accessibilityRole="button"
              accessibilityLabel={mostrar ? 'Ocultar senha' : 'Mostrar senha'}>
              <Ionicons name={mostrar ? 'eye-off-outline' : 'eye-outline'} size={20} color={t.muted} />
            </Pressable>
          </View>

          <Text style={[styles.label, styles.labelEspaco]}>Repita a senha</Text>
          <View style={styles.inputWrap}>
            <Ionicons name="lock-closed-outline" size={20} color={t.muted} style={styles.inputIcon} />
            <TextInput
              style={[styles.input, styles.codeInput]}
              value={confirmar}
              onChangeText={(valor) => {
                setConfirmar(valor.replace(/\D/g, '').slice(0, 6));
                if (erro) setErro(null);
              }}
              placeholder="••••••"
              placeholderTextColor="#9AAAA5"
              keyboardType="number-pad"
              inputMode="numeric"
              maxLength={6}
              secureTextEntry={!mostrar}
              returnKeyType="done"
              onSubmitEditing={() => {
                if (podeSalvar) salvar();
              }}
            />
          </View>
          <Text style={styles.hint}>Use 6 números que você lembre com facilidade.</Text>

          {erro && (
            <View style={styles.erroBox}>
              <Ionicons name="alert-circle" size={18} color="#B23B4E" />
              <Text style={styles.erroTxt}>{erro}</Text>
            </View>
          )}

          <Pressable
            style={({ pressed }) => [
              styles.primaryBtn,
              pressed && styles.primaryBtnPressed,
              !podeSalvar && styles.primaryBtnOff,
            ]}
            onPress={salvar}
            disabled={!podeSalvar}
            accessibilityRole="button"
            accessibilityLabel="Salvar senha"
            accessibilityState={{ busy: salvando, disabled: !podeSalvar }}>
            {salvando ? <ActivityIndicator color="#fff" /> : <Text style={styles.primaryBtnText}>Salvar senha</Text>}
          </Pressable>

          <View style={styles.foot}>
            <Ionicons name="shield-checkmark-outline" size={18} color={t.muted} />
            <Text style={styles.footText}>
              Sua senha fica só neste aparelho para entrar mais rápido. Você pode alterá-la depois em Meu perfil.
            </Text>
          </View>

          <Pressable
            style={[styles.sairBtn, { marginBottom: insets.bottom + 4 }]}
            onPress={sairDaConta}
            disabled={salvando}
            accessibilityRole="button"
            accessibilityLabel="Sair da conta">
            <Text style={styles.sairTxt}>Sair da conta</Text>
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    root: { flex: 1, backgroundColor: t.brandDeep },
    brand: { paddingHorizontal: 28, paddingTop: 8, paddingBottom: 28 },
    mark: {
      width: 44,
      height: 44,
      borderRadius: 14,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: 'rgba(255,255,255,0.10)',
      borderWidth: 1,
      borderColor: alpha(t.onBrand, 0.28),
      marginBottom: 16,
    },
    titulo: { color: t.onBrand, fontSize: 26, fontWeight: '700', letterSpacing: -0.3 },
    subtitulo: { color: alpha(t.onBrand, 0.72), fontSize: 14.5, marginTop: 6, lineHeight: 20 },
    sheetWrap: { flex: 1 },
    sheet: {
      flex: 1,
      backgroundColor: t.surface,
      borderTopLeftRadius: 28,
      borderTopRightRadius: 28,
      marginTop: -8,
    },
    sheetContent: { paddingHorizontal: 28, paddingTop: 30, paddingBottom: 24 },
    label: { fontSize: 13, fontWeight: '600', color: t.ink, marginBottom: 8 },
    labelEspaco: { marginTop: 12 },
    inputWrap: {
      flexDirection: 'row',
      alignItems: 'center',
      height: 56,
      backgroundColor: '#F7FAF9',
      borderWidth: 1,
      borderColor: t.line,
      borderRadius: 14,
      paddingHorizontal: 14,
      marginBottom: 10,
    },
    inputIcon: { marginRight: 10 },
    input: { flex: 1, fontSize: 17, color: t.ink, height: '100%' },
    codeInput: { fontSize: 24, fontWeight: '700', letterSpacing: 8 },
    hint: { fontSize: 13, color: t.muted, marginBottom: 22, marginTop: 2 },
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
    erroTxt: { flex: 1, fontSize: 14, color: '#8A2B3A', lineHeight: 19 },
    primaryBtn: {
      height: 56,
      borderRadius: 15,
      backgroundColor: t.brand,
      alignItems: 'center',
      justifyContent: 'center',
      shadowColor: t.brandDeep,
      shadowOpacity: 0.35,
      shadowRadius: 18,
      shadowOffset: { width: 0, height: 10 },
      elevation: 4,
    },
    primaryBtnPressed: { backgroundColor: t.brandDeep },
    primaryBtnOff: { opacity: 0.45, shadowOpacity: 0, elevation: 0 },
    primaryBtnText: { color: '#fff', fontSize: 17, fontWeight: '700' },
    foot: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      marginTop: 24,
      paddingHorizontal: 4,
    },
    footText: { flex: 1, fontSize: 13, color: t.muted, lineHeight: 18 },
    sairBtn: { alignItems: 'center', paddingVertical: 16, marginTop: 12 },
    sairTxt: { fontSize: 15, fontWeight: '700', color: '#B23B4E' },
  });
