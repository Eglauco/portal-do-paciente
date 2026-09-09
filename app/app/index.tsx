import { Ionicons } from '@expo/vector-icons';
import { StatusBar } from 'expo-status-bar';
import { useMemo, useState } from 'react';
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

import { alpha, type Tema, useTema } from '@/hooks/use-tema';
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
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const { solicitarCodigo, ativar } = useSessao();

  const [etapa, setEtapa] = useState<'telefone' | 'codigo'>('telefone');
  const [telefone, setTelefone] = useState('');
  const [codigo, setCodigo] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [entrando, setEntrando] = useState(false);

  const telefoneValido = telefone.replace(/\D/g, '').length >= 10;
  const podeEntrar = codigo.length === 6 && !entrando;

  async function pedirCodigo() {
    if (enviando || !telefoneValido) return;
    setErro(null);
    setEnviando(true);
    try {
      await solicitarCodigo(telefone);
      setCodigo('');
      setEtapa('codigo');
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível enviar o código. Tente novamente.');
    } finally {
      setEnviando(false);
    }
  }

  async function entrar() {
    if (entrando || codigo.length !== 6) return;
    setErro(null);
    setEntrando(true);
    try {
      await ativar(telefone, codigo);
      // A navegação para "Selecionar Perfil" é feita pelo Navegacao (perfilSelecionado=false).
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível entrar. Tente novamente.');
      setEntrando(false);
    }
  }

  function voltarParaTelefone() {
    setEtapa('telefone');
    setCodigo('');
    setErro(null);
  }

  return (
    <View style={styles.root}>
      <StatusBar style="light" />

      {/* Faixa de marca */}
      <SafeAreaView edges={['top']} style={styles.brand}>
        <Ionicons name="pulse" size={220} color={alpha(t.glow, 0.1)} style={styles.brandWatermark} />

        <View style={styles.wordmark}>
          <View style={styles.mark}>
            <Ionicons name="pulse" size={22} color={t.glow} />
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
          {etapa === 'telefone' ? (
            <>
              <Text style={styles.title}>Entrar no aplicativo</Text>
              <Text style={styles.subtitle}>
                Informe o telefone do seu cadastro. Enviaremos um código de acesso por SMS para confirmar que é você.
              </Text>

              {/* Telefone */}
              <Text style={styles.label}>Telefone</Text>
              <View style={styles.inputWrap}>
                <Ionicons name="call-outline" size={20} color={t.muted} style={styles.inputIcon} />
                <TextInput
                  style={styles.input}
                  value={telefone}
                  onChangeText={(valor) => {
                    setTelefone(mascararTelefone(valor));
                    if (erro) setErro(null);
                  }}
                  placeholder="(11) 99999-0000"
                  placeholderTextColor="#9AAAA5"
                  keyboardType="phone-pad"
                  autoComplete="tel"
                  inputMode="tel"
                  maxLength={15}
                  returnKeyType="done"
                  onSubmitEditing={() => {
                    if (telefoneValido) pedirCodigo();
                  }}
                />
              </View>

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
                  (!telefoneValido || enviando) && styles.primaryBtnOff,
                ]}
                onPress={pedirCodigo}
                disabled={!telefoneValido || enviando}
                accessibilityRole="button"
                accessibilityLabel="Receber código"
                accessibilityState={{ busy: enviando, disabled: !telefoneValido || enviando }}>
                {enviando ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <Text style={styles.primaryBtnText}>Receber código</Text>
                )}
              </Pressable>

              <View style={styles.foot}>
                <Ionicons name="information-circle-outline" size={18} color={t.muted} />
                <Text style={styles.footText}>
                  Telefone não cadastrado? Procure a recepção da sua unidade de saúde.
                </Text>
              </View>
            </>
          ) : (
            <>
              <Pressable style={styles.backRow} onPress={voltarParaTelefone} accessibilityRole="button">
                <Ionicons name="chevron-back" size={20} color={t.brandDeep} />
                <Text style={styles.backTxt}>Trocar telefone</Text>
              </Pressable>

              <Text style={styles.title}>Digite o código</Text>
              <Text style={styles.subtitle}>
                Enviamos um código por SMS para {telefone}.
              </Text>

              <Text style={styles.label}>Código de acesso</Text>
              <View style={styles.inputWrap}>
                <Ionicons name="key-outline" size={20} color={t.muted} style={styles.inputIcon} />
                <TextInput
                  style={[styles.input, styles.codeInput]}
                  value={codigo}
                  onChangeText={(valor) => {
                    setCodigo(valor.replace(/\D/g, '').slice(0, 6));
                    if (erro) setErro(null);
                  }}
                  placeholder="000000"
                  placeholderTextColor="#9AAAA5"
                  keyboardType="number-pad"
                  inputMode="numeric"
                  maxLength={6}
                  autoFocus
                  returnKeyType="done"
                  onSubmitEditing={() => {
                    if (podeEntrar) entrar();
                  }}
                />
              </View>
              <Text style={styles.hint}>São 6 números e o código expira em alguns minutos.</Text>

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
                  !podeEntrar && styles.primaryBtnOff,
                ]}
                onPress={entrar}
                disabled={!podeEntrar}
                accessibilityRole="button"
                accessibilityLabel="Entrar"
                accessibilityState={{ busy: entrando, disabled: !podeEntrar }}>
                {entrando ? <ActivityIndicator color="#fff" /> : <Text style={styles.primaryBtnText}>Entrar</Text>}
              </Pressable>

              <Pressable
                style={styles.linkBtn}
                onPress={pedirCodigo}
                disabled={enviando}
                accessibilityRole="button">
                <Text style={styles.linkTxt}>
                  {enviando ? 'Reenviando…' : 'Reenviar código por SMS'}
                </Text>
              </Pressable>
            </>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: t.brandDeep,
  },
  brand: {
    paddingHorizontal: 28,
    paddingBottom: 34,
    overflow: 'hidden',
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
    borderColor: alpha(t.onBrand, 0.28),
  },
  wordmarkName: {
    color: t.onBrand,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1.6,
  },
  wordmarkSub: {
    color: alpha(t.onBrand, 0.62),
    fontSize: 11,
    letterSpacing: 2,
    marginTop: 2,
  },
  pitch: {
    marginTop: 34,
  },
  headline: {
    color: t.onBrand,
    fontSize: 30,
    lineHeight: 34,
    fontWeight: '600',
    letterSpacing: -0.3,
  },
  headlineAccent: {
    color: t.glow,
  },
  sheetWrap: {
    flex: 1,
  },
  sheet: {
    flex: 1,
    backgroundColor: t.surface,
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
    color: t.ink,
    letterSpacing: -0.3,
  },
  subtitle: {
    fontSize: 15,
    color: t.muted,
    marginTop: 6,
    marginBottom: 26,
    lineHeight: 21,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    color: t.ink,
    marginBottom: 8,
  },
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
  inputIcon: {
    marginRight: 10,
  },
  input: {
    flex: 1,
    fontSize: 17,
    color: t.ink,
    height: '100%',
  },
  codeInput: {
    fontSize: 24,
    fontWeight: '700',
    letterSpacing: 8,
  },
  hint: {
    fontSize: 13,
    color: t.muted,
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
    backgroundColor: t.brand,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: t.brandDeep,
    shadowOpacity: 0.35,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 10 },
    elevation: 4,
  },
  primaryBtnPressed: {
    backgroundColor: t.brandDeep,
  },
  primaryBtnOff: {
    // Desabilitado = a própria marca esmaecida (segue o tema), em vez de uma cor fixa.
    opacity: 0.45,
    shadowOpacity: 0,
    elevation: 0,
  },
  primaryBtnText: {
    color: '#fff',
    fontSize: 17,
    fontWeight: '700',
  },
  linkBtn: {
    alignItems: 'center',
    paddingVertical: 16,
    marginTop: 4,
  },
  linkTxt: {
    fontSize: 15,
    fontWeight: '600',
    color: t.brandDeep,
  },
  backRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 2,
    marginBottom: 18,
    marginLeft: -4,
  },
  backTxt: {
    fontSize: 15,
    fontWeight: '600',
    color: t.brandDeep,
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
    color: t.muted,
    lineHeight: 19,
  },
});
