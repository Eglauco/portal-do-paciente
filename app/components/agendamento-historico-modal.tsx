import { Ionicons } from '@expo/vector-icons';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { Brand } from '@/constants/theme';
import { AgendamentoLog, listarLogsAgendamento } from '@/services/agendamentos';

interface Props {
  visivel: boolean;
  /** Agendamento cujo histórico será exibido; null fecha o modal. */
  agendamentoId: string | null;
  onFechar: () => void;
}

const doisDigitos = (n: number) => String(n).padStart(2, '0');

/** "dd/MM/yyyy · HH:mm" a partir do ISO (hora local do aparelho). */
function dataHora(iso: string): string {
  const d = new Date(iso);
  return `${doisDigitos(d.getDate())}/${doisDigitos(d.getMonth() + 1)}/${d.getFullYear()} · ${doisDigitos(d.getHours())}:${doisDigitos(d.getMinutes())}`;
}

/** Frase de quem fez a troca; responsável ganha destaque (marcador âmbar). */
function autoria(log: AgendamentoLog): { texto: string; responsavel: boolean } {
  // Decide o marcador (âmbar) pelo AUTOR, não pela presença do nome: um responsável
  // removido do cadastro zera o responsavel_id (FK SET NULL), mas a ação continua
  // sendo dele — cai num rótulo genérico mantendo o destaque de responsável.
  if (log.autor === 'RESPONSAVEL') {
    return {
      texto: log.responsavelNome ? `por ${log.responsavelNome} (responsável)` : 'por um responsável',
      responsavel: true,
    };
  }
  if (log.autor === 'PACIENTE') {
    return { texto: 'pelo paciente', responsavel: false };
  }
  // UNIDADE (atendente do back-office) — mostra o nome quando houver.
  return { texto: log.usuarioNome ? `pela unidade · ${log.usuarioNome}` : 'pela unidade', responsavel: false };
}

