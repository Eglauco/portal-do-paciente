import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ScreenHeader } from '@/components/screen-header';
import { Brand } from '@/constants/theme';
import { useSessao } from '@/hooks/use-sessao';

const FOTO_PACIENTE = 'https://i.pravatar.cc/240?img=47';

interface Campo {
  icon: string;
  rotulo: string;
  valor: string;
}
interface Secao {
  titulo: string;
  campos: Campo[];
}

const SECOES: Secao[] = [
  {
    titulo: 'Dados pessoais',
    campos: [
      { icon: 'person-outline', rotulo: 'Nome completo', valor: 'Mariana Duarte Oliveira' },
      { icon: 'card-outline', rotulo: 'CPF', valor: '123.456.789-00' },
      { icon: 'calendar-outline', rotulo: 'Data de nascimento', valor: '14/03/1990 (36 anos)' },
      { icon: 'male-female-outline', rotulo: 'Sexo', valor: 'Feminino' },
      { icon: 'heart-outline', rotulo: 'Estado civil', valor: 'Casada' },
    ],
  },
  {
    titulo: 'Contato',
    campos: [
      { icon: 'call-outline', rotulo: 'Telefone', valor: '(11) 98765-4321' },
      { icon: 'mail-outline', rotulo: 'E-mail', valor: 'mariana.duarte@email.com' },
    ],
  },
  {
    titulo: 'Endereço',
    campos: [
      { icon: 'home-outline', rotulo: 'Logradouro', valor: 'Rua das Acácias, 245 — Apto 52' },
      { icon: 'map-outline', rotulo: 'Bairro', valor: 'Jardim Primavera' },
      { icon: 'location-outline', rotulo: 'Cidade / UF', valor: 'São Paulo / SP' },
      { icon: 'navigate-outline', rotulo: 'CEP', valor: '01234-567' },
    ],
  },
  {
    titulo: 'Saúde',
    campos: [
      { icon: 'medkit-outline', rotulo: 'Cartão SUS', valor: '700 1234 5678 9012' },
      { icon: 'shield-checkmark-outline', rotulo: 'Convênio', valor: 'Unimed — Plano Pleno' },
      { icon: 'water-outline', rotulo: 'Tipo sanguíneo', valor: 'O+' },
    ],
  },
];

export default function PerfilScreen() {
  const router = useRouter();
  const { sessao, sair } = useSessao();
  const [saindo, setSaindo] = useState(false);

  async function sairDaConta() {
    if (saindo) return;
    setSaindo(true);
    await sair();
    router.replace('/');
  }

  return (
    <View style={styles.screen}>
      <ScreenHeader
        title="Meu perfil"
        action={<Ionicons name="create-outline" size={22} color={Brand.brandDeep} />}
      />
      <ScrollView contentContainerStyle={styles.content}>
        {/* Cabeçalho do perfil */}
        <View style={styles.hero}>
          <View style={styles.avatarRing}>
            <Image source={FOTO_PACIENTE} style={styles.avatar} contentFit="cover" transition={200} />
          </View>
          <Text style={styles.nome}>{sessao?.nome ?? 'Paciente'}</Text>
          <Text style={styles.sub}>Paciente · Unidade de Saúde 01</Text>
          <View style={styles.codigo}>
            <Ionicons name="finger-print-outline" size={13} color={Brand.brandDeep} />
            <Text style={styles.codigoTxt}>Prontuário nº 004821</Text>
          </View>
        </View>

        {SECOES.map((secao) => (
          <View key={secao.titulo} style={styles.secao}>
            <Text style={styles.secaoTitulo}>{secao.titulo}</Text>
            <View style={styles.card}>
              {secao.campos.map((campo, i) => (
                <View key={campo.rotulo} style={[styles.linha, i > 0 && styles.linhaBorda]}>
                  <View style={styles.linhaIcon}>
                    <Ionicons name={campo.icon as any} size={18} color={Brand.brandDeep} />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.rotulo}>{campo.rotulo}</Text>
                    <Text style={styles.valor}>{campo.valor}</Text>
                  </View>
                </View>
              ))}
            </View>
          </View>
        ))}

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
    marginBottom: 12,
  },
  avatar: { width: '100%', height: '100%', borderRadius: 44, backgroundColor: Brand.line },
  nome: { fontSize: 22, fontWeight: '800', color: Brand.ink, letterSpacing: -0.4 },
  sub: { fontSize: 13.5, color: Brand.muted, marginTop: 3 },
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
