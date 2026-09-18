import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ScreenHeader } from '@/components/screen-header';
import { SemAcesso } from '@/components/sem-acesso';
import { useFuncionalidades } from '@/hooks/use-funcionalidades';
import { alpha, type Tema, useTema } from '@/hooks/use-tema';
import { useSessao } from '@/hooks/use-sessao';
import {
  adicionarResponsavel,
  definirSituacaoResponsavel,
  editarResponsavel,
  FUNCIONALIDADES,
  listarResponsaveis,
  type MapaPermissoes,
  type MeuResponsavel,
  NIVEIS,
  type NivelAcesso,
  permissoesVazias,
  removerResponsavel,
} from '@/services/responsaveis';
import { ehPerfilProprio, type FuncionalidadeApp } from '@/services/sessao';

/** Vermelho de "remover" (mesmo tom do status "cancelado"). */
const PERIGO = '#B23B4E';

const soDigitos = (v: string) => v.replace(/\D/g, '');

function fmtTelefone(v: string): string {
  const d = soDigitos(v);
  if (d.length === 11) return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`;
  if (d.length === 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`;
  return v;
}

/** Máscara de CPF "000.000.000-00" enquanto o paciente digita. */
function fmtCpf(v: string): string {
  const d = soDigitos(v).slice(0, 11);
  if (d.length <= 3) return d;
  if (d.length <= 6) return `${d.slice(0, 3)}.${d.slice(3)}`;
  if (d.length <= 9) return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6)}`;
  return `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}`;
}

/** Máscara de data "00/00/0000" (dd/mm/aaaa) enquanto o paciente digita. */
function fmtData(v: string): string {
  const d = soDigitos(v).slice(0, 8);
  if (d.length <= 2) return d;
  if (d.length <= 4) return `${d.slice(0, 2)}/${d.slice(2)}`;
  return `${d.slice(0, 2)}/${d.slice(2, 4)}/${d.slice(4)}`;
}

/** Converte a data digitada "dd/mm/aaaa" para o ISO "AAAA-MM-DD" esperado pelo backend. */
function dataParaIso(v: string): string {
  const d = soDigitos(v);
  if (d.length !== 8) return '';
  return `${d.slice(4, 8)}-${d.slice(2, 4)}-${d.slice(0, 2)}`;
}

/** Converte o ISO "AAAA-MM-DD" (vindo do backend) de volta para os dígitos "ddmmaaaa" da máscara. */
function isoParaDigitos(iso: string): string {
  const d = soDigitos(iso); // "AAAAMMDD"
  if (d.length !== 8) return '';
  return `${d.slice(6, 8)}${d.slice(4, 6)}${d.slice(0, 4)}`;
}

/** Normaliza um mapa parcial de permissões num mapa completo (chave ausente = SEM_ACESSO). */
function normalizarPermissoes(p: MapaPermissoes | undefined): MapaPermissoes {
  return { ...permissoesVazias(), ...(p ?? {}) };
}

/**
 * Matriz de permissões: uma linha por funcionalidade com botões segmentados de nível
 * (Sem acesso / Só visualizar / Ver e lançar). Reutilizada no adicionar e no editar.
 * O botão selecionado fica destacado; Prontuário não oferece "Ver e lançar".
 *
 * `telaHabilitada` filtra da EXIBIÇÃO as telas desligadas globalmente (kill switch). O
 * payload NÃO é filtrado aqui: `edPermissoes` continua carregando/reenviando o nível salvo
 * da tela oculta (ver `salvar`), então o vínculo não se perde no PUT (substituição total).
 */
function MatrizPermissoes({
  permissoes,
  onChange,
  desabilitado,
  telaHabilitada,
  styles,
}: {
  permissoes: MapaPermissoes;
  onChange: (funcionalidade: string, nivel: NivelAcesso) => void;
  desabilitado?: boolean;
  telaHabilitada: (func: FuncionalidadeApp) => boolean;
  styles: ReturnType<typeof criarEstilos>;
}) {
  return (
    <View style={styles.matriz}>
      {FUNCIONALIDADES.filter((f) => telaHabilitada(f.valor)).map((f, i) => {
        const niveis = f.semLancamento ? NIVEIS.filter((n) => n.valor !== 'VISUALIZAR_LANCAR') : NIVEIS;
        const atual = permissoes[f.valor] ?? 'SEM_ACESSO';
        return (
          <View key={f.valor} style={[styles.permLinha, i > 0 && styles.permLinhaBorda]}>
            <Text style={styles.permRotulo}>{f.rotulo}</Text>
            <View style={styles.seg}>
              {niveis.map((n) => {
                const sel = atual === n.valor;
                return (
                  <Pressable
                    key={n.valor}
                    style={({ pressed }) => [
                      styles.segBtn,
                      sel && styles.segBtnSel,
                      pressed && !sel && styles.segBtnPressed,
                    ]}
                    onPress={() => onChange(f.valor, n.valor)}
                    disabled={desabilitado}
                    accessibilityRole="button"
                    accessibilityState={{ selected: sel, disabled: desabilitado }}
                    accessibilityLabel={`${f.rotulo}: ${n.rotulo}`}>
                    <Text style={[styles.segBtnTxt, sel && styles.segBtnTxtSel]}>{n.rotulo}</Text>
                  </Pressable>
                );
              })}
            </View>
          </View>
        );
      })}
    </View>
  );
}

export default function ResponsaveisScreen() {
  const t = useTema();
  const styles = useMemo(() => criarEstilos(t), [t]);
  const insets = useSafeAreaInsets();
  const { sessao } = useSessao();
  const { telaHabilitada } = useFuncionalidades();
  const proprio = ehPerfilProprio(sessao);

  const [lista, setLista] = useState<MeuResponsavel[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(false);

  const [removendoId, setRemovendoId] = useState<number | null>(null);
  const [atualizandoId, setAtualizandoId] = useState<number | null>(null);

  // Modal único de cadastro/edição, reaproveitado nos dois modos. No "adicionar" o CPF é editável
  // (é a identidade de login da nova pessoa); no "editar" fica somente-leitura. Os campos são
  // preenchidos ao abrir e NÃO são limpos ao fechar (evita ler nulo durante o fade de saída).
  const [edVisivel, setEdVisivel] = useState(false);
  const [edModo, setEdModo] = useState<'adicionar' | 'editar'>('adicionar');
  const [edId, setEdId] = useState<number | null>(null);
  const [edCpf, setEdCpf] = useState(''); // só dígitos
  const [edNome, setEdNome] = useState('');
  const [edData, setEdData] = useState(''); // só dígitos
  const [edTelefone, setEdTelefone] = useState(''); // só dígitos
  const [edPermissoes, setEdPermissoes] = useState<MapaPermissoes>(() => permissoesVazias());
  const [edSalvando, setEdSalvando] = useState(false);

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

  function confirmarRemocao(r: MeuResponsavel) {
    Alert.alert('Excluir pessoa autorizada', `Excluir ${r.nome}? Ela perderá o acesso ao seu perfil.`, [
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
      `${r.nome} deixará de acessar o seu perfil. Você pode reativar quando quiser.`,
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

  function abrirAdicao() {
    setEdModo('adicionar');
    setEdId(null);
    setEdCpf('');
    setEdNome('');
    setEdData('');
    setEdTelefone('');
    setEdPermissoes(permissoesVazias());
    setEdVisivel(true);
  }

  function abrirEdicao(r: MeuResponsavel) {
    setEdModo('editar');
    setEdId(r.id);
    setEdCpf(soDigitos(r.cpf));
    setEdNome(r.nome);
    setEdData(isoParaDigitos(r.dataNascimento));
    setEdTelefone(soDigitos(r.telefone));
    setEdPermissoes(normalizarPermissoes(r.permissoes));
    setEdVisivel(true);
  }

  function fecharEdicao() {
    if (!edSalvando) setEdVisivel(false);
  }

  const adicionando = edModo === 'adicionar';

  const podeSalvar =
    edNome.trim().length >= 2 &&
    edData.length === 8 &&
    edTelefone.length >= 10 &&
    (!adicionando || edCpf.length === 11) &&
    !edSalvando;

  /** Salva o modal conforme o modo: POST (adicionar) ou PUT (editar). */
  async function salvar() {
    if (!podeSalvar) return;
    Keyboard.dismiss();
    try {
      setEdSalvando(true);
      if (adicionando) {
        const novo = await adicionarResponsavel(edNome.trim(), edCpf, dataParaIso(edData), edTelefone, edPermissoes);
        setLista((atual) => [novo, ...atual]);
      } else if (edId != null) {
        const atualizado = await editarResponsavel(edId, {
          nome: edNome.trim(),
          dataNascimento: dataParaIso(edData),
          telefone: edTelefone,
          permissoes: edPermissoes,
        });
        setLista((atual) => atual.map((x) => (x.id === atualizado.id ? atualizado : x)));
      }
      setEdVisivel(false);
    } catch (e) {
      Alert.alert(
        adicionando ? 'Não foi possível adicionar' : 'Não foi possível salvar',
        e instanceof Error ? e.message : 'Tente novamente.',
      );
    } finally {
      setEdSalvando(false);
    }
  }

  return (
    <View style={styles.screen}>
      <ScreenHeader title="Pessoas autorizadas" />

      {!proprio ? (
        <SemAcesso mensagem="Apenas o próprio paciente pode gerenciar as pessoas autorizadas a agendar por ele." />
      ) : (
        <>
        <ScrollView
          contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 100 }]}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag">
          {/* O que a pessoa poderá fazer */}
          <View style={styles.aviso}>
            <Ionicons name="shield-checkmark-outline" size={18} color={t.brandDeep} />
            <Text style={styles.avisoTxt}>
              Você decide o que cada pessoa pode fazer em cada área — de{' '}
              <Text style={styles.avisoForte}>só visualizar</Text> a <Text style={styles.avisoForte}>ver e lançar</Text>.
              Quem você adiciona começa sem acesso a nada.
            </Text>
          </View>

          {/* Lista */}
          <View style={styles.secao}>
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
                  Toque em “Adicionar novas pessoas” e escolha o que cada uma pode fazer por você.
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
                      ) : (
                        <View style={styles.acoes}>
                          {/* Editar: nome, telefone, data e permissões (CPF fica fixo). */}
                          <Pressable
                            style={({ pressed }) => [styles.editar, pressed && styles.editarPressed]}
                            onPress={() => abrirEdicao(r)}
                            hitSlop={6}
                            accessibilityRole="button"
                            accessibilityLabel={`Editar ${r.nome}`}>
                            <Ionicons name="create-outline" size={19} color={t.brandDeep} />
                          </Pressable>

                          {!r.ativo ? (
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
                      )}
                    </View>
                  );
                })}
              </View>
            )}
          </View>
        </ScrollView>

        {/* Botão flutuante para cadastrar (abre o mesmo modal, em modo "adicionar"). */}
        {!carregando && !erro && (
          <Pressable
            style={({ pressed }) => [styles.fab, { bottom: insets.bottom + 16 }, pressed && styles.fabPressed]}
            onPress={abrirAdicao}
            accessibilityRole="button"
            accessibilityLabel="Adicionar novas pessoas">
            <Ionicons name="person-add" size={20} color={t.onBrand} />
            <Text style={styles.fabTxt}>Adicionar novas pessoas</Text>
          </Pressable>
        )}

        {/* Cadastro/edição de pessoa. No adicionar o CPF é editável; no editar fica travado. */}
        <Modal
          visible={edVisivel}
          transparent
          animationType="fade"
          statusBarTranslucent
          onRequestClose={fecharEdicao}>
          <KeyboardAvoidingView style={styles.backdrop} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
            <View style={styles.modalCard}>
              <View style={styles.modalCabecalho}>
                <Text style={styles.modalTitulo}>{adicionando ? 'Adicionar pessoa' : 'Editar pessoa'}</Text>
                <Pressable
                  style={styles.modalFechar}
                  onPress={fecharEdicao}
                  disabled={edSalvando}
                  accessibilityRole="button"
                  accessibilityLabel="Fechar">
                  <Ionicons name="close" size={20} color={t.muted} />
                </Pressable>
              </View>

              <ScrollView
                contentContainerStyle={styles.modalScroll}
                keyboardShouldPersistTaps="handled"
                showsVerticalScrollIndicator={false}>
                <Text style={styles.label}>Nome</Text>
                <TextInput
                  style={styles.input}
                  value={edNome}
                  onChangeText={setEdNome}
                  placeholder="Nome da pessoa"
                  placeholderTextColor={t.muted}
                  autoCapitalize="words"
                  maxLength={120}
                  editable={!edSalvando}
                />

                <Text style={[styles.label, styles.labelEspaco]}>CPF</Text>
                {adicionando ? (
                  <TextInput
                    style={styles.input}
                    value={fmtCpf(edCpf)}
                    onChangeText={(v) => setEdCpf(soDigitos(v).slice(0, 11))}
                    placeholder="000.000.000-00"
                    placeholderTextColor={t.muted}
                    keyboardType="number-pad"
                    inputMode="numeric"
                    maxLength={14}
                    editable={!edSalvando}
                  />
                ) : (
                  <>
                    <View style={styles.inputTravado}>
                      <Text style={styles.inputTravadoTxt}>{fmtCpf(edCpf)}</Text>
                      <Ionicons name="lock-closed" size={15} color={t.muted} />
                    </View>
                    <Text style={styles.cpfNota}>O CPF não pode ser alterado.</Text>
                  </>
                )}

                <Text style={[styles.label, styles.labelEspaco]}>Data de nascimento</Text>
                <TextInput
                  style={styles.input}
                  value={fmtData(edData)}
                  onChangeText={(v) => setEdData(soDigitos(v).slice(0, 8))}
                  placeholder="00/00/0000"
                  placeholderTextColor={t.muted}
                  keyboardType="number-pad"
                  inputMode="numeric"
                  maxLength={10}
                  editable={!edSalvando}
                />

                <Text style={[styles.label, styles.labelEspaco]}>Telefone (com DDD)</Text>
                <TextInput
                  style={styles.input}
                  value={fmtTelefone(edTelefone)}
                  onChangeText={(v) => setEdTelefone(soDigitos(v).slice(0, 11))}
                  placeholder="(11) 98888-1111"
                  placeholderTextColor={t.muted}
                  keyboardType="phone-pad"
                  editable={!edSalvando}
                />

                <Text style={[styles.label, styles.labelEspaco]}>O que esta pessoa pode fazer</Text>
                {adicionando && (
                  <Text style={styles.permAjuda}>
                    Toque para escolher o acesso de cada área. Começa tudo em “Sem acesso”.
                  </Text>
                )}
                <MatrizPermissoes
                  permissoes={edPermissoes}
                  onChange={(f, n) => setEdPermissoes((p) => ({ ...p, [f]: n }))}
                  desabilitado={edSalvando}
                  telaHabilitada={telaHabilitada}
                  styles={styles}
                />
                {adicionando && (
                  <Text style={styles.dica}>
                    A pessoa entra no app com o próprio CPF (mesmo login por SMS) e passa a ver este perfil.
                  </Text>
                )}
              </ScrollView>

              <Pressable
                style={({ pressed }) => [
                  styles.btnAdd,
                  !podeSalvar && styles.btnAddOff,
                  pressed && podeSalvar && styles.btnAddPressed,
                ]}
                onPress={salvar}
                disabled={!podeSalvar}
                accessibilityRole="button"
                accessibilityLabel={adicionando ? 'Adicionar pessoa' : 'Salvar alterações'}
                accessibilityState={{ disabled: !podeSalvar, busy: edSalvando }}>
                {edSalvando ? (
                  <ActivityIndicator size="small" color={t.onBrand} />
                ) : (
                  <Ionicons
                    name={adicionando ? 'person-add-outline' : 'checkmark-circle-outline'}
                    size={18}
                    color={t.onBrand}
                  />
                )}
                <Text style={styles.btnAddTxt}>
                  {edSalvando ? (adicionando ? 'Adicionando…' : 'Salvando…') : adicionando ? 'Adicionar' : 'Salvar'}
                </Text>
              </Pressable>
            </View>
          </KeyboardAvoidingView>
        </Modal>
        </>
      )}
    </View>
  );
}

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
    screen: { flex: 1, backgroundColor: t.bg },
    content: { padding: 20, paddingBottom: 108 },
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

    // Ações de cada pessoa na lista (Editar + situação).
    acoes: { flexDirection: 'row', alignItems: 'center', gap: 8 },
    editar: {
      width: 40,
      height: 40,
      borderRadius: 12,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: t.brandTint,
    },
    editarPressed: { backgroundColor: t.brandTintStrong },

    // Matriz de permissões (uma linha por funcionalidade + botões segmentados de nível).
    permAjuda: { fontSize: 12, color: t.muted, lineHeight: 17, marginTop: 2, marginBottom: 4 },
    matriz: { marginTop: 6 },
    permLinha: { paddingVertical: 12 },
    permLinhaBorda: { borderTopWidth: 1, borderTopColor: t.line },
    permRotulo: { fontSize: 14, fontWeight: '700', color: t.ink, marginBottom: 8 },
    seg: { flexDirection: 'row', gap: 6 },
    segBtn: {
      flex: 1,
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

    // Modal de edição.
    backdrop: {
      flex: 1,
      backgroundColor: alpha(t.brandPine, 0.55),
      alignItems: 'center',
      justifyContent: 'center',
      padding: 24,
    },
    modalCard: {
      width: '100%',
      maxWidth: 420,
      maxHeight: '88%',
      backgroundColor: t.surface,
      borderRadius: 24,
      padding: 22,
    },
    modalCabecalho: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
    modalTitulo: { fontSize: 18, fontWeight: '800', color: t.ink, letterSpacing: -0.3 },
    modalFechar: {
      width: 34,
      height: 34,
      borderRadius: 17,
      alignItems: 'center',
      justifyContent: 'center',
      backgroundColor: t.bg,
    },
    modalScroll: { paddingTop: 12, paddingBottom: 4 },
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

    // Botão flutuante (FAB estendido) para abrir o modal de cadastro.
    fab: {
      position: 'absolute',
      right: 20,
      flexDirection: 'row',
      alignItems: 'center',
      gap: 8,
      height: 54,
      paddingHorizontal: 20,
      borderRadius: 27,
      backgroundColor: t.brand,
      shadowColor: '#000',
      shadowOpacity: 0.18,
      shadowRadius: 12,
      shadowOffset: { width: 0, height: 6 },
      elevation: 6,
    },
    fabPressed: { backgroundColor: t.brandDeep },
    fabTxt: { color: t.onBrand, fontSize: 15, fontWeight: '800' },
  });

/** Fundo bem suave do vermelho de remover (para o estado pressionado). */
function mixSuave(hex: string): string {
  const h = hex.replace('#', '');
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, 0.1)`;
}
