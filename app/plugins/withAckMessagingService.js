// Config plugin (Android): registra um FirebaseMessagingService NATIVO (Kotlin) que confirma a
// entrega do chat (2o "check" ✓✓) com o app MORTO — direto em Kotlin, SEM JS/HeadlessJS. Por isso
// funciona na New Architecture (bridgeless), onde o headless do Expo/rnfirebase NÃO roda
// ("Cannot start headless task, CatalystInstance not available").
//
// O serviço ESTENDE o ExpoFirebaseMessagingService: chama super (mantém a notificação visível + a
// task quando o app está vivo) e, além disso, extrai o ack (o Expo entrega o data do usuário como
// JSON na chave "body" do FCM — ver RemoteMessageSerializer) e faz um POST em /push/entregue.
// Registrado com a intent-filter em priority 0 (> -1 do Expo), então vence o slot único de
// FirebaseMessagingService do Android. iOS não é afetado (lá a entrega morta é via NSE).
const { withDangerousMod, withAndroidManifest, AndroidConfig } = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

const SERVICE_CLASS = 'AckFirebaseMessagingService';
const API_URL = 'https://amiable-flow-production-a405.up.railway.app';

function kotlinSource(packageName) {
  return `package ${packageName}

import com.google.firebase.messaging.RemoteMessage
import expo.modules.notifications.service.ExpoFirebaseMessagingService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Confirma a entrega do chat (2o check) com o app MORTO, NATIVAMENTE (sem JS/HeadlessJS), por isso
 * funciona na New Architecture. Estende o servico do expo-notifications e chama super() para manter
 * a notificacao visivel e a task-quando-vivo; alem disso extrai o ack e faz o POST em /push/entregue.
 */
class ${SERVICE_CLASS} : ExpoFirebaseMessagingService() {
  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    // Primeiro a confirmacao nativa (garantida mesmo que o super tente o headless e falhe na New Arch).
    confirmarEntrega(remoteMessage)
    super.onMessageReceived(remoteMessage)
  }

  private fun confirmarEntrega(remoteMessage: RemoteMessage) {
    try {
      val data = remoteMessage.data
      // O Expo Push entrega o data do usuario como JSON na chave "body"; cobrimos tambem o caso direto.
      var ack: String? = data["ack"]
      if (ack.isNullOrEmpty()) {
        val body = data["body"]
        if (!body.isNullOrEmpty()) {
          ack = try { JSONObject(body).optString("ack", "") } catch (e: Exception) { null }
        }
      }
      val token: String = ack?.takeIf { it.isNotEmpty() } ?: return
      Thread {
        try {
          val conn = URL("${API_URL}/push/entregue").openConnection() as HttpURLConnection
          conn.requestMethod = "POST"
          conn.setRequestProperty("Content-Type", "application/json")
          conn.doOutput = true
          conn.connectTimeout = 10000
          conn.readTimeout = 10000
          conn.outputStream.use { it.write(("{\\"ack\\":\\"" + token + "\\"}").toByteArray(Charsets.UTF_8)) }
          conn.responseCode // dispara a requisicao
          conn.disconnect()
        } catch (e: Exception) {
          // best-effort: se falhar, o 2o check ainda sai ao reabrir
        }
      }.start()
    } catch (e: Exception) {
      // nunca derruba o recebimento da mensagem
    }
  }
}
`;
}

const withAckMessagingService = (config) => {
  // 1) Escreve o .kt no projeto Android gerado (CNG) durante o prebuild.
  config = withDangerousMod(config, [
    'android',
    (cfg) => {
      const pkg = cfg.android && cfg.android.package;
      if (!pkg) {
        throw new Error('withAckMessagingService: android.package ausente no app config');
      }
      const dir = path.join(
        cfg.modRequest.platformProjectRoot,
        'app',
        'src',
        'main',
        'java',
        ...pkg.split('.'),
      );
      fs.mkdirSync(dir, { recursive: true });
      fs.writeFileSync(path.join(dir, `${SERVICE_CLASS}.kt`), kotlinSource(pkg), 'utf8');
      return cfg;
    },
  ]);

  // 2) Registra o service no AndroidManifest (priority 0 > -1 do Expo -> vence o slot do FCM).
  config = withAndroidManifest(config, (cfg) => {
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(cfg.modResults);
    if (!Array.isArray(app.service)) {
      app.service = [];
    }
    const name = `.${SERVICE_CLASS}`;
    const jaRegistrado = app.service.some((s) => s && s.$ && s.$['android:name'] === name);
    if (!jaRegistrado) {
      app.service.push({
        $: { 'android:name': name, 'android:exported': 'false' },
        'intent-filter': [
          {
            action: [{ $: { 'android:name': 'com.google.firebase.MESSAGING_EVENT' } }],
          },
        ],
      });
    }
    return cfg;
  });

  return config;
};

module.exports = withAckMessagingService;
