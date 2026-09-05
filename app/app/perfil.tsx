import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import type { ImagePickerOptions } from 'expo-image-picker';
import { useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ScreenHeader } from '@/components/screen-header';
import { Brand } from '@/constants/theme';
import { usePerfilFoto } from '@/hooks/use-perfil-foto';
import { useSessao } from '@/hooks/use-sessao';
import { carregarPerfil, excluirFoto, trocarFoto, type MeuPerfil, type SexoPaciente } from '@/services/perfil';

const SEXO_LABEL: Record<SexoPaciente, string> = {
  MASCULINO: 'Masculino',
  FEMININO: 'Feminino',
  OUTRO: 'Outro',
  NAO_INFORMADO: 'Não informado',
};

const soDigitos = (v: string | null) => (v ?? '').replace(/\D/g, '');

function fmtCpf(v: string | null) {
  const d = soDigitos(v);
  return d.length === 11 ? `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}` : v;
}
function fmtCns(v: string | null) {
  const d = soDigitos(v);
  return d.length === 15 ? `${d.slice(0, 3)} ${d.slice(3, 7)} ${d.slice(7, 11)} ${d.slice(11)}` : v;
}
function fmtCep(v: string | null) {
  const d = soDigitos(v);
  return d.length === 8 ? `${d.slice(0, 5)}-${d.slice(5)}` : v;
}
function fmtTelefone(v: string | null) {
  const d = soDigitos(v);
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return v;
}
function fmtNascimento(iso: string | null): string | null {
  if (!iso) return null;
  const [ano, mes, dia] = iso.split('-');
  if (!dia) return iso;
  const nasc = new Date(Number(ano), Number(mes) - 1, Number(dia));
  const hoje = new Date();
  let idade = hoje.getFullYear() - nasc.getFullYear();
  const m = hoje.getMonth() - nasc.getMonth();
  if (m < 0 || (m === 0 && hoje.getDate() < nasc.getDate())) idade--;
  return `${dia}/${mes}/${ano}${idade >= 0 ? ` (${idade} anos)` : ''}`;
}

interface Campo {
  icon: string;
  rotulo: string;
  valor: string;
}
function campo(icon: string, rotulo: string, valor: string | null | undefined): Campo | null {
  return valor ? { icon, rotulo, valor } : null;
}

/** Monta as seções mostrando apenas os campos que têm valor. */
function montarSecoes(p: MeuPerfil) {
  const ruaNumero = [p.rua, p.numero].filter(Boolean).join(', ');
  const logradouro = [ruaNumero, p.complemento].filter(Boolean).join(' — ');
  const cidadeUf = [p.municipio, p.uf].filter(Boolean).join(' / ');
  const adicionais = p.telefonesAdicionais.map((t) => campo('call-outline', 'Outro telefone', fmtTelefone(t)));

  const secoes = [
    {
      titulo: 'Dados pessoais',
      campos: [
        campo('person-outline', 'Nome completo', p.nome),
        campo('calendar-outline', 'Data de nascimento', fmtNascimento(p.dataNascimento)),
        campo('male-female-outline', 'Sexo', p.sexo ? SEXO_LABEL[p.sexo] : null),
        campo('woman-outline', 'Nome da mãe', p.nomeMae),
        campo('man-outline', 'Nome do pai', p.nomePai),
      ],
    },
    {
      titulo: 'Documentos',
      campos: [
        campo('card-outline', 'CPF', fmtCpf(p.cpf)),
        campo('card-outline', 'RG', p.rg),
        campo('medkit-outline', 'Cartão SUS (CNS)', fmtCns(p.cns)),
        campo('document-text-outline', 'Prontuário', p.prontuario),
        campo('barcode-outline', 'Código de integração', p.codigoIntegracao),
      ],
    },
    {
      titulo: 'Contato',
      campos: [
        campo('call-outline', 'Telefone', fmtTelefone(p.telefone)),
        ...adicionais,
        campo('mail-outline', 'E-mail', p.email),
      ],
    },
    {
      titulo: 'Endereço',
      campos: [
        campo('home-outline', 'Logradouro', logradouro || null),
        campo('map-outline', 'Bairro', p.bairro),
        campo('location-outline', 'Cidade / UF', cidadeUf || null),
        campo('navigate-outline', 'CEP', fmtCep(p.cep)),
      ],
    },
  ];

  return secoes
    .map((s) => ({ titulo: s.titulo, campos: s.campos.filter((c): c is Campo => c !== null) }))
    .filter((s) => s.campos.length > 0);
}

