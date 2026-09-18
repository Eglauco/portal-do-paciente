import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  type TextInputProps,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ScreenHeader } from '@/components/screen-header';
import { SemAcesso } from '@/components/sem-acesso';
import { type Tema, useTema } from '@/hooks/use-tema';
import { useSessao } from '@/hooks/use-sessao';
import { atualizarPerfil, carregarPerfil, type MeuPerfil, type PerfilEditavel, type SexoPaciente } from '@/services/perfil';
import { podeLancar } from '@/services/sessao';

/** Vermelho de "remover" (mesmo tom usado nas demais telas). */
const PERIGO = '#B23B4E';

const soDigitos = (v: string): string => v.replace(/\D/g, '');

/** Máscara progressiva de telefone rumo a "(XX) XXXXX-XXXX". */
function fmtTelefone(v: string): string {
  const d = soDigitos(v).slice(0, 11);
  if (d.length === 0) return '';
  if (d.length <= 2) return `(${d}`;
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`;
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
}

/** Máscara de CPF "000.000.000-00" (só exibição — o CPF é travado). */
function fmtCpf(v: string): string {
  const d = soDigitos(v).slice(0, 11);
  if (d.length !== 11) return v;
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
}

/** Máscara progressiva de CNS "000 0000 0000 0000" (15 dígitos). */
function fmtCns(v: string): string {
  const d = soDigitos(v).slice(0, 15);
  const partes = [d.slice(0, 3), d.slice(3, 7), d.slice(7, 11), d.slice(11, 15)].filter((p) => p.length > 0);
  return partes.join(' ');
}

/** Máscara progressiva de CEP "00000-000". */
function fmtCep(v: string): string {
  const d = soDigitos(v).slice(0, 8);
  if (d.length <= 5) return d;
  return `${d.slice(0, 5)}-${d.slice(5)}`;
}

/** Máscara de data "00/00/0000" (dd/mm/aaaa) enquanto o paciente digita. */
function fmtData(v: string): string {
  const d = soDigitos(v).slice(0, 8);
  if (d.length <= 2) return d;
  if (d.length <= 4) return `${d.slice(0, 2)}/${d.slice(2)}`;
  return `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`;
}

/** Converte "ddmmaaaa" (dígitos da máscara) para o ISO "AAAA-MM-DD" do backend. */
function dataParaIso(digitos: string): string {
  const d = soDigitos(digitos);
  if (d.length !== 8) return '';
  return `${d.slice(4, 8)}-${d.slice(2, 4)}-${d.slice(0, 2)}`;
}

/** Converte o ISO "AAAA-MM-DD" (do backend) de volta para os dígitos "ddmmaaaa" da máscara. */
function isoParaDigitos(iso: string | null): string {
  const d = soDigitos(iso ?? ''); // "AAAAMMDD"
  if (d.length !== 8) return '';
  return `${d.slice(6, 8)}${d.slice(4, 6)}${d.slice(0, 4)}`;
}

/** Endereço do CEP (ViaCEP), os campos que usamos. */
interface EnderecoCep {
  logradouro: string;
  bairro: string;
  municipio: string;
  uf: string;
}

/** Consulta o CEP no ViaCEP (API pública, mesmo serviço do back-office). null se não achar/falhar. */
async function buscarCep(cepDigitos: string): Promise<EnderecoCep | null> {
  if (cepDigitos.length !== 8) return null;
  try {
    const resposta = await fetch(`https://viacep.com.br/ws/${cepDigitos}/json/`);
    if (!resposta.ok) return null;
    const r = (await resposta.json()) as {
      logradouro?: string;
      bairro?: string;
      localidade?: string;
      uf?: string;
      erro?: boolean;
    };
    if (r.erro) return null;
    return { logradouro: r.logradouro ?? '', bairro: r.bairro ?? '', municipio: r.localidade ?? '', uf: r.uf ?? '' };
  } catch {
    return null;
  }
}

/** Opções de sexo (os valores casam com o enum do backend). */
const SEXOS: { valor: SexoPaciente; rotulo: string }[] = [
  { valor: 'MASCULINO', rotulo: 'Masculino' },
  { valor: 'FEMININO', rotulo: 'Feminino' },
  { valor: 'OUTRO', rotulo: 'Outro' },
  { valor: 'NAO_INFORMADO', rotulo: 'Não informado' },
];

