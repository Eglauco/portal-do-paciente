import { Ionicons } from '@expo/vector-icons';
import { forwardRef, memo, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';

import { AvatarPaciente } from '@/components/avatar-paciente';
import { type Tema, useTema } from '@/hooks/use-tema';

export interface ComentarioInputHandle {
  focus: () => void;
}

interface Props {
  autorIniciais: string;
  /** Envia o comentário/resposta; retorna true se deu certo (aí o campo é limpo). */
  onEnviar: (texto: string) => Promise<boolean>;
  /** Nome do autor sendo respondido; quando definido, mostra a faixa "Respondendo a …". */
  respondendoNome?: string | null;
  onCancelarResposta?: () => void;
  paddingBottom?: number;
}

/**
 * Barra de "Deixe um comentário…". Mantém o texto em estado local para que
 * digitar NÃO re-renderize a tela toda (evita a imagem piscar na web).
 * O foco é acionado imperativamente pelo pai (via ref) ao tocar em "Responder".
 */
export const ComentarioInput = memo(
  forwardRef<ComentarioInputHandle, Props>(function ComentarioInput(
    { autorIniciais, onEnviar, respondendoNome, onCancelarResposta, paddingBottom = 10 },
    ref,
  ) {
    const t = useTema();
    const styles = useMemo(() => criarEstilos(t), [t]);
    const [texto, setTexto] = useState('');
    const [enviando, setEnviando] = useState(false);
    const campoRef = useRef<TextInput>(null);
    const podeEnviar = !!texto.trim() && !enviando;

    useImperativeHandle(ref, () => ({ focus: () => campoRef.current?.focus() }), []);

    const enviar = async () => {
      const conteudo = texto.trim();
      if (!conteudo || enviando) return;
      setEnviando(true);
      const ok = await onEnviar(conteudo);
      setEnviando(false);
      if (ok) setTexto('');
    };

    return (
      <View style={styles.container}>
        {respondendoNome ? (
          <View style={styles.faixa}>
            <Text style={styles.faixaTxt} numberOfLines={1}>
              Respondendo a <Text style={styles.faixaNome}>{respondendoNome}</Text>
            </Text>
            <Pressable onPress={onCancelarResposta} hitSlop={8} accessibilityLabel="Cancelar resposta">
              <Ionicons name="close" size={18} color={t.muted} />
            </Pressable>
          </View>
        ) : null}
        <View style={[styles.input, { paddingBottom }]}>
          <AvatarPaciente
            iniciais={autorIniciais}
            tamanho={34}
            estiloCirculo={styles.inputAvatar}
            estiloTexto={styles.inputAvatarTxt}
            estiloFoto={styles.inputAvatarFoto}
          />
          <TextInput
            ref={campoRef}
            style={styles.campo}
            value={texto}
            onChangeText={setTexto}
            placeholder={respondendoNome ? 'Escreva uma resposta…' : 'Deixe um comentário…'}
            placeholderTextColor="#9AAAA5"
            multiline
          />
          <Pressable
            style={[styles.enviar, !podeEnviar && styles.enviarDesativado]}
            onPress={enviar}
            disabled={!podeEnviar}
            accessibilityLabel={respondendoNome ? 'Enviar resposta' : 'Enviar comentário'}>
            {enviando ? <ActivityIndicator color="#fff" size="small" /> : <Ionicons name="send" size={18} color="#fff" />}
          </Pressable>
        </View>
      </View>
    );
  }),
);

const criarEstilos = (t: Tema) =>
  StyleSheet.create({
  container: {
    backgroundColor: t.surface,
    borderTopWidth: 1,
    borderTopColor: t.line,
  },
  faixa: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: t.line,
    backgroundColor: t.brandTint,
  },
  faixaTxt: { flex: 1, fontSize: 12.5, color: t.muted },
  faixaNome: { fontWeight: '700', color: t.ink },
  input: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
    paddingHorizontal: 12,
    paddingTop: 10,
    backgroundColor: t.surface,
  },
  inputAvatar: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: t.brandDeep,
    marginBottom: 4,
  },
  inputAvatarTxt: { color: t.onBrand, fontSize: 12, fontWeight: '800' },
  inputAvatarFoto: { marginBottom: 4 },
  campo: {
    flex: 1,
    minHeight: 42,
    maxHeight: 110,
    backgroundColor: t.bg,
    borderWidth: 1,
    borderColor: t.line,
    borderRadius: 22,
    paddingHorizontal: 16,
    paddingVertical: Platform.OS === 'ios' ? 10 : 6,
    fontSize: 15,
    color: t.ink,
  },
  enviar: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: t.brand,
  },
  enviarDesativado: { opacity: 0.5 },
});
