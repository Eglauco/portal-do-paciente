import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from 'react-native';

import { Brand } from '@/constants/theme';
import { cancelarAgendamento } from '@/services/agendamentos';
import { assinarAtualizacao } from '@/services/atualizacao';
import { LembretePopup as Lembrete, listarPopupsPendentes, reconhecerLembrete } from '@/services/lembretes';

const doisDigitos = (n: number) => String(n).padStart(2, '0');

function dataHoraFmt(iso: string): string {
  const d = new Date(iso);
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}/${d.getFullYear()} às ${doisDigitos(d.getHours())}:${doisDigitos(d.getMinutes())}`;
}

/**
 * Pop-up fixo de lembrete: ao abrir o app (e ao chegar push / voltar ao primeiro
 * plano) busca os lembretes pendentes e mostra um de cada vez. Reaparece até o
 * paciente reconhecer (fechar) ou cancelar o agendamento. Quando o agendamento
 * ainda está no prazo, oferece cancelar direto no pop-up.
 */
export function LembretePopup() {
  const [fila, setFila] = useState<Lembrete[]>([]);
  const [processando, setProcessando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const atual = fila[0] ?? null;
  // Ids já reconhecidos localmente: impede que um refetch (push/foreground) durante
  // a janela do POST ressuscite um pop-up que o paciente acabou de fechar.
  const reconhecidos = useRef<Set<number>>(new Set());

  const carregar = useCallback(() => {
    listarPopupsPendentes()
      .then((lista) => setFila(lista.filter((p) => !reconhecidos.current.has(p.id))))
      .catch(() => {}); // silencioso: sem conexão, tenta de novo no próximo gatilho
  }, []);

  useEffect(() => {
    carregar();
  }, [carregar]);

  // Mesmo barramento das telas: dispara ao receber push ou voltar ao app.
  useEffect(() => {
    const off = assinarAtualizacao(() => carregar());
    return off;
  }, [carregar]);

  const removerReconhecido = (id: number) => {
    reconhecidos.current.add(id);
    setFila((f) => f.filter((p) => p.id !== id));
  };

  const fechar = async () => {
    if (!atual || processando) return;
    setProcessando(true);
    setErro(null);
    try {
      await reconhecerLembrete(atual.id);
    } catch {
      // best-effort: mesmo se falhar em marcar, tira da fila local (some da tela)
    }
    removerReconhecido(atual.id);
    setProcessando(false);
  };

  const cancelar = async () => {
    if (!atual || processando || atual.agendamentoId == null) return;
    const alvo = atual;
    setProcessando(true);
    setErro(null);
    try {
      await cancelarAgendamento(String(alvo.agendamentoId));
    } catch {
      // Cancelamento FALHOU (ex.: prazo passou entre abrir o pop-up e clicar): avisa.
      setErro('Não foi possível cancelar (o prazo pode ter passado). Tente pelo menu de Agendamentos.');
      carregar();
      setProcessando(false);
      return;
    }
    // Cancelou com sucesso: reconhece (best-effort) e some o pop-up — sem erro falso
    // se só o reconhecer falhar.
    try {
      await reconhecerLembrete(alvo.id);
    } catch {
      // segue: o cancelamento já valeu; o id fica em `reconhecidos` para não voltar
    }
    removerReconhecido(alvo.id);
    setProcessando(false);
  };

  if (!atual) return null;

  return (
    <Modal
      visible
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={() => {
        if (!processando) fechar();
      }}>
      <View style={styles.backdrop}>
        <View style={styles.card} accessibilityViewIsModal accessibilityRole="alert">
          <View style={styles.icone}>
            <Ionicons name="alarm" size={26} color={Brand.brandDeep} />
          </View>
          <Text style={styles.titulo}>{atual.titulo}</Text>
          <Text style={styles.mensagem}>{atual.mensagem}</Text>

          {(atual.especialidade || atual.dataHora) && (
            <View style={styles.detalhe}>
              {!!atual.especialidade && (
                <View style={styles.detLinha}>
                  <Ionicons name="medkit-outline" size={15} color={Brand.muted} />
                  <Text style={styles.detTxt}>{atual.especialidade}</Text>
                </View>
              )}
              {!!atual.dataHora && (
                <View style={styles.detLinha}>
                  <Ionicons name="calendar-outline" size={15} color={Brand.muted} />
                  <Text style={styles.detTxt}>{dataHoraFmt(atual.dataHora)}</Text>
                </View>
              )}
            </View>
          )}

          {!!erro && (
            <View style={styles.erroBox}>
              <Ionicons name="alert-circle" size={16} color="#B23B4E" />
              <Text style={styles.erroTxt}>{erro}</Text>
            </View>
          )}

          {atual.podeCancelar && (
            <Pressable style={[styles.btnCancelar, processando && styles.desativado]} onPress={cancelar} disabled={processando}>
              {processando ? (
                <ActivityIndicator color="#B23B4E" />
              ) : (
                <>
                  <Ionicons name="close-circle-outline" size={18} color="#B23B4E" />
                  <Text style={styles.btnCancelarTxt}>Cancelar agendamento</Text>
                </>
              )}
            </Pressable>
          )}

          <Pressable style={[styles.btnOk, processando && styles.desativado]} onPress={fechar} disabled={processando}>
            <Text style={styles.btnOkTxt}>Ok, entendi</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(7,46,43,0.55)', alignItems: 'center', justifyContent: 'center', padding: 24 },
  card: { width: '100%', maxWidth: 400, backgroundColor: Brand.surface, borderRadius: 24, padding: 22, alignItems: 'center' },
  icone: {
    width: 56, height: 56, borderRadius: 18, backgroundColor: '#E7F3EF',
    alignItems: 'center', justifyContent: 'center', marginBottom: 12,
  },
  titulo: { fontSize: 18, fontWeight: '800', color: Brand.ink, textAlign: 'center', letterSpacing: -0.3 },
  mensagem: { fontSize: 14.5, color: '#40514C', textAlign: 'center', lineHeight: 21, marginTop: 8 },
  detalhe: {
    width: '100%', backgroundColor: Brand.bg, borderRadius: 14, borderWidth: 1, borderColor: Brand.line,
    padding: 12, marginTop: 14, gap: 6,
  },
  detLinha: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  detTxt: { fontSize: 13, color: '#40514C' },
  erroBox: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 12 },
  erroTxt: { flex: 1, fontSize: 12.5, color: '#B23B4E', lineHeight: 17 },
  btnCancelar: {
    width: '100%', flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7,
    height: 50, borderRadius: 14, marginTop: 16, borderWidth: 1.5, borderColor: '#F0C6CD', backgroundColor: '#FBE9EC',
  },
  btnCancelarTxt: { color: '#B23B4E', fontSize: 14.5, fontWeight: '800' },
  btnOk: {
    width: '100%', alignItems: 'center', justifyContent: 'center',
    height: 50, borderRadius: 14, marginTop: 10, backgroundColor: Brand.brand,
  },
  btnOkTxt: { color: '#fff', fontSize: 15, fontWeight: '800' },
  desativado: { opacity: 0.6 },
});
