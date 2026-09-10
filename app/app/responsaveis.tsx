import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Keyboard,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';

import { ScreenHeader } from '@/components/screen-header';
import { SemAcesso } from '@/components/sem-acesso';
import { type Tema, useTema } from '@/hooks/use-tema';
import { useSessao } from '@/hooks/use-sessao';
import {
  adicionarResponsavel,
  definirSituacaoResponsavel,
  listarResponsaveis,
  removerResponsavel,
  type MeuResponsavel,
} from '@/services/responsaveis';
import { ehPerfilProprio } from '@/services/sessao';

/** Vermelho de "remover" (mesmo tom do status "cancelado"). */
const PERIGO = '#B23B4E';

const soDigitos = (v: string) => v.replace(/\D/g, '');

function fmtTelefone(v: string): string {
  const d = soDigitos(v);
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return v;
}

export default function ResponsaveisScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const { sessao } = useSessao();
  const proprio = ehPerfilProprio(sessao);

  const [lista, setLista] = useState<MeuResponsavel[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);

  const [nome, setNome] = useState('');
  const [telefone, setTelefone] = useState(''); // só dígitos
  const [enviando, setEnviando] = useState(false);
  const [removendoId, setRemovendoId] = useState<number | null>(null);
  const [atualizandoId, setAtualizandoId] = useState<number | null>(null);

  const carregar = useCallback(async () => {
    try {
      setErro(false);
      setCarregando(true);
      setLista(await listarResponsaveis());
    } catch {
      setErro(true);
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    // Perfil dependente não gerencia (o backend responde 403): não busca.
    if (!proprio) {
      setCarregando(false);
      return;
    }
    carregar();
  }, [carregar, proprio]);

  const podeAdicionar = nome.trim().length >= 2 && telefone.length >= 10 && !enviando;

  async function adicionar() {
    if (!podeAdicionar) return;
    Keyboard.dismiss();
    try {
      setEnviando(true);
      const novo = await adicionarResponsavel(nome.trim(), telefone);
      setLista((atual) => [novo, ...atual]);
      setNome('');
      setTelefone('');
    } catch (e) {
      Alert.alert('Não foi possível adicionar', e instanceof Error ? e.message : 'Tente novamente.');
    } finally {
      setEnviando(false);
    }
  }

  function confirmarRemocao(r: MeuResponsavel) {
    Alert.alert('Excluir pessoa autorizada', `Excluir ${r.nome}? Ela perderá o acesso para agendar por você.`, [
      { text: 'Cancelar', style: 'cancel' },
      { text: 'Excluir', style: 'destructive', onPress: () => remover(r.id) },
    ]);
  }

  async function remover(id: number) {
    try {
      setRemovendoId(id);
      await removerResponsavel(id);
      setLista((atual) => atual.filter((x) => x.id !== id));
    } catch (e) {
      // 409 = ganhou lançamentos desde que a lista carregou: reflete que agora só cabe inativar,
      // trocando o botão Excluir por Inativar (sem isso, o toque repetiria o 409 até recarregar).
      if ((e as { status?: number }).status === 409) {
        setLista((atual) => atual.map((x) => (x.id === id ? { ...x, podeExcluir: false } : x)));
      }
      Alert.alert('Não foi possível excluir', e instanceof Error ? e.message : 'Tente novamente.');
    } finally {
      setRemovendoId(null);
    }
  }

  function confirmarInativacao(r: MeuResponsavel) {
    Alert.alert(
      'Inativar pessoa autorizada',
      `${r.nome} deixará de poder agendar por você. Você pode reativar quando quiser.`,
      [
        { text: 'Cancelar', style: 'cancel' },
        { text: 'Inativar', style: 'destructive', onPress: () => alternarSituacao(r) },
      ],
    );
  }

  async function alternarSituacao(r: MeuResponsavel) {
    try {
      setAtualizandoId(r.id);
      const atualizado = await definirSituacaoResponsavel(r.id, !r.ativo);
      setLista((atual) => atual.map((x) => (x.id === atualizado.id ? atualizado : x)));
    } catch (e) {
      Alert.alert('Não foi possível atualizar', e instanceof Error ? e.message : 'Tente novamente.');
    } finally {
      setAtualizandoId(null);
    }
  }

  return (
    <View style={styles.screen}>
      <ScreenHeader title="Pessoas autorizadas" />

      {!proprio ? (
        <SemAcesso mensagem="Apenas o próprio paciente pode gerenciar as pessoas autorizadas a agendar por ele." />
      ) : (
        <ScrollView
          contentContainerStyle={styles.content}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag">
          {/* O que a pessoa poderá fazer */}
          <View style={styles.aviso}>
            <Ionicons name="shield-checkmark-outline" size={18} color={t.brandDeep} />
            <Text style={styles.avisoTxt}>
              Quem você adicionar poderá <Text style={styles.avisoForte}>ver e agendar</Text> consultas por você — e
              nada mais. Não terá acesso ao seu prontuário, chat, ou outros dados.
            </Text>
          </View>

          {/* Adicionar */}
          <View style={styles.secao}>
            <Text style={styles.secaoTitulo}>Adicionar pessoa</Text>
            <View style={styles.card}>
              <Text style={styles.label}>Nome</Text>
              <TextInput
                style={styles.input}
                value={nome}
                onChangeText={setNome}
                placeholder="Nome da pessoa"
                placeholderTextColor={t.muted}
                autoCapitalize="words"
                returnKeyType="next"
                maxLength={120}
              />
              <Text style={[styles.label, styles.labelEspaco]}>Telefone (com DDD)</Text>
              <TextInput
                style={styles.input}
                value={fmtTelefone(telefone)}
                onChangeText={(v) => setTelefone(soDigitos(v).slice(0, 11))}
                placeholder="(11) 98888-1111"
                placeholderTextColor={t.muted}
                keyboardType="phone-pad"
                returnKeyType="done"
                onSubmitEditing={adicionar}
              />
              <Text style={styles.dica}>
                A pessoa entra no app com o próprio telefone (mesmo login por SMS) e passa a ver este perfil.
              </Text>
              <Pressable
                style={({ pressed }) => [
                  styles.btnAdd,
                  !podeAdicionar && styles.btnAddOff,
                  pressed && podeAdicionar && styles.btnAddPressed,
                ]}
                onPress={adicionar}
                disabled={!podeAdicionar}
                accessibilityRole="button"
                accessibilityLabel="Adicionar pessoa autorizada"
                accessibilityState={{ disabled: !podeAdicionar, busy: enviando }}>
                {enviando ? (
                  <ActivityIndicator size="small" color={t.onBrand} />
                ) : (
                  <Ionicons name="person-add-outline" size={18} color={t.onBrand} />
                )}
                <Text style={styles.btnAddTxt}>{enviando ? 'Adicionando…' : 'Adicionar'}</Text>
              </Pressable>
            </View>
          </View>

          {/* Lista */}
          <View style={styles.secao}>
            <Text style={styles.secaoTitulo}>Pessoas autorizadas</Text>

            {carregando ? (
              <View style={styles.estado}>
                <ActivityIndicator color={t.brand} />
                <Text style={styles.estadoTxt}>Carregando…</Text>
              </View>
            ) : erro ? (
              <View style={styles.estado}>
                <View style={styles.estadoIcone}>
                  <Ionicons name="cloud-offline-outline" size={24} color={t.muted} />
                </View>
                <Text style={styles.estadoTitulo}>Não foi possível carregar</Text>
                <Text style={styles.estadoTxt}>Verifique sua conexão e tente novamente.</Text>
                <Pressable style={styles.estadoBtn} onPress={carregar} accessibilityRole="button">
                  <Ionicons name="refresh" size={16} color="#fff" />
                  <Text style={styles.estadoBtnTxt}>Tentar novamente</Text>
                </Pressable>
              </View>
            ) : lista.length === 0 ? (
              <View style={styles.vazio}>
                <View style={styles.estadoIcone}>
                  <Ionicons name="people-outline" size={24} color={t.muted} />
                </View>
                <Text style={styles.estadoTitulo}>Ninguém autorizado ainda</Text>
                <Text style={styles.estadoTxt}>
                  Adicione uma pessoa acima para que ela possa agendar consultas por você.
                </Text>
              </View>
            ) : (
              <View style={styles.card}>
                {lista.map((r, i) => {
                  const ocupado = removendoId === r.id || atualizandoId === r.id;
                  return (
                    <View key={r.id} style={[styles.linha, i > 0 && styles.linhaBorda]}>
                      <View style={[styles.avatar, !r.ativo && styles.avatarInativo]}>
                        <Ionicons name="person-outline" size={18} color={r.ativo ? t.brandDeep : t.muted} />
                      </View>
                      <View style={[{ flex: 1 }, !r.ativo && styles.linhaInativaTxt]}>
                        <Text style={styles.linhaNome}>{r.nome}</Text>
                        <Text style={styles.linhaTel}>{fmtTelefone(r.telefone)}</Text>
                        {!r.ativo && (
                          <View style={styles.badgeInativo}>
                            <Text style={styles.badgeInativoTxt}>Inativo</Text>
                          </View>
                        )}
                      </View>

                      {ocupado ? (
                        <ActivityIndicator size="small" color={t.brand} style={styles.acaoSpinner} />
                      ) : !r.ativo ? (
                        // Inativo → reativar (devolve o acesso).
                        <Pressable
                          style={({ pressed }) => [styles.pill, styles.pillBrand, pressed && styles.pillBrandPressed]}
                          onPress={() => alternarSituacao(r)}
                          accessibilityRole="button"
                          accessibilityLabel={`Reativar ${r.nome}`}>
                          <Ionicons name="refresh" size={15} color={t.brandDeep} />
                          <Text style={styles.pillBrandTxt}>Reativar</Text>
                        </Pressable>
                      ) : r.podeExcluir ? (
                        // Ativo e sem lançamentos → excluir de vez.
                        <Pressable
                          style={({ pressed }) => [styles.remover, pressed && styles.removerPressed]}
                          onPress={() => confirmarRemocao(r)}
                          hitSlop={8}
                          accessibilityRole="button"
                          accessibilityLabel={`Excluir ${r.nome}`}>
                          <Ionicons name="trash-outline" size={19} color={PERIGO} />
                        </Pressable>
                      ) : (
                        // Ativo com lançamentos → só inativar (preserva o histórico).
                        <Pressable
                          style={({ pressed }) => [styles.pill, pressed && styles.pillPressed]}
                          onPress={() => confirmarInativacao(r)}
                          accessibilityRole="button"
                          accessibilityLabel={`Inativar ${r.nome}`}>
                          <Ionicons name="pause-circle-outline" size={15} color={t.muted} />
                          <Text style={styles.pillTxt}>Inativar</Text>
                        </Pressable>
                      )}
                    </View>
                  );
                })}
              </View>
            )}
          </View>
        </ScrollView>
      )}
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    screen: { flex: 1, backgroundColor: t.bg },
    content: { padding: 20, paddingBottom: 48 },
    aviso: {
      flexDirection: 'row',
      alignItems: 'flex-start',
      gap: 10,
      backgroundColor: t.brandTint,
      borderRadius: 14,
      paddingHorizontal: 14,
      paddingVertical: 12,
      marginBottom: 20,
    },
    avisoTxt: { flex: 1, fontSize: 13, color: t.muted, lineHeight: 19 },
    avisoForte: { fontWeight: '800', color: t.brandDeep },
    secao: { marginBottom: 20 },
    secaoTitulo: {
      fontSize: 12,
      fontWeight: '700',
      color: t.muted,
      textTransform: 'uppercase',
      letterSpacing: 0.6,
      marginBottom: 10,
      marginLeft: 4,
    },
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
    dica: { fontSize: 12, color: t.muted, lineHeight: 17, marginTop: 10 },
    btnAdd: {
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      gap: 8,
      height: 50,
      borderRadius: 14,
      backgroundColor: t.brand,
      marginTop: 16,
    },
    btnAddOff: { opacity: 0.45 },
    btnAddPressed: { backgroundColor: t.brandDeep },
    btnAddTxt: { color: t.onBrand, fontSize: 15, fontWeight: '800' },
    linha: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12 },
    linhaBorda: { borderTopWidth: 1, borderTopColor: t.brandTint },
    avatar: {
      width: 40,
      height: 40,
      borderRadius: 12,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: t.brandTint,
    },
    linhaNome: { fontSize: 15, fontWeight: '700', color: t.ink },
    linhaTel: { fontSize: 13, color: t.muted, marginTop: 2 },
    remover: {
      width: 40,
      height: 40,
      borderRadius: 12,
      alignItems: 'center',
      justifyContent: 'center',
    },
    removerPressed: { backgroundColor: mixSuave(PERIGO) },
    avatarInativo: { backgroundColor: t.line },
    linhaInativaTxt: { opacity: 0.65 },
    badgeInativo: {
      alignSelf: 'flex-start',
      marginTop: 5,
      paddingHorizontal: 8,
      paddingVertical: 2,
      borderRadius: 6,
      backgroundColor: t.line,
    },
    badgeInativoTxt: { fontSize: 10.5, fontWeight: '800', color: t.muted, letterSpacing: 0.4 },
    pill: {
      flexDirection: 'row',
      alignItems: 'center',
      gap: 5,
      height: 34,
      paddingHorizontal: 12,
      borderRadius: 10,
      borderWidth: 1,
      borderColor: t.line,
      backgroundColor: t.bg,
    },
    pillPressed: { backgroundColor: t.line },
    pillTxt: { fontSize: 13, fontWeight: '700', color: t.muted },
    pillBrand: { borderColor: t.glow, backgroundColor: t.brandTint },
    pillBrandPressed: { backgroundColor: t.brandTintStrong },
    pillBrandTxt: { fontSize: 13, fontWeight: '800', color: t.brandDeep },
    acaoSpinner: { width: 40, height: 40 },
    estado: { alignItems: 'center', justifyContent: 'center', paddingVertical: 32, gap: 10 },
    vazio: {
      alignItems: 'center',
      justifyContent: 'center',
      gap: 10,
      paddingVertical: 30,
      paddingHorizontal: 16,
      backgroundColor: t.surface,
      borderRadius: 18,
      borderWidth: 1,
      borderColor: t.line,
    },
    estadoIcone: {
      width: 52,
      height: 52,
      borderRadius: 16,
      backgroundColor: t.bg,
      borderWidth: 1,
      borderColor: t.line,
      alignItems: 'center',
      justifyContent: 'center',
    },
    estadoTitulo: { fontSize: 15.5, fontWeight: '800', color: t.ink, marginTop: 2 },
    estadoTxt: { fontSize: 13, color: t.muted, textAlign: 'center', paddingHorizontal: 20, lineHeight: 18 },
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