export function AgendamentoHistoricoModal({ visivel, agendamentoId, onFechar }: Props) {
  const [logs, setLogs] = useState<AgendamentoLog[]>([]);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState(false);

  // Reset SÍNCRONO ao trocar de agendamento (ainda no render, antes do paint): sem isso
  // o modal — que fica sempre montado — pintaria por um frame os dados/vazio da abertura
  // anterior antes de o efeito ligar o loading (com fade, vira "fantasma" perceptível).
  const [idAnterior, setIdAnterior] = useState<string | null>(agendamentoId);
  if (agendamentoId !== idAnterior) {
    setIdAnterior(agendamentoId);
    setLogs([]);
    setErro(false);
    setCarregando(!!agendamentoId);
  }

  useEffect(() => {
    if (!visivel || !agendamentoId) return;
    let ativo = true;
    setCarregando(true);
    setErro(false);
    listarLogsAgendamento(agendamentoId)
      .then((dados) => {
        if (ativo) setLogs(dados);
      })
      .catch(() => {
        if (ativo) setErro(true);
      })
      .finally(() => {
        if (ativo) setCarregando(false);
      });
    return () => {
      ativo = false;
    };
  }, [visivel, agendamentoId]);

  return (
    <Modal visible={visivel} transparent animationType="fade" onRequestClose={onFechar}>
      <View style={styles.backdrop}>
        <View style={styles.card}>
          <View style={styles.cabecalho}>
            <Text style={styles.titulo}>Histórico de status</Text>
            <Pressable style={styles.fechar} onPress={onFechar} accessibilityRole="button" accessibilityLabel="Fechar">
              <Ionicons name="close" size={20} color={Brand.muted} />
            </Pressable>
          </View>
          <Text style={styles.subtitulo}>Quem realizou cada mudança de status deste agendamento.</Text>

          {carregando ? (
            <View style={styles.estado}>
              <ActivityIndicator color={Brand.brand} />
              <Text style={styles.estadoTxt}>Carregando histórico…</Text>
            </View>
          ) : erro ? (
            <View style={styles.estado}>
              <Ionicons name="cloud-offline-outline" size={26} color={Brand.muted} />
              <Text style={styles.estadoTxt}>Não foi possível carregar o histórico.</Text>
            </View>
          ) : logs.length === 0 ? (
            <View style={styles.estado}>
              <Ionicons name="time-outline" size={26} color={Brand.muted} />
              <Text style={styles.estadoTxt}>Nenhuma mudança de status registrada ainda.</Text>
            </View>
          ) : (
            <ScrollView style={styles.lista} contentContainerStyle={styles.listaContent}>
              {logs.map((log, i) => {
                const quem = autoria(log);
                const ultimo = i === logs.length - 1;
                return (
                  <View key={log.id} style={styles.item}>
                    <View style={styles.rail}>
                      <View style={[styles.dot, quem.responsavel && styles.dotResp]} />
                      {!ultimo && <View style={styles.linha} />}
                    </View>
                    <View style={styles.conteudo}>
                      <Text style={styles.status}>
                        {log.statusAnterior == null ? 'Agendamento criado' : log.statusNovoDescricao}
                      </Text>
                      {log.statusAnterior != null && (
                        <Text style={styles.transicao}>
                          {log.statusAnteriorDescricao} → {log.statusNovoDescricao}
                        </Text>
                      )}
                      {log.statusAnterior == null && log.statusNovoDescricao && (
                        <Text style={styles.transicao}>{log.statusNovoDescricao}</Text>
                      )}
                      <View style={styles.quemLinha}>
                        {quem.responsavel && (
                          <Ionicons name="people-outline" size={13} color="#8A5A00" />
                        )}
                        <Text style={[styles.quem, quem.responsavel && styles.quemResp]}>{quem.texto}</Text>
                      </View>
                      <Text style={styles.data}>{dataHora(log.criadoEm)}</Text>
                    </View>
                  </View>
                );
              })}
            </ScrollView>
          )}

          <Pressable style={styles.btnFechar} onPress={onFechar}>
            <Text style={styles.btnFecharTxt}>Fechar</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
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
    padding: 22,
  },
  cabecalho: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  titulo: { fontSize: 18, fontWeight: '800', color: Brand.ink, letterSpacing: -0.3 },
  fechar: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.bg,
  },
  subtitulo: { fontSize: 13, color: Brand.muted, marginTop: 4, marginBottom: 14, lineHeight: 18 },

  estado: { alignItems: 'center', justifyContent: 'center', paddingVertical: 32, gap: 10 },
  estadoTxt: { fontSize: 13.5, color: Brand.muted, textAlign: 'center', paddingHorizontal: 16, lineHeight: 19 },

  lista: { flexGrow: 0 },
  listaContent: { paddingVertical: 2 },
  item: { flexDirection: 'row', gap: 12 },
  rail: { alignItems: 'center', width: 14 },
  dot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: Brand.brand,
    marginTop: 3,
  },
  dotResp: { backgroundColor: '#B98900', borderWidth: 2, borderColor: '#F2E3C4' },
  linha: { flex: 1, width: 2, backgroundColor: Brand.line, marginTop: 2, minHeight: 14 },
  conteudo: { flex: 1, paddingBottom: 18 },
  status: { fontSize: 14.5, fontWeight: '800', color: Brand.ink },
  transicao: { fontSize: 12.5, color: Brand.muted, marginTop: 1 },
  quemLinha: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 4 },
  quem: { fontSize: 13, color: '#40514C', fontWeight: '600' },
  quemResp: { color: '#8A5A00', fontWeight: '700' },
  data: { fontSize: 11.5, color: Brand.muted, marginTop: 3 },

  btnFechar: {
    marginTop: 8,
    height: 46,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.bg,
  },
  btnFecharTxt: { fontSize: 14.5, fontWeight: '700', color: Brand.brandDeep },
});
