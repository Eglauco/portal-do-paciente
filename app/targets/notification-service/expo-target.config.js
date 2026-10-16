/** @type {import('@bacons/apple-targets/app.plugin').Config} */
// Notification Service Extension (iOS): roda na CHEGADA de cada push de chat (mesmo com o app
// encerrado/force-quit, pois o push é visível + mutable-content), lê o token de entrega assinado
// que veio em data.ack e o ecoa para POST /push/entregue — confirmando o 2º "check" (entregue).
//
// NÃO precisa de App Group nem Keychain compartilhado: a extensão só repassa um token opaco de
// escopo único (não lê a sessão do paciente). EAS gera o bundle/provisioning da extensão sozinho.
//
// Obs.: ESM e TypeScript NÃO são suportados neste arquivo de config (precisa ser CommonJS).
module.exports = {
  type: "notification-service",
  // Casa com o deployment target do app Expo SDK 54 (iOS 15.1). Ajuste se subir o mínimo do app.
  deploymentTarget: "15.1",
};
