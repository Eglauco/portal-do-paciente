import { TextDecoder, TextEncoder } from 'text-encoding';

/**
 * O Hermes (React Native) não inclui TextEncoder/TextDecoder, que o
 * @stomp/stompjs usa para (des)serializar os frames STOMP. Sem isso, o
 * cliente abre o WebSocket mas nunca conclui o handshake STOMP.
 * Importar este arquivo o mais cedo possível (antes de usar o STOMP).
 */
const alvo = globalThis as unknown as {
  TextEncoder?: unknown;
  TextDecoder?: unknown;
};

if (typeof alvo.TextEncoder === 'undefined') {
  alvo.TextEncoder = TextEncoder;
}
if (typeof alvo.TextDecoder === 'undefined') {
  alvo.TextDecoder = TextDecoder;
}
