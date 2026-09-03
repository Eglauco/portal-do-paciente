import { Ionicons } from '@expo/vector-icons';
import { useMemo, useRef, useState } from 'react';
import {
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

import { Brand } from '@/constants/theme';

export interface ItemSelect {
  id: number;
  nome: string;
}

interface Props {
  itens: ItemSelect[];
  valor: number | null;
  onSelecionar: (id: number) => void;
  placeholder?: string;
  /** Título do modal e rótulo do campo para leitores de tela. */
  rotulo?: string;
  placeholderBusca?: string;
  /** Não abre o modal (ex.: uma única opção já selecionada). Mostra o valor sem seta. */
  desabilitado?: boolean;
  /** Avisa a tela quando o modal abre/fecha (para ela ignorar o teclado da busca). */
  onAbertoChange?: (aberto: boolean) => void;
}

/** Remove acentos e caixa para uma busca tolerante (ex.: "sao" acha "São"). */
function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

/**
 * Campo de seleção única com busca por digitação (substitui listas longas de
 * opções). Segue o padrão de modal do app (statusBarTranslucent +
 * KeyboardAvoidingView) para o teclado da busca não cobrir a lista no Android
 * edge-to-edge. Fecha pelo X ou pelo voltar do sistema.
 */
export function SelectBusca({
  itens,
  valor,
  onSelecionar,
  placeholder = 'Selecione',
  rotulo = 'Selecione uma opção',
  placeholderBusca = 'Buscar…',
  desabilitado = false,
  onAbertoChange,
}: Props) {
  const [aberto, setAberto] = useState(false);
  const [busca, setBusca] = useState('');
  const inputRef = useRef<TextInput>(null);

  const selecionado = itens.find((i) => i.id === valor) ?? null;

  const filtrados = useMemo(() => {
    const q = normalizar(busca);
    if (!q) return itens;
    return itens.filter((i) => normalizar(i.nome).includes(q));
  }, [itens, busca]);

  const abrir = () => {
    if (desabilitado) return;
    setBusca('');
    setAberto(true);
    onAbertoChange?.(true);
  };

  const fechar = () => {
    setAberto(false);
    onAbertoChange?.(false);
  };

  const escolher = (id: number) => {
    onSelecionar(id);
    fechar();
  };

  return (
    <>
      <Pressable
        onPress={abrir}
        disabled={desabilitado}
        style={[styles.campo, desabilitado && styles.campoDesabilitado]}
        accessibilityRole="button"
        accessibilityState={{ disabled: desabilitado, expanded: aberto }}
        accessibilityLabel={`${rotulo}: ${selecionado ? selecionado.nome : 'nenhuma opção selecionada'}`}>
        <Text style={[styles.campoTexto, !selecionado && styles.campoPlaceholder]} numberOfLines={1}>
          {selecionado ? selecionado.nome : placeholder}
        </Text>
        {!desabilitado && <Ionicons name="chevron-down" size={20} color={Brand.muted} />}
      </Pressable>

      <Modal
        visible={aberto}
        transparent
        animationType="fade"
        statusBarTranslucent
        onRequestClose={fechar}
        // autoFocus dentro do Modal pode não abrir o teclado no Android (a janela do
        // Modal ainda não ganhou foco); focar no onShow (com um respiro) é confiável.
        onShow={() => setTimeout(() => inputRef.current?.focus(), 50)}>
        <KeyboardAvoidingView style={styles.backdrop} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
          <View style={styles.card}>
            <View style={styles.cabecalho}>
              <Text style={styles.titulo} numberOfLines={1}>
                {rotulo}
              </Text>
              <Pressable
                onPress={fechar}
                style={styles.fechar}
                accessibilityRole="button"
                accessibilityLabel="Fechar">
                <Ionicons name="close" size={20} color={Brand.muted} />
              </Pressable>
            </View>

            <View style={styles.buscaBox}>
              <Ionicons name="search" size={18} color={Brand.muted} />
              <TextInput
                ref={inputRef}
                style={styles.buscaInput}
                value={busca}
                onChangeText={setBusca}
                placeholder={placeholderBusca}
                placeholderTextColor="#9AAAA5"
                autoCorrect={false}
                returnKeyType="search"
                accessibilityLabel={placeholderBusca}
              />
              {busca.length > 0 && (
                <Pressable onPress={() => setBusca('')} accessibilityRole="button" accessibilityLabel="Limpar busca">
                  <Ionicons name="close-circle" size={18} color={Brand.muted} />
                </Pressable>
              )}
            </View>

            <ScrollView
              style={styles.lista}
              keyboardShouldPersistTaps="handled"
              showsVerticalScrollIndicator={false}>
              {filtrados.length === 0 ? (
                <Text style={styles.vazio}>Nenhuma opção encontrada.</Text>
              ) : (
                filtrados.map((item) => {
                  const ativo = item.id === valor;
                  return (
                    <Pressable
                      key={item.id}
                      onPress={() => escolher(item.id)}
                      style={({ pressed }) => [styles.opcao, pressed && styles.opcaoPressed]}
                      accessibilityRole="radio"
                      accessibilityState={{ selected: ativo }}>
                      <Text style={[styles.opcaoTexto, ativo && styles.opcaoTextoAtivo]} numberOfLines={2}>
                        {item.nome}
                      </Text>
                      {ativo && <Ionicons name="checkmark" size={20} color={Brand.brand} />}
                    </Pressable>
                  );
                })
              )}
            </ScrollView>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  campo: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    minHeight: 52,
    paddingHorizontal: 14,
    borderWidth: 1,
    borderColor: Brand.line,
    borderRadius: 14,
    backgroundColor: Brand.surface,
  },
  campoDesabilitado: { backgroundColor: Brand.bg },
  campoTexto: { flex: 1, fontSize: 15, color: Brand.ink },
  campoPlaceholder: { color: '#9AAAA5' },

  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(7,46,43,0.55)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    maxWidth: 400,
    maxHeight: '80%',
    backgroundColor: Brand.surface,
    borderRadius: 24,
    padding: 18,
  },
  cabecalho: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 14 },
  titulo: { flex: 1, fontSize: 17, fontWeight: '800', color: Brand.ink, letterSpacing: -0.3 },
  fechar: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.bg,
  },

  buscaBox: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    height: 46,
    paddingHorizontal: 12,
    borderWidth: 1,
    borderColor: Brand.line,
    borderRadius: 12,
    backgroundColor: Brand.bg,
    marginBottom: 10,
  },
  buscaInput: { flex: 1, fontSize: 15, color: Brand.ink, paddingVertical: 0 },

  lista: { flexShrink: 1 },
  vazio: { fontSize: 14, color: Brand.muted, textAlign: 'center', paddingVertical: 24 },
  opcao: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 14,
    paddingHorizontal: 12,
    borderRadius: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#F0F4F2',
  },
  opcaoPressed: { backgroundColor: '#EEF3F1' },
  opcaoTexto: { flex: 1, fontSize: 15, color: Brand.ink },
  opcaoTextoAtivo: { fontWeight: '700', color: Brand.brandDeep },
});