/** Campo de texto rotulado (definido no escopo do módulo para não perder o foco a cada tecla). */
function CampoTexto({
  styles,
  cor,
  label,
  espaco = true,
  ...input
}: {
  styles: ReturnType<typeof criarEstilos>;
  cor: string;
  label: string;
  espaco?: boolean;
} & TextInputProps) {
  return (
    <>
      <Text style={[styles.label, espaco && styles.labelEspaco]}>{label}</Text>
      <TextInput style={styles.input} placeholderTextColor={cor} {...input} />
    </>
  );
}

export default function PerfilEditarScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { sessao } = useSessao();
  // Perfil próprio SEMPRE pode; responsável só com "Ver e lançar" em MEU_PERFIL.
  const podeEditar = podeLancar(sessao, 'MEU_PERFIL');

  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [buscandoCep, setBuscandoCep] = useState(false);

  // CPF é só exibição (identidade de login): guardado apenas para mostrar travado.
  const [cpf, setCpf] = useState('');
  const [nome, setNome] = useState('');
  const [telefones, setTelefones] = useState<string[]>(['']); // só dígitos
  const [sexo, setSexo] = useState<SexoPaciente | null>(null);
  const [data, setData] = useState(''); // só dígitos "ddmmaaaa"
  const [rg, setRg] = useState('');
  const [cns, setCns] = useState(''); // só dígitos
  const [nomeMae, setNomeMae] = useState('');
  const [nomePai, setNomePai] = useState('');
  const [email, setEmail] = useState('');
  const [rua, setRua] = useState('');
  const [numero, setNumero] = useState('');
  const [complemento, setComplemento] = useState('');
  const [bairro, setBairro] = useState('');
  const [municipio, setMunicipio] = useState('');
  const [uf, setUf] = useState('');
  const [cep, setCep] = useState(''); // só dígitos

  function preencher(p: MeuPerfil) {
    setCpf(soDigitos(p.cpf ?? ''));
    setNome(p.nome ?? '');
    setTelefones(p.telefonesAdicionais.length ? p.telefonesAdicionais.map(soDigitos) : ['']);
    setSexo(p.sexo);
    setData(isoParaDigitos(p.dataNascimento));
    setRg(p.rg ?? '');
    setCns(soDigitos(p.cns ?? ''));
    setNomeMae(p.nomeMae ?? '');
    setNomePai(p.nomePai ?? '');
    setEmail(p.email ?? '');
    setRua(p.rua ?? '');
    setNumero(p.numero ?? '');
    setComplemento(p.complemento ?? '');
    setBairro(p.bairro ?? '');
    setMunicipio(p.municipio ?? '');
    setUf(p.uf ?? '');
    setCep(soDigitos(p.cep ?? ''));
  }

  const carregar = useCallback(async () => {
    try {
      setErro(false);
      setCarregando(true);
      preencher(await carregarPerfil());
    } catch {
      setErro(true);
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    if (!podeEditar) {
      setCarregando(false);
      return;
    }
    carregar();
  }, [carregar, podeEditar]);

  function atualizarTelefone(indice: number, valor: string) {
    setTelefones((atual) => atual.map((tel, i) => (i === indice ? soDigitos(valor).slice(0, 11) : tel)));
  }
  function adicionarTelefone() {
    setTelefones((atual) => [...atual, '']);
  }
  function removerTelefone(indice: number) {
    // Sempre resta ao menos um campo (o backend exige >= 1 telefone).
    setTelefones((atual) => (atual.length <= 1 ? atual : atual.filter((_, i) => i !== indice)));
  }

  /** Digitou o CEP: guarda só os dígitos e, ao completar 8, busca o endereço (igual ao @front). */
  function aoMudarCep(valor: string) {
    const digitos = soDigitos(valor).slice(0, 8);
    setCep(digitos);
    if (digitos.length === 8) {
      void preencherPorCep(digitos);
    }
  }

  /** Preenche rua/bairro (mantém o que já houver se vier vazio) e Município/UF (fonte = CEP). */
  async function preencherPorCep(digitos: string) {
    setBuscandoCep(true);
    const end = await buscarCep(digitos);
    setBuscandoCep(false);
    if (!end) return;
    setRua((prev) => end.logradouro || prev);
    setBairro((prev) => end.bairro || prev);
    setMunicipio((prev) => end.municipio || prev);
    setUf((prev) => end.uf || prev);
  }

  // Telefones preenchidos e válidos (10 ou 11 dígitos). O backend exige pelo menos um.
  const telefonesPreenchidos = telefones.map(soDigitos).filter((d) => d.length > 0);
  const telefonesValidos =
    telefonesPreenchidos.length >= 1 && telefonesPreenchidos.every((d) => d.length === 10 || d.length === 11);
  const dataValida = data.length === 0 || data.length === 8;
  const cnsValido = cns.length === 0 || cns.length === 15;
  const cepValido = cep.length === 0 || cep.length === 8;

  const podeSalvar =
    !carregando &&
    !salvando &&
    nome.trim().length >= 2 &&
    telefonesValidos &&
    dataValida &&
    cnsValido &&
    cepValido;

  async function salvar() {
    if (!podeSalvar) return;
    Keyboard.dismiss();
    try {
      setSalvando(true);
      const dados: PerfilEditavel = {
        nome: nome.trim(),
        telefonesAdicionais: telefonesPreenchidos,
        sexo,
        dataNascimento: data.length === 8 ? dataParaIso(data) : null,
        rg: rg.trim() || null,
        cns: cns.length > 0 ? cns : null,
        nomeMae: nomeMae.trim() || null,
        nomePai: nomePai.trim() || null,
        email: email.trim() || null,
        rua: rua.trim() || null,
        numero: numero.trim() || null,
        complemento: complemento.trim() || null,
        bairro: bairro.trim() || null,
        municipio: municipio.trim() || null,
        uf: uf.trim().toUpperCase() || null,
        cep: cep.length > 0 ? cep : null,
      };
      await atualizarPerfil(dados);
      // Volta ao perfil, que recarrega ao ganhar foco e já mostra os dados atualizados.
      router.back();
    } catch (e) {
      Alert.alert('Não foi possível salvar', e instanceof Error ? e.message : 'Tente novamente.');
    } finally {
      setSalvando(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={styles.screen}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <ScreenHeader title="Editar dados" />

      {!podeEditar ? (
        <SemAcesso mensagem="Você não tem permissão para editar os dados deste perfil." />
      ) : carregando ? (
        <View style={styles.estado}>
          <ActivityIndicator color={t.brand} />
          <Text style={styles.estadoTxt}>Carregando seus dados…</Text>
        </View>
      ) : erro ? (
        <View style={styles.estado}>
          <View style={styles.estadoIcone}>
            <Ionicons name="cloud-offline-outline" size={26} color={t.muted} />
          </View>
          <Text style={styles.estadoTitulo}>Não foi possível carregar</Text>
          <Text style={styles.estadoTxt}>Verifique sua conexão e tente novamente.</Text>
          <Pressable style={styles.estadoBtn} onPress={carregar} accessibilityRole="button">
            <Ionicons name="refresh" size={16} color="#fff" />
            <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
          </Pressable>
        </View>
      ) : (
        <View style={styles.flex}>
          <ScrollView
            contentContainerStyle={styles.content}
            keyboardShouldPersistTaps="handled"
            keyboardDismissMode="on-drag"
            showsVerticalScrollIndicator={false}>
            {/* Dados pessoais */}
            <Text style={styles.secaoTitulo}>Dados pessoais</Text>
            <View style={styles.card}>
              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="Nome completo"
                espaco={false}
                value={nome}
                onChangeText={setNome}
                placeholder="Seu nome completo"
                autoCapitalize="words"
                maxLength={120}
                editable={!salvando}
              />

              <Text style={[styles.label, styles.labelEspaco]}>Sexo</Text>
              <View style={styles.seg}>
                {SEXOS.map((s) => {
                  const sel = sexo === s.valor;
                  return (
                    <Pressable
                      key={s.valor}
                      style={({ pressed }) => [
                        styles.segBtn,
                        sel && styles.segBtnSel,
                        pressed && !sel && styles.segBtnPressed,
                      ]}
                      onPress={() => setSexo(s.valor)}
                      disabled={salvando}
                      accessibilityRole="button"
                      accessibilityState={{ selected: sel, disabled: salvando }}
                      accessibilityLabel={`Sexo: ${s.rotulo}`}>
                      <Text style={[styles.segBtnTxt, sel && styles.segBtnTxtSel]}>{s.rotulo}</Text>
                    </Pressable>
                  );
                })}
              </View>

              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="Data de nascimento"
                value={fmtData(data)}
                onChangeText={(v) => setData(soDigitos(v).slice(0, 8))}
                placeholder="00/00/0000"
                keyboardType="number-pad"
                inputMode="numeric"
                maxLength={10}
                editable={!salvando}
              />
              <View style={styles.nota}>
                <Ionicons name="information-circle-outline" size={13} color={t.muted} />
                <Text style={styles.notaTxt}>Também é usada para entrar no app.</Text>
              </View>

              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="Nome da mãe"
                value={nomeMae}
                onChangeText={setNomeMae}
                placeholder="Nome da mãe"
                autoCapitalize="words"
                maxLength={120}
                editable={!salvando}
              />
              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="Nome do pai"
                value={nomePai}
                onChangeText={setNomePai}
                placeholder="Nome do pai"
                autoCapitalize="words"
                maxLength={120}
                editable={!salvando}
              />
            </View>

            {/* Documentos */}
            <Text style={[styles.secaoTitulo, styles.secaoEspaco]}>Documentos</Text>
            <View style={styles.card}>
              {/* CPF: identidade de login, não pode ser alterado. */}
              <Text style={styles.label}>CPF</Text>
              <View style={styles.inputTravado}>
                <Text style={styles.inputTravadoTxt}>{cpf ? fmtCpf(cpf) : '—'}</Text>
                <Ionicons name="lock-closed" size={15} color={t.muted} />
              </View>
              <Text style={styles.cpfNota}>O CPF não pode ser alterado.</Text>

              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="RG"
                value={rg}
                onChangeText={setRg}
                placeholder="Número do RG"
                maxLength={20}
                editable={!salvando}
              />
              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="Cartão SUS (CNS)"
                value={fmtCns(cns)}
                onChangeText={(v) => setCns(soDigitos(v).slice(0, 15))}
                placeholder="000 0000 0000 0000"
                keyboardType="number-pad"
                inputMode="numeric"
                maxLength={18}
                editable={!salvando}
              />
            </View>

            {/* Contato */}
            <Text style={[styles.secaoTitulo, styles.secaoEspaco]}>Contato</Text>
            <View style={styles.card}>
              <Text style={styles.label}>Telefones</Text>
              {telefones.map((tel, i) => (
                <View key={i} style={[styles.telLinha, i > 0 && styles.telLinhaEspaco]}>
                  <TextInput
                    style={[styles.input, styles.telInput]}
                    value={fmtTelefone(tel)}
                    onChangeText={(v) => atualizarTelefone(i, v)}
                    placeholder="(11) 98888-1111"
                    placeholderTextColor={t.muted}
                    keyboardType="phone-pad"
                    editable={!salvando}
                  />
                  {telefones.length > 1 && (
                    <Pressable
                      style={({ pressed }) => [styles.telRemover, pressed && styles.telRemoverPressed]}
                      onPress={() => removerTelefone(i)}
                      disabled={salvando}
                      hitSlop={6}
                      accessibilityRole="button"
                      accessibilityLabel="Remover telefone">
                      <Ionicons name="trash-outline" size={19} color={PERIGO} />
                    </Pressable>
                  )}
                </View>
              ))}
              <Pressable
                style={({ pressed }) => [styles.addTel, pressed && styles.addTelPressed]}
                onPress={adicionarTelefone}
                disabled={salvando}
                accessibilityRole="button"
                accessibilityLabel="Adicionar telefone">
                <Ionicons name="add-circle-outline" size={18} color={t.brandDeep} />
                <Text style={styles.addTelTxt}>Adicionar telefone</Text>
              </Pressable>
              <Text style={styles.cpfNota}>É preciso manter ao menos um telefone.</Text>

              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="E-mail"
                value={email}
                onChangeText={setEmail}
                placeholder="voce@email.com"
                keyboardType="email-address"
                autoCapitalize="none"
                autoCorrect={false}
                maxLength={120}
                editable={!salvando}
              />
            </View>

            {/* Endereço — o CEP no topo preenche o restante automaticamente (ViaCEP). */}
            <Text style={[styles.secaoTitulo, styles.secaoEspaco]}>Endereço</Text>
            <View style={styles.card}>
              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="CEP"
                espaco={false}
                value={fmtCep(cep)}
                onChangeText={aoMudarCep}
                placeholder="00000-000"
                keyboardType="number-pad"
                inputMode="numeric"
                maxLength={9}
                editable={!salvando}
              />
              <View style={styles.nota}>
                <Ionicons
                  name={buscandoCep ? 'sync-outline' : 'information-circle-outline'}
                  size={13}
                  color={t.muted}
                />
                <Text style={styles.notaTxt}>
                  {buscandoCep ? 'Buscando endereço…' : 'Preenche o endereço automaticamente.'}
                </Text>
              </View>

              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="Rua / Logradouro"
                value={rua}
                onChangeText={setRua}
                placeholder="Nome da rua"
                autoCapitalize="words"
                maxLength={120}
                editable={!salvando}
              />
              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="Número"
                value={numero}
                onChangeText={setNumero}
                placeholder="Número"
                maxLength={20}
                editable={!salvando}
              />
              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="Complemento"
                value={complemento}
                onChangeText={setComplemento}
                placeholder="Apto, bloco, referência"
                maxLength={80}
                editable={!salvando}
              />
              <CampoTexto
                styles={styles}
                cor={t.muted}
                label="Bairro"
                value={bairro}
                onChangeText={setBairro}
                placeholder="Bairro"
                autoCapitalize="words"
                maxLength={80}
                editable={!salvando}
              />

              {/* Município e UF vêm do CEP: bloqueados (não editáveis à mão). */}
              <Text style={[styles.label, styles.labelEspaco]}>Município</Text>
              <View style={styles.inputTravado}>
                <Text style={styles.inputTravadoTxt}>{municipio || '—'}</Text>
                <Ionicons name="lock-closed" size={15} color={t.muted} />
              </View>

              <Text style={[styles.label, styles.labelEspaco]}>UF</Text>
              <View style={styles.inputTravado}>
                <Text style={styles.inputTravadoTxt}>{uf || '—'}</Text>
                <Ionicons name="lock-closed" size={15} color={t.muted} />
              </View>
              <Text style={styles.cpfNota}>Município e UF são preenchidos pelo CEP.</Text>
            </View>
          </ScrollView>

          {/* Rodapé fixo com o botão Salvar. */}
          <View style={[styles.footer, { paddingBottom: insets.bottom + 12 }]}>
            <Pressable
              style={({ pressed }) => [
                styles.btnSalvar,
                !podeSalvar && styles.btnSalvarOff,
                pressed && podeSalvar && styles.btnSalvarPressed,
              ]}
              onPress={salvar}
              disabled={!podeSalvar}
              accessibilityRole="button"
              accessibilityLabel="Salvar dados"
              accessibilityState={{ disabled: !podeSalvar, busy: salvando }}>
              {salvando ? (
                <ActivityIndicator size="small" color={t.onBrand} />
              ) : (
                <Ionicons name="checkmark-circle-outline" size={18} color={t.onBrand} />
              )}
              <Text style={styles.btnSalvarTxt}>{salvando ? 'Salvando…' : 'Salvar'}</Text>
            </Pressable>
          </View>
        </View>
      )}
    </KeyboardAvoidingView>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    screen: { flex: 1, backgroundColor: t.bg },
    flex: { flex: 1 },
    content: { padding: 20, paddingBottom: 28 },
    secaoTitulo: {
      fontSize: 12,
      fontWeight: '700',
      color: t.muted,
      textTransform: 'uppercase',
      letterSpacing: 0.6,
      marginBottom: 10,
      marginLeft: 4,
    },
    secaoEspaco: { marginTop: 18 },
    card: {
      backgroundColor: t.surface,
      borderRadius: 18,
      borderWidth: 1,
      borderColor: t.line,
      padding: 16,
    },
    label: { fontSize: 12.5, fontWeight: '700', color: t.muted, marginBottom: 6 },
    labelEspaco: { marginTop: 14 },
    input: {
      height: 48,
      borderWidth: 1,
      borderColor: t.line,
      borderRadius: 12,
      paddingHorizontal: 14,
      fontSize: 15,
      color: t.ink,
      backgroundColor: t.bg,
    },
    // CPF travado.
    inputTravado: {
      height: 48,
      borderWidth: 1,
      borderColor: t.line,
      borderRadius: 12,
      paddingHorizontal: 14,
      backgroundColor: t.bg,
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      opacity: 0.75,
    },
    inputTravadoTxt: { fontSize: 15, color: t.muted },
    cpfNota: { fontSize: 11.5, color: t.muted, marginTop: 6 },
    // Aviso curto (data usada no login).
    nota: { flexDirection: 'row', alignItems: 'center', gap: 5, marginTop: 6 },
    notaTxt: { flex: 1, fontSize: 11.5, color: t.muted, lineHeight: 16 },
    // Sexo: botões segmentados (2 por linha).
    seg: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
    segBtn: {
      flexGrow: 1,
      flexBasis: '46%',
      minHeight: 46,
      borderRadius: 10,
      borderWidth: 1,
      borderColor: t.line,
      backgroundColor: t.bg,
      alignItems: 'center',
      justifyContent: 'center',
      paddingHorizontal: 6,
      paddingVertical: 8,
    },
    segBtnSel: { borderColor: t.brand, backgroundColor: t.brand },
    segBtnPressed: { backgroundColor: t.line },
    segBtnTxt: { fontSize: 12.5, fontWeight: '700', color: t.muted, textAlign: 'center' },
    segBtnTxtSel: { color: t.onBrand },
    // Telefones (lista editável).
    telLinha: { flexDirection: 'row', alignItems: 'center', gap: 8 },
    telLinhaEspaco: { marginTop: 8 },
    telInput: { flex: 1 },
    telRemover: {
      width: 44,
      height: 44,
      borderRadius: 12,
      alignItems: 'center',
      justifyContent: 'center',
    },
    telRemoverPressed: { backgroundColor: mixSuave(PERIGO) },
    addTel: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 6,
      alignSelf: 'flex-start',
      marginTop: 10,
      height: 40,
      paddingHorizontal: 12,
      borderRadius: 10,
      borderWidth: 1,
      borderColor: t.glow,
      backgroundColor: t.brandTint,
    },
    addTelPressed: { backgroundColor: t.brandTintStrong },
    addTelTxt: { fontSize: 13.5, fontWeight: '800', color: t.brandDeep },
    // Rodapé com o Salvar.
    footer: {
      paddingHorizontal: 20,
      paddingTop: 12,
      backgroundColor: t.surface,
      borderTopWidth: 1,
      borderTopColor: t.line,
    },
    btnSalvar: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 8,
      height: 52,
      borderRadius: 14,
      backgroundColor: t.brand,
    },
    btnSalvarOff: { opacity: 0.45 },
    btnSalvarPressed: { backgroundColor: t.brandDeep },
    btnSalvarTxt: { color: t.onBrand, fontSize: 15, fontWeight: '800' },
    // Estados (carregando / erro).
    estado: { alignItems: 'center', justifyContent: 'center', paddingVertical: 48, gap: 10 },
    estadoIcone: {
      width: 56,
      height: 56,
      borderRadius: 18,
      backgroundColor: t.bg,
      borderWidth: 1,
      borderColor: t.line,
      alignItems: 'center',
      justifyContent: 'center',
    },
    estadoTitulo: { fontSize: 16, fontWeight: '800', color: t.ink, marginTop: 2 },
    estadoTxt: { fontSize: 13.5, color: t.muted, textAlign: 'center', paddingHorizontal: 32, lineHeight: 19 },
    estadoBtn: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 7,
      marginTop: 6,
      height: 44,
      paddingHorizontal: 18,
      borderRadius: 14,
      backgroundColor: t.brand,
    },
    estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
  });

/** Fundo bem suave do vermelho de remover (para o estado pressionado). */
function mixSuave(hex: string): string {
  const h = hex.replace('#', '');
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, 0.1)`;
}