export default function PerfilScreen() {
  const router = useRouter();
  const { sessao, sair } = useSessao();
  const { definirFoto } = usePerfilFoto();
  const [perfil, setPerfil] = useState<MeuPerfil | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);
  const [enviandoFoto, setEnviandoFoto] = useState(false);
  const [saindo, setSaindo] = useState(false);

  const carregar = useCallback(async () => {
    try {
      setErro(false);
      setCarregando(true);
      const dados = await carregarPerfil();
      setPerfil(dados);
      definirFoto(dados.fotoUrl); // mantém o cabeçalho em sincronia
    } catch {
      setErro(true);
    } finally {
      setCarregando(false);
    }
  }, [definirFoto]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function trocarFotoFluxo(origem: 'camera' | 'galeria') {
    try {
      // Carregado sob demanda: a tela (só leitura) não depende do módulo nativo para abrir.
      const ImagePicker = await import('expo-image-picker');
      const permissao =
        origem === 'camera'
          ? await ImagePicker.requestCameraPermissionsAsync()
          : await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permissao.granted) {
        Alert.alert('Permissão necessária', 'Autorize o acesso para trocar a foto do perfil.');
        return;
      }

      const opcoes: ImagePickerOptions = {
        mediaTypes: ['images'],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 0.7,
      };
      const resultado =
        origem === 'camera'
          ? await ImagePicker.launchCameraAsync(opcoes)
          : await ImagePicker.launchImageLibraryAsync(opcoes);
      if (resultado.canceled || !resultado.assets?.length) return;

      const asset = resultado.assets[0];
      setEnviandoFoto(true);
      const atualizado = await trocarFoto(asset.uri, asset.fileName ?? 'foto.jpg', asset.mimeType ?? 'image/jpeg');
      setPerfil(atualizado);
      definirFoto(atualizado.fotoUrl); // reflete a nova foto no cabeçalho
      setErro(false); // o PUT devolve o perfil completo: se o GET inicial falhou, sai da tela de erro
    } catch (e) {
      Alert.alert('Não foi possível trocar a foto', e instanceof Error ? e.message : 'Tente novamente.');
    } finally {
      setEnviandoFoto(false);
    }
  }

  async function removerFoto() {
    if (enviandoFoto) return;
    try {
      setEnviandoFoto(true);
      const atualizado = await excluirFoto();
      setPerfil(atualizado);
      definirFoto(atualizado.fotoUrl); // null → cabeçalho volta ao ícone
      setErro(false);
    } catch (e) {
      Alert.alert('Não foi possível remover a foto', e instanceof Error ? e.message : 'Tente novamente.');
    } finally {
      setEnviandoFoto(false);
    }
  }

  function escolherOrigemFoto() {
    if (enviandoFoto) return;
    Alert.alert('Foto do perfil', 'Escolha uma opção', [
      { text: 'Tirar foto', onPress: () => trocarFotoFluxo('camera') },
      { text: 'Escolher da galeria', onPress: () => trocarFotoFluxo('galeria') },
      ...(perfil?.fotoUrl
        ? [{ text: 'Remover foto atual', style: 'destructive' as const, onPress: removerFoto }]
        : []),
      { text: 'Cancelar', style: 'cancel' as const },
    ]);
  }

  async function sairDaConta() {
    if (saindo) return;
    setSaindo(true);
    await sair();
    router.replace('/');
  }

  const nome = perfil?.nome ?? sessao?.nome ?? 'Paciente';
  const secoes = perfil ? montarSecoes(perfil) : [];

  return (
    <View style={styles.screen}>
      <ScreenHeader title="Meu perfil" />
      <ScrollView contentContainerStyle={styles.content}>
        {/* Cabeçalho do perfil */}
        <View style={styles.hero}>
          <Pressable
            onPress={escolherOrigemFoto}
            disabled={enviandoFoto}
            accessibilityRole="button"
            accessibilityLabel="Alterar foto do perfil"
            accessibilityState={{ busy: enviandoFoto }}
            style={styles.avatarRing}>
            {perfil?.fotoUrl ? (
              <Image source={perfil.fotoUrl} style={styles.avatar} contentFit="cover" transition={200} />
            ) : (
              <View style={[styles.avatar, styles.avatarVazio]}>
                <Ionicons name="person" size={44} color={Brand.muted} />
              </View>
            )}
            <View style={styles.cameraBadge}>
              {enviandoFoto ? (
                <ActivityIndicator size="small" color="#fff" />
              ) : (
                <Ionicons name="camera" size={15} color="#fff" />
              )}
            </View>
          </Pressable>
          <Text style={styles.nome}>{nome}</Text>
          <Pressable onPress={escolherOrigemFoto} disabled={enviandoFoto} accessibilityRole="button">
            <Text style={styles.trocarFoto}>{enviandoFoto ? 'Enviando foto…' : 'Alterar foto'}</Text>
          </Pressable>
          {perfil?.prontuario ? (
            <View style={styles.codigo}>
              <Ionicons name="finger-print-outline" size={13} color={Brand.brandDeep} />
              <Text style={styles.codigoTxt}>Prontuário nº {perfil.prontuario}</Text>
            </View>
          ) : null}
        </View>

        {carregando ? (
          <View style={styles.estado}>
            <ActivityIndicator color={Brand.brand} />
            <Text style={styles.estadoTxt}>Carregando seus dados…</Text>
          </View>
        ) : erro ? (
          <View style={styles.estado}>
            <View style={styles.estadoIcone}>
              <Ionicons name="cloud-offline-outline" size={26} color={Brand.muted} />
            </View>
            <Text style={styles.estadoTitulo}>Não foi possível carregar</Text>
            <Text style={styles.estadoTxt}>Verifique sua conexão com o servidor e tente novamente.</Text>
            <Pressable style={styles.estadoBtn} onPress={carregar}>
              <Ionicons name="refresh" size={16} color="#fff" />
              <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
            </Pressable>
          </View>
        ) : (
          <>
            <View style={styles.aviso}>
              <Ionicons name="lock-closed-outline" size={14} color={Brand.muted} />
              <Text style={styles.avisoTxt}>
                Seus dados são somente para consulta. Para corrigir algo, procure a sua unidade de saúde.
              </Text>
            </View>
            {secoes.map((secao) => (
              <View key={secao.titulo} style={styles.secao}>
                <Text style={styles.secaoTitulo}>{secao.titulo}</Text>
                <View style={styles.card}>
                  {secao.campos.map((c, i) => (
                    <View key={`${secao.titulo}-${i}`} style={[styles.linha, i > 0 && styles.linhaBorda]}>
                      <View style={styles.linhaIcon}>
                        <Ionicons name={c.icon as never} size={18} color={Brand.brandDeep} />
                      </View>
                      <View style={{ flex: 1 }}>
                        <Text style={styles.rotulo}>{c.rotulo}</Text>
                        <Text style={styles.valor}>{c.valor}</Text>
                      </View>
                    </View>
                  ))}
                </View>
              </View>
            ))}
          </>
        )}

        <Pressable
          style={({ pressed }) => [styles.sair, pressed && styles.sairPressed]}
          onPress={sairDaConta}
          disabled={saindo}>
          <Ionicons name="log-out-outline" size={20} color="#B23B4E" />
          <Text style={styles.sairTxt}>{saindo ? 'Saindo…' : 'Sair da conta'}</Text>
        </Pressable>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: Brand.bg },
  content: { padding: 20, paddingBottom: 40 },
  hero: { alignItems: 'center', marginBottom: 22 },
  avatarRing: {
    width: 96,
    height: 96,
    borderRadius: 48,
    padding: 3,
    borderWidth: 2,
    borderColor: Brand.glow,
    marginBottom: 10,
  },
  avatar: { width: '100%', height: '100%', borderRadius: 44, backgroundColor: Brand.line },
  avatarVazio: { alignItems: 'center', justifyContent: 'center', backgroundColor: '#E7F3EF' },
  cameraBadge: {
    position: 'absolute',
    right: -2,
    bottom: -2,
    width: 30,
    height: 30,
    borderRadius: 15,
    backgroundColor: Brand.brand,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: Brand.bg,
  },
  nome: { fontSize: 22, fontWeight: '800', color: Brand.ink, letterSpacing: -0.4 },
  trocarFoto: { fontSize: 13.5, fontWeight: '700', color: Brand.brandDeep, marginTop: 4 },
  codigo: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#E7F3EF',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    marginTop: 12,
  },
  codigoTxt: { fontSize: 12.5, fontWeight: '700', color: Brand.brandDeep },
  aviso: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: '#EEF3F1',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    marginBottom: 18,
  },
  avisoTxt: { flex: 1, fontSize: 12.5, color: Brand.muted, lineHeight: 17 },
  secao: { marginBottom: 18 },
  secaoTitulo: {
    fontSize: 12,
    fontWeight: '700',
    color: Brand.muted,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginBottom: 10,
    marginLeft: 4,
  },
  card: {
    backgroundColor: Brand.surface,
    borderRadius: 18,
    borderWidth: 1,
    borderColor: Brand.line,
    paddingHorizontal: 14,
  },
  linha: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 13 },
  linhaBorda: { borderTopWidth: 1, borderTopColor: '#EEF3F1' },
  linhaIcon: {
    width: 38,
    height: 38,
    borderRadius: 11,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#E7F3EF',
  },
  rotulo: { fontSize: 12, color: Brand.muted },
  valor: { fontSize: 14.5, fontWeight: '600', color: Brand.ink, marginTop: 1 },
  estado: { alignItems: 'center', justifyContent: 'center', paddingVertical: 40, gap: 10 },
  estadoIcone: {
    width: 56,
    height: 56,
    borderRadius: 18,
    backgroundColor: Brand.bg,
    borderWidth: 1,
    borderColor: Brand.line,
    alignItems: 'center',
    justifyContent: 'center',
  },
  estadoTitulo: { fontSize: 16, fontWeight: '800', color: Brand.ink, marginTop: 2 },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center', paddingHorizontal: 32, lineHeight: 19 },
  estadoBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    marginTop: 6,
    height: 44,
    paddingHorizontal: 18,
    borderRadius: 14,
    backgroundColor: Brand.brand,
  },
  estadoBtnTxt: { color: '#fff', fontSize: 14, fontWeight: '700' },
  sair: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: Brand.surface,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: '#F3D6DB',
    paddingVertical: 15,
    marginTop: 6,
  },
  sairPressed: { backgroundColor: '#FDF2F3' },
  sairTxt: { fontSize: 15, fontWeight: '700', color: '#B23B4E' },
});
