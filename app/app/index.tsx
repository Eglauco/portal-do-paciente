import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useMemo, useState } from 'react';
import {
  AccessibilityInfo,
  ActivityIndicator,
  BackHandler,
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
import { buscarMarca, marcaEmCache, type Marca } from '@/services/marca';
import { type ClinicaLogin } from '@/services/sessao';

/** Máscara de CPF "000.000.000-00" enquanto o paciente digita. */
function mascararCpf(valor: string): string {
  const d = valor.replace(/\D/g, '').slice(0, 11);
  if (d.length <= 3) return d;
  if (d.length <= 6) return `${d.slice(0, 3)}.${d.slice(3)}`;
  if (d.length <= 9) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`;
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
}

/** Máscara de data "00/00/0000" (dd/mm/aaaa) enquanto o paciente digita. */
function mascararData(valor: string): string {
  const d = valor.replace(/\D/g, '').slice(0, 8);
  if (d.length <= 2) return d;
  if (d.length <= 4) return `${d.slice(0, 2)}/${d.slice(2)}`;
  return `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`;
}

/** Máscara progressiva de telefone rumo a "(XX) XXXXX-XXXX" enquanto o paciente digita. */
function mascararTelefone(valor: string): string {
  const d = valor.replace(/\D/g, '').slice(0, 11);
  if (d.length === 0) return '';
  if (d.length <= 2) return `(${d}`;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

/** Converte a data digitada "dd/mm/aaaa" para o ISO "AAAA-MM-DD" esperado pelo backend. */
function dataParaIso(valor: string): string {
  const d = valor.replace(/\D/g, '');
  if (d.length !== 8) return '';
  return `${d.slice(4, 8)}-${d.slice(2, 4)}-${d.slice(0, 2)}`;
}

/**
 * Etapas do login (CPF-first): digita-se SÓ o CPF; a pré-checagem roteia a próxima tela.
 * - `cpf`: só o CPF → "Continuar" chama a pré-checagem (tem senha central?) e roteia. É a tela inicial.
 * - `senha`: a pré-checagem achou senha → digita só o PIN (login sem SMS). "Esqueci" leva ao SMS.
 * - `clinica`: app COMPARTILHADO (sem inquilino assado) e o CPF está em mais de uma clínica → escolhe qual.
 * - `identidade`: a pré-checagem NÃO achou senha (primeiro acesso) ou veio do "esqueci" → telefone +
 *   CPF + nascimento → envia o código por SMS. (A pré-checagem é clínico-agnóstica: só diz se há PIN na
 *   plataforma, não se o CPF é paciente desta clínica — a trava real fica no login/SMS; anti-enumeração.)
 * - `codigo`: digita o código do SMS → ativa (e depois define a senha).
 */
type Etapa = 'cpf' | 'senha' | 'clinica' | 'identidade' | 'codigo';

export default function LoginScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const insets = useSafeAreaInsets();
  const { preChecagem, loginPorSenha, solicitarCodigo, ativar } = useSessao();

  const [etapa, setEtapa] = useState<Etapa>('cpf');
  const [telefone, setTelefone] = useState('');
  const [cpf, setCpf] = useState('');
  const [dataNascimento, setDataNascimento] = useState('');
  const [telefoneMascarado, setTelefoneMascarado] = useState('');
  const [senha, setSenha] = useState('');
  const [mostrarSenha, setMostrarSenha] = useState(false);
  const [codigo, setCodigo] = useState('');
  const [erro, setErro] = useState<string | null>(null);
  const [checando, setChecando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [entrandoSenha, setEntrandoSenha] = useState(false);
  const [entrando, setEntrando] = useState(false);
  /** Marca (nome + logo) da clínica para o wordmark do login (white-label). */
  const [marca, setMarca] = useState<Marca | null>(null);
  /** App compartilhado: clínicas do CPF quando há mais de uma (etapa `clinica`). */
  const [clinicas, setClinicas] = useState<ClinicaLogin[]>([]);
  /** Clínica escolhida no seletor (usada pelo ativar/OTP) e de onde o seletor foi aberto. */
  const [inquilinoEscolhido, setInquilinoEscolhido] = useState<number | null>(null);
  const [clinicaOrigem, setClinicaOrigem] = useState<'senha' | 'otp'>('senha');

  const cpfValido = cpf.replace(/\D/g, '').length === 11;
  const dataValida = dataNascimento.replace(/\D/g, '').length === 8;
  const telefoneValido = [10, 11].includes(telefone.replace(/\D/g, '').length);
  const podeContinuar = cpfValido && !checando;
  const podeEnviarCodigo = cpfValido && dataValida && telefoneValido && !enviando;
  const podeEntrarSenha = cpfValido && senha.length === 6 && !entrandoSenha;
  const podeEntrar = codigo.length === 6 && !entrando;
  /** Algum pedido em voo: trava os controles de navegação para não cair numa etapa errada. */
  const ocupado = checando || enviando || entrando || entrandoSenha;

  /** Pede o código por SMS (identidade). Devolve o resultado (telefone mascarado + clínicas que conferem). */
  async function pedirCodigoSms() {
    const r = await solicitarCodigo(cpf, dataParaIso(dataNascimento), telefone);
    setTelefoneMascarado(r.telefoneMascarado);
    setCodigo('');
    return r;
  }

  /** Passo 1 (só CPF): pré-checa se o CPF tem senha central e roteia — tela de senha OU primeiro acesso. */
  async function continuar() {
    if (checando || !cpfValido) return;
    setErro(null);
    setChecando(true);
    try {
      const { temSenha } = await preChecagem(cpf);
      setSenha('');
      setMostrarSenha(false);
      setEtapa(temSenha ? 'senha' : 'identidade');
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível continuar. Tente novamente.');
    } finally {
      setChecando(false);
    }
  }

  /** Volta ao passo do CPF, limpando dados sensíveis (mantém CPF/telefone já digitados). */
  function voltarParaCpf() {
    setEtapa('cpf');
    setErro(null);
    setCodigo('');
    setSenha('');
    setMostrarSenha(false);
  }

  /** Primeiro acesso / recuperação: valida identidade, envia o SMS e roteia (1 clínica → código; N → seletor). */
  async function enviarCodigo() {
    if (enviando || !cpfValido || !dataValida || !telefoneValido) return;
    setErro(null);
    setEnviando(true);
    try {
      const r = await pedirCodigoSms();
      if (r.clinicas.length > 1) {
        // App compartilhado, identidade confere em mais de uma clínica: escolher ANTES de digitar o código.
        setClinicas(r.clinicas);
        setClinicaOrigem('otp');
        setInquilinoEscolhido(null);
        setEtapa('clinica');
      } else {
        setInquilinoEscolhido(r.clinicas[0]?.inquilinoId ?? null);
        setEtapa('codigo');
      }
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível enviar o código. Tente novamente.');
    } finally {
      setEnviando(false);
    }
  }

  /** Entra com CPF + senha (sem SMS). App compartilhado com CPF em mais de uma clínica → abre o seletor. */
  async function entrarComSenha() {
    if (entrandoSenha || !cpfValido || senha.length !== 6) return;
    setErro(null);
    setEntrandoSenha(true);
    try {
      const resultado = await loginPorSenha(cpf, senha);
      if ('clinicas' in resultado) {
        setClinicas(resultado.clinicas);
        setClinicaOrigem('senha');
        setEtapa('clinica');
        setEntrandoSenha(false);
        return;
      }
      // Sessão criada → o Navegacao leva para "Selecionar Perfil" (perfilSelecionado=false).
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível entrar. Tente novamente.');
      setEntrandoSenha(false);
    }
  }

  /** Escolhe a clínica (fluxo PIN) → re-chama o login com o inquilino escolhido. */
  async function escolherClinica(inquilinoId: number) {
    if (entrandoSenha) return;
    setErro(null);
    setEntrandoSenha(true);
    try {
      await loginPorSenha(cpf, senha, inquilinoId);
      // Sessão criada → o Navegacao leva para "Selecionar Perfil".
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível entrar. Tente novamente.');
      setEntrandoSenha(false);
    }
  }

  /** Pick no seletor de clínica: no fluxo PIN faz o login; no fluxo OTP fixa a clínica e vai para o código. */
  function onEscolherClinica(inquilinoId: number) {
    if (clinicaOrigem === 'otp') {
      setInquilinoEscolhido(inquilinoId);
      setErro(null);
      setEtapa('codigo');
    } else {
      escolherClinica(inquilinoId);
    }
  }

  /** Da tela de senha ("esqueci a senha") → fluxo por SMS (o código redefine a senha). */
  function irParaPrimeiroAcesso() {
    setSenha('');
    setMostrarSenha(false);
    setErro(null);
    setEtapa('identidade');
  }

  /** Reenvia o código na etapa "codigo". */
  async function reenviarCodigo() {
    if (enviando) return;
    setErro(null);
    setEnviando(true);
    try {
      await pedirCodigoSms();
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível reenviar o código. Tente novamente.');
    } finally {
      setEnviando(false);
    }
  }

  async function entrar() {
    if (entrando || codigo.length !== 6) return;
    setErro(null);
    setEntrando(true);
    try {
      await ativar(cpf, dataParaIso(dataNascimento), codigo, telefone, inquilinoEscolhido ?? undefined);
      // A navegação para "Definir senha" / "Selecionar Perfil" é feita pelo Navegacao.
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível entrar. Tente novamente.');
      setEntrando(false);
    }
  }

  /** Da etapa "codigo" volta para o Primeiro acesso (onde ficam telefone/CPF/nascimento). */
  function voltarParaIdentidade() {
    setEtapa('identidade');
    setCodigo('');
    setErro(null);
  }

  // Android: o botão/gesto físico de "voltar" acompanha a máquina de estados (volta à tela inicial
  // ou à etapa anterior) em vez de fechar o app a partir de uma sub-tela. Enquanto há pedido em voo,
  // não navega (igual aos botões de "Voltar" na tela, que também ficam travados).
  useEffect(() => {
    if (Platform.OS !== 'android') return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (etapa === 'cpf') return false; // deixa o sistema tratar (fecha/background)
      if (ocupado) return true; // ignora enquanto envia/entra, sem sair da etapa atual
      if (etapa === 'codigo') voltarParaIdentidade();
      else if (etapa === 'clinica') {
        setErro(null);
        setEtapa(clinicaOrigem === 'otp' ? 'identidade' : 'senha'); // volta pra identidade (OTP) ou PIN
      } else voltarParaCpf(); // senha ou identidade → volta ao CPF
      return true;
    });
    return () => sub.remove();
  }, [etapa, ocupado, clinicaOrigem]);

  // Acessibilidade: anuncia a mensagem de erro assim que ela aparece (leitor de tela).
  useEffect(() => {
    if (erro) AccessibilityInfo.announceForAccessibility(erro);
  }, [erro]);

  // Marca (nome + logo) da clínica para o wordmark: cache instantâneo + busca fresca (/marca com X-Inquilino).
  useEffect(() => {
    let vivo = true;
    marcaEmCache().then((m) => {
      if (vivo && m) setMarca(m);
    });
    buscarMarca().then((m) => {
      if (vivo && m) setMarca(m);
    });
    return () => {
      vivo = false;
    };
  }, []);

  return (
    <View style={styles.root}>
      <StatusBar style="light" />

      {/* Faixa de marca */}
      <SafeAreaView edges={['top']} style={styles.brand}>
        <Ionicons name="pulse" size={220} color={alpha(t.glow, 0.1)} style={styles.brandWatermark} />

        <View style={styles.wordmark}>
          <View style={styles.mark}>
            {marca?.logoUrl ? (
              <Image
                source={marca.logoUrl}
                style={styles.markLogo}
                contentFit="contain"
                transition={150}
                accessibilityLabel="Logomarca"
              />
            ) : (
              <Ionicons name="pulse" size={22} color={t.glow} />
            )}
          </View>
          <View style={styles.wordmarkTexts}>
            <Text style={styles.wordmarkName} numberOfLines={2}>
              {marca?.nomePlataforma ?? 'Portal do Paciente'}
            </Text>
            <Text style={styles.wordmarkSub}>Cuidado conectado</Text>
          </View>
        </View>

        <View style={styles.pitch}>
          <Text style={styles.headline}>Seu cuidado,</Text>
          <Text style={[styles.headline, styles.headlineAccent]}>sempre por perto.</Text>
        </View>
      </SafeAreaView>

      {/* Folha do formulário */}
      {/*
        Sob edge-to-edge (padrão do Expo SDK 54) o teclado só sobrepõe a tela no Android —
        não a redimensiona (adjustResize não vale), então CPF e Data de nascimento ficavam
        atrás do teclado. behavior="height" encolhe o viewport da folha até o topo do teclado,
        e o RN rola o campo em foco para dentro da área visível. A faixa de marca fica acima da
        KAV (irmã), então a hero continua intacta; keyboardVerticalOffset={0} porque o fundo da
        KAV é o fundo da tela (sem compensação a fazer). Não reservar paddingBottom = altura do
        teclado: a altura da KAV já encolhe e duplicaria o espaço.
      */}
      <KeyboardAvoidingView
        style={styles.sheetWrap}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        keyboardVerticalOffset={0}>
        <ScrollView
          style={styles.sheet}
          contentContainerStyle={[styles.sheetContent, { paddingBottom: insets.bottom + 24 }]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}>
          {etapa === 'cpf' ? (
            <>
              <Text style={styles.title}>Bem-vindo(a)</Text>
              <Text style={styles.subtitle}>Digite seu CPF para entrar.</Text>

              {/* CPF */}
              <Text style={styles.label}>CPF</Text>
              <View style={styles.inputWrap}>
                <Ionicons name="card-outline" size={20} color={t.muted} style={styles.inputIcon} />
                <TextInput
                  style={styles.input}
                  value={cpf}
                  onChangeText={(valor) => {
                    setCpf(mascararCpf(valor));
                    if (erro) setErro(null);
                  }}
                  placeholder="000.000.000-00"
                  placeholderTextColor="#9AAAA5"
                  keyboardType="number-pad"
                  inputMode="numeric"
                  maxLength={14}
                  autoFocus
                  returnKeyType="done"
                  onSubmitEditing={() => {
                    if (podeContinuar) continuar();
                  }}
                />
              </View>

              {erro && (
                <View style={styles.erroBox} accessibilityRole="alert" accessibilityLiveRegion="assertive">
                  <Ionicons name="alert-circle" size={18} color="#B23B4E" />
                  <Text style={styles.erroTxt}>{erro}</Text>
                </View>
              )}

              <Pressable
                style={({ pressed }) => [
                  styles.primaryBtn,
                  pressed && styles.primaryBtnPressed,
                  !podeContinuar && styles.primaryBtnOff,
                ]}
                onPress={continuar}
                disabled={!podeContinuar}
                accessibilityRole="button"
                accessibilityLabel="Continuar"
                accessibilityState={{ busy: checando, disabled: !podeContinuar }}>
                {checando ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <Text style={styles.primaryBtnText}>Continuar</Text>
                )}
              </Pressable>

              <View style={styles.foot}>
                <Ionicons name="information-circle-outline" size={18} color={t.muted} />
                <Text style={styles.footText}>
                  CPF não cadastrado? Procure a recepção da sua unidade de saúde.
                </Text>
              </View>
            </>
          ) : etapa === 'senha' ? (
            <>
              <Pressable
                style={[styles.backRow, entrandoSenha && styles.backRowOff]}
                onPress={voltarParaCpf}
                disabled={entrandoSenha}
                accessibilityRole="button">
                <Ionicons name="chevron-back" size={20} color={t.brandDeep} />
                <Text style={styles.backTxt}>Trocar CPF</Text>
              </Pressable>

              <Text style={styles.title}>Digite sua senha</Text>
              <Text style={styles.subtitle}>
                CPF {cpf} — entre com a sua senha de 6 dígitos, a mesma de sempre.
              </Text>

              {/* Senha (PIN) */}
              <Text style={styles.label}>Senha</Text>
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
                  secureTextEntry={!mostrarSenha}
                  autoFocus
                  returnKeyType="done"
                  onSubmitEditing={() => {
                    if (podeEntrarSenha) entrarComSenha();
                  }}
                />
                <Pressable
                  onPress={() => setMostrarSenha((v) => !v)}
                  hitSlop={10}
                  accessibilityRole="button"
                  accessibilityLabel={mostrarSenha ? 'Ocultar senha' : 'Mostrar senha'}>
                  <Ionicons name={mostrarSenha ? 'eye-off-outline' : 'eye-outline'} size={20} color={t.muted} />
                </Pressable>
              </View>
              <Text style={styles.hint}>São 6 números.</Text>

              {erro && (
                <View style={styles.erroBox} accessibilityRole="alert" accessibilityLiveRegion="assertive">
                  <Ionicons name="alert-circle" size={18} color="#B23B4E" />
                  <Text style={styles.erroTxt}>{erro}</Text>
                </View>
              )}

              <Pressable
                style={({ pressed }) => [
                  styles.primaryBtn,
                  pressed && styles.primaryBtnPressed,
                  !podeEntrarSenha && styles.primaryBtnOff,
                ]}
                onPress={entrarComSenha}
                disabled={!podeEntrarSenha}
                accessibilityRole="button"
                accessibilityLabel="Entrar"
                accessibilityState={{ busy: entrandoSenha, disabled: !podeEntrarSenha }}>
                {entrandoSenha ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <Text style={styles.primaryBtnText}>Entrar</Text>
                )}
              </Pressable>

              <Pressable
                style={styles.linkBtn}
                onPress={irParaPrimeiroAcesso}
                disabled={entrandoSenha}
                accessibilityRole="button"
                accessibilityLabel="Esqueci minha senha. Entrar por SMS.">
                <Text style={styles.linkTxt}>Esqueci minha senha</Text>
              </Pressable>
            </>
          ) : etapa === 'clinica' ? (
            <>
              <Pressable
                style={[styles.backRow, entrandoSenha && styles.backRowOff]}
                onPress={() => {
                  setErro(null);
                  setEtapa(clinicaOrigem === 'otp' ? 'identidade' : 'senha');
                }}
                disabled={entrandoSenha}
                accessibilityRole="button">
                <Ionicons name="chevron-back" size={20} color={t.brandDeep} />
                <Text style={styles.backTxt}>Voltar</Text>
              </Pressable>

              <Text style={styles.title}>Selecionar clínica</Text>
              <Text style={styles.subtitle}>
                Seu CPF tem cadastro em mais de uma unidade. Escolha por qual você quer entrar.
              </Text>

              {erro && (
                <View style={styles.erroBox} accessibilityRole="alert" accessibilityLiveRegion="assertive">
                  <Ionicons name="alert-circle" size={18} color="#B23B4E" />
                  <Text style={styles.erroTxt}>{erro}</Text>
                </View>
              )}

              {clinicas.map((c) => (
                <Pressable
                  key={c.inquilinoId}
                  style={({ pressed }) => [styles.clinicaItem, pressed && styles.clinicaItemPressed]}
                  onPress={() => onEscolherClinica(c.inquilinoId)}
                  disabled={entrandoSenha}
                  accessibilityRole="button"
                  accessibilityLabel={`Entrar na ${c.nome}`}>
                  <View style={styles.clinicaIcon}>
                    <Ionicons name="business-outline" size={20} color={t.brandDeep} />
                  </View>
                  <Text style={styles.clinicaNome} numberOfLines={2}>
                    {c.nome}
                  </Text>
                  {entrandoSenha ? (
                    <ActivityIndicator color={t.brandDeep} />
                  ) : (
                    <Ionicons name="chevron-forward" size={20} color={t.muted} />
                  )}
                </Pressable>
              ))}
            </>
          ) : etapa === 'identidade' ? (
            <>
              <Pressable
                style={[styles.backRow, enviando && styles.backRowOff]}
                onPress={voltarParaCpf}
                disabled={enviando}
                accessibilityRole="button">
                <Ionicons name="chevron-back" size={20} color={t.brandDeep} />
                <Text style={styles.backTxt}>Voltar</Text>
              </Pressable>

              <Text style={styles.title}>Primeiro acesso</Text>
              <Text style={styles.subtitle}>
                Confirme seu telefone, CPF e data de nascimento. Enviaremos um código por SMS para
                ativar o seu acesso ou criar uma nova senha.
              </Text>

              {/* Telefone (1ª trava de identidade) */}
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
                  placeholder="(00) 00000-0000"
                  placeholderTextColor="#9AAAA5"
                  keyboardType="phone-pad"
                  maxLength={15}
                  returnKeyType="next"
                />
              </View>

              {/* CPF (2ª trava de identidade) */}
              <Text style={[styles.label, styles.labelEspaco]}>CPF</Text>
              <View style={styles.inputWrap}>
                <Ionicons name="card-outline" size={20} color={t.muted} style={styles.inputIcon} />
                <TextInput
                  style={styles.input}
                  value={cpf}
                  onChangeText={(valor) => {
                    setCpf(mascararCpf(valor));
                    if (erro) setErro(null);
                  }}
                  placeholder="000.000.000-00"
                  placeholderTextColor="#9AAAA5"
                  keyboardType="number-pad"
                  inputMode="numeric"
                  maxLength={14}
                  returnKeyType="next"
                />
              </View>

              {/* Data de nascimento (3ª trava de identidade) */}
              <Text style={[styles.label, styles.labelEspaco]}>Data de nascimento</Text>
              <View style={styles.inputWrap}>
                <Ionicons name="calendar-outline" size={20} color={t.muted} style={styles.inputIcon} />
                <TextInput
                  style={styles.input}
                  value={dataNascimento}
                  onChangeText={(valor) => {
                    setDataNascimento(mascararData(valor));
                    if (erro) setErro(null);
                  }}
                  placeholder="00/00/0000"
                  placeholderTextColor="#9AAAA5"
                  keyboardType="number-pad"
                  inputMode="numeric"
                  maxLength={10}
                  returnKeyType="done"
                  onSubmitEditing={() => {
                    if (podeEnviarCodigo) enviarCodigo();
                  }}
                />
              </View>

              {erro && (
                <View style={styles.erroBox} accessibilityRole="alert" accessibilityLiveRegion="assertive">
                  <Ionicons name="alert-circle" size={18} color="#B23B4E" />
                  <Text style={styles.erroTxt}>{erro}</Text>
                </View>
              )}

              <Pressable
                style={({ pressed }) => [
                  styles.primaryBtn,
                  pressed && styles.primaryBtnPressed,
                  !podeEnviarCodigo && styles.primaryBtnOff,
                ]}
                onPress={enviarCodigo}
                disabled={!podeEnviarCodigo}
                accessibilityRole="button"
                accessibilityLabel="Enviar código por SMS"
                accessibilityState={{ busy: enviando, disabled: !podeEnviarCodigo }}>
                {enviando ? (
                  <ActivityIndicator color="#fff" />
                ) : (
                  <Text style={styles.primaryBtnText}>Enviar código</Text>
                )}
              </Pressable>

              <View style={styles.foot}>
                <Ionicons name="information-circle-outline" size={18} color={t.muted} />
                <Text style={styles.footText}>
                  CPF não cadastrado? Procure a recepção da sua unidade de saúde.
                </Text>
              </View>
            </>
          ) : (
            <>
              <Pressable
                style={[styles.backRow, (enviando || entrando) && styles.backRowOff]}
                onPress={voltarParaIdentidade}
                disabled={enviando || entrando}
                accessibilityRole="button">
                <Ionicons name="chevron-back" size={20} color={t.brandDeep} />
                <Text style={styles.backTxt}>Trocar dados</Text>
              </Pressable>

              <Text style={styles.title}>Digite o código</Text>
              <Text style={styles.subtitle}>
                Enviamos um código por SMS para {telefoneMascarado || 'o telefone do seu cadastro'}.
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
                <View style={styles.erroBox} accessibilityRole="alert" accessibilityLiveRegion="assertive">
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
                onPress={reenviarCodigo}
                disabled={enviando || entrando}
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
    overflow: 'hidden',
  },
  markLogo: {
    width: 30,
    height: 30,
  },
  wordmarkTexts: {
    flex: 1,
  },
  wordmarkName: {
    color: t.onBrand,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1.6,
    textTransform: 'uppercase',
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
  labelEspaco: {
    marginTop: 6,
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
  backRowOff: {
    opacity: 0.4,
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
  // Seletor de clínica (app compartilhado com CPF em mais de uma unidade).
  clinicaItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    backgroundColor: t.surface,
    borderWidth: 1.5,
    borderColor: t.line,
    borderRadius: 16,
    paddingVertical: 16,
    paddingHorizontal: 16,
    marginBottom: 12,
  },
  clinicaItemPressed: {
    backgroundColor: '#F2F7F6',
  },
  clinicaIcon: {
    width: 42,
    height: 42,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: alpha(t.brand, 0.1),
  },
  clinicaNome: {
    flex: 1,
    fontSize: 16,
    fontWeight: '600',
    color: t.ink,
  },
});
