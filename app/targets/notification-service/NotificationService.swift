import UserNotifications

/// Notification Service Extension do "Integra Saúde".
///
/// O iOS executa esta extensão (processo separado) a cada push VISÍVEL com `mutable-content: 1`,
/// INCLUSIVE com o app encerrado/force-quit. O backend manda o push de chat com `mutableContent:true`
/// e, em `data.ack`, um token de ENTREGA assinado (HMAC, curto, escopo único). Aqui a gente só
/// repassa esse token para `POST /push/entregue` — confirmando o 2º "check" (entregue) sem precisar
/// da sessão do paciente, sem App Group e sem Keychain.
///
/// Regras de ouro da NSE:
/// - janela de ~30s; SEMPRE chamar `contentHandler` (também em `serviceExtensionTimeWillExpire`);
/// - iniciar a rede ANTES de chamar `contentHandler` (rede iniciada depois pode não completar);
/// - a notificação é exibida pelo sistema de qualquer forma — se o ACK falhar, o paciente ainda vê.
class NotificationService: UNNotificationServiceExtension {

    /// URL do backend. TestFlight/produção = Railway. Para testar contra um backend local,
    /// troque temporariamente por http://SEU_IP:8080 (a NSE não lê o IP do Metro como o app faz).
    private static let baseUrl = "https://amiable-flow-production-a405.up.railway.app"

    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var bestAttemptContent: UNMutableNotificationContent?

    override func didReceive(_ request: UNNotificationRequest,
                             withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void) {
        self.contentHandler = contentHandler
        self.bestAttemptContent = request.content.mutableCopy() as? UNMutableNotificationContent

        guard let ack = Self.extrairAck(request.content.userInfo) else {
            finalizar()
            return
        }
        // Dispara o ACK e só então entrega a notificação (garante que a rede começou a tempo).
        confirmarEntrega(ack: ack) { [weak self] in
            self?.finalizar()
        }
    }

    override func serviceExtensionTimeWillExpire() {
        // Acabou o tempo da extensão: entrega o que tiver (o POST pode não ter completado).
        finalizar()
    }

    private func finalizar() {
        guard let handler = contentHandler, let content = bestAttemptContent else { return }
        contentHandler = nil
        handler(content)
    }

    /// Extrai `ack` do payload. O Expo costuma aninhar o `data` como JSON-string sob "body" no
    /// userInfo; cobrimos também as outras formas possíveis para robustez entre versões.
    private static func extrairAck(_ userInfo: [AnyHashable: Any]) -> String? {
        // 1) data aninhado em "body" como string JSON (formato típico do Expo).
        if let body = userInfo["body"] as? String,
           let bytes = body.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: bytes) as? [String: Any],
           let ack = json["ack"] as? String {
            return ack
        }
        // 2) "body" já como dicionário.
        if let body = userInfo["body"] as? [String: Any], let ack = body["ack"] as? String {
            return ack
        }
        // 3) data sob "data".
        if let data = userInfo["data"] as? [String: Any], let ack = data["ack"] as? String {
            return ack
        }
        // 4) ack direto no topo.
        if let ack = userInfo["ack"] as? String {
            return ack
        }
        return nil
    }

    private func confirmarEntrega(ack: String, completion: @escaping () -> Void) {
        guard let url = URL(string: Self.baseUrl + "/push/entregue"),
              let corpo = try? JSONSerialization.data(withJSONObject: ["ack": ack]) else {
            completion()
            return
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.timeoutInterval = 8
        req.httpBody = corpo
        URLSession.shared.dataTask(with: req) { _, _, _ in
            completion()
        }.resume()
    }
}
