import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
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

import { ScreenHeader } from '@/components/screen-header';
import { type Tema, useTema } from '@/hooks/use-tema';
import { alterarSenha } from '@/services/sessao';

/**
 * Troca do PIN de acesso da conta (Meu perfil). É credencial da CONTA (não muda por perfil):
 * exige a senha atual + a nova (6 dígitos, confirmada). Ao salvar, avisa e volta.
 */
export default function AlterarSenhaScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const router = useRouter();

  const [atual, setAtual] = useState('');
  const [nova, setNova] = useState('');
  const [confirmar, setConfirmar] = useState('');
  const [mostrar, setMostrar] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [salvando, setSalvando] = useState(false);

  const atualOk = atual.length === 6;
  const novaOk = nova.length === 6;
  const confereOk = confirmar.length === 6 && confirmar === nova;
  const podeSalvar = atualOk && novaOk && confereOk && !salvando;

  async function salvar() {
    if (!atualOk) {
      setErro('Informe sua senha atual (6 dígitos).');
      return;
    }
    if (!novaOk) {
      setErro('A nova senha deve ter 6 dígitos numéricos.');
      return;
    }
    if (!confereOk) {
      setErro('A confirmação não é igual à nova senha.');
      return;
    }
    setErro(null);
    setSalvando(true);
    try {
      await alterarSenha(atual, nova);
      Alert.alert('Senha alterada', 'Sua senha foi atualizada.', [
        { text: 'OK', onPress: () => router.back() },
      ]);
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível alterar a senha. Tente novamente.');
      setSalvando(false);
    }
  }

  function campo(
    rotulo: string,
    valor: string,
    setar: (v: string) => void,
    autoFocus: boolean,
    ultimo = false,
  ) {
    return (
      <>
        <Text style={styles.label}>{rotulo}</Text>
        <View style={styles.inputWrap}>
          <Ionicons name="lock-closed-outline" size={20} color={t.muted} style={styles.inputIcon} />
          <TextInput
            style={[styles.input, styles.codeInput]}
            value={valor}
            onChangeText={(v) => {
              setar(v.replace(/\D/g, '').slice(0, 6));
              if (erro) setErro(null);
            }}
            placeholder="••••••"
            placeholderTextColor="#9AAAA5"
            keyboardType="number-pad"
            inputMode="numeric"
            maxLength={6}
            secureTextEntry={!mostrar}
            autoFocus={autoFocus}
            returnKeyType={ultimo ? 'done' : 'next'}
            onSubmitEditing={ultimo ? () => (podeSalvar ? salvar() : undefined) : undefined}
          />
          {ultimo && (
            <Pressable
              onPress={() => setMostrar((v) => !v)}
              hitSlop={10}
              accessibilityRole="button"
              accessibilityLabel={mostrar ? 'Ocultar senhas' : 'Mostrar senhas'}>
              <Ionicons name={mostrar ? 'eye-off-outline' : 'eye-outline'} size={20} color={t.muted} />
            </Pressable>
          )}
        </View>
      </>
    );
  }

  return (
    <View style={styles.screen}>
      <ScreenHeader title="Alterar senha" />
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}>
          <View style={styles.aviso}>
            <Ionicons name="information-circle-outline" size={16} color={t.muted} />
            <Text style={styles.avisoTxt}>
              A senha é usada para entrar no app sem SMS. Use 6 números.
            </Text>
          </View>

          {campo('Senha atual', atual, setAtual, true)}
          <View style={styles.espaco} />
          {campo('Nova senha', nova, setNova, false)}
          <View style={styles.espaco} />
          {campo('Repita a nova senha', confirmar, setConfirmar, false, true)}

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
            accessibilityLabel="Salvar nova senha"
            accessibilityState={{ busy: salvando, disabled: !podeSalvar }}>
            {salvando ? <ActivityIndicator color="#fff" /> : <Text style={styles.primaryBtnText}>Salvar nova senha</Text>}
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    screen: { flex: 1, backgroundColor: t.bg },
    flex: { flex: 1 },
    content: { padding: 20, paddingBottom: 40 },
    aviso: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      backgroundColor: t.brandTint,
      borderRadius: 12,
      paddingHorizontal: 12,
      paddingVertical: 10,
      marginBottom: 18,
    },
    avisoTxt: { flex: 1, fontSize: 12.5, color: t.muted, lineHeight: 17 },
    label: { fontSize: 13, fontWeight: '600', color: t.ink, marginBottom: 8 },
    espaco: { height: 14 },
    inputWrap: {
      flexDirection: 'row',
      alignItems: 'center',
      height: 56,
      backgroundColor: t.surface,
      borderWidth: 1,
      borderColor: t.line,
      borderRadius: 14,
      paddingHorizontal: 14,
    },
    inputIcon: { marginRight: 10 },
    input: { flex: 1, fontSize: 17, color: t.ink, height: '100%' },
    codeInput: { fontSize: 22, fontWeight: '700', letterSpacing: 6 },
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
      marginTop: 18,
    },
    erroTxt: { flex: 1, fontSize: 14, color: '#8A2B3A', lineHeight: 19 },
    primaryBtn: {
      height: 56,
      borderRadius: 15,
      backgroundColor: t.brand,
      alignItems: 'center',
      justifyContent: 'center',
      marginTop: 24,
    },
    primaryBtnPressed: { backgroundColor: t.brandDeep },
    primaryBtnOff: { opacity: 0.45 },
    primaryBtnText: { color: '#fff', fontSize: 17, fontWeight: '700' },
  });
