// Config plugin (Android): registra um FirebaseMessagingService NATIVO (Kotlin) que confirma a
// entrega do chat (2o "check" ✓✓) com o app MORTO — direto em Kotlin, SEM JS/HeadlessJS. Por isso
// funciona na New Architecture (bridgeless), onde o headless do Expo/rnfirebase NÃO roda
// ("Cannot start headless task, CatalystInstance not available").
//
// O serviço ESTENDE o ExpoFirebaseMessagingService: chama super (mantém a notificação visível + a
// task quando o app está vivo) e, além disso, extrai o ack (o Expo entrega o data do usuário como
// JSON na chave "body" do FCM — ver RemoteMessageSerializer) e faz um POST em <base>/push/entregue.
// A <base> NÃO é fixa: o serviço lê a URL do backend de um arquivo (ack-base.txt) que o app grava a
// cada abertura (services/ack-nativo.ts) — assim segue o MESMO backend do app (LOCAL em dev, Railway
// em produção); se o arquivo faltar, usa o fallback compilado (produção). Registrado com a
// intent-filter em priority 0 (> -1 do Expo), então vence o slot único de FirebaseMessagingService
// do Android. iOS não é afetado (lá a entrega morta é via NSE, que ainda aponta p/ produção).
const {
  withDangerousMod,
  withAndroidManifest,
  withAppBuildGradle,
  AndroidConfig,
} = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

// Versao do firebase-messaging que o expo-notifications usa (android/build.gradle) — casamos para
// nao conflitar. O expo-notifications o declara como `implementation`, entao as classes
// (FirebaseMessagingService, RemoteMessage) NAO vazam pro classpath de compilacao do app; como o
// nosso .kt as referencia diretamente, precisamos adiciona-las ao modulo do app.
const FIREBASE_MESSAGING = 'com.google.firebase:firebase-messaging:24.0.1';

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
  // Fallback COMPILADO (producao/Railway). Em runtime preferimos a URL gravada pelo app no arquivo
  // abaixo, para o servico nativo seguir o MESMO backend que o app usa (local em dev, Railway em prod).
  private val fallbackBaseUrl = "${API_URL}"
  private val arquivoBase = "ack-base.txt" // casa com o lado JS: documentDirectory == context.filesDir

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    // Primeiro a confirmacao nativa (garantida mesmo que o super tente o headless e falhe na New Arch).
    confirmarEntrega(remoteMessage)
    super.onMessageReceived(remoteMessage)
  }

  /**
   * Backend que o app esta usando, lido do arquivo que o JS grava a cada abertura (em dev, o IP do
   * notebook; em producao, o Railway). Cai no fallback compilado se o arquivo nao existir ou estiver vazio.
   */
  private fun baseUrl(): String {
    return try {
      val arquivo = java.io.File(applicationContext.filesDir, arquivoBase)
      val txt = if (arquivo.exists()) arquivo.readText(Charsets.UTF_8).trim().trimEnd('/') else ""
      if (txt.isNotEmpty()) txt else fallbackBaseUrl
    } catch (e: Exception) {
      fallbackBaseUrl
    }
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
      val base = baseUrl()
      Thread {
        try {
          val conn = URL(base + "/push/entregue").openConnection() as HttpURLConnection
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
  // 0) Adiciona o firebase-messaging ao classpath de COMPILACAO do app (bloco dependencies extra;
  //    o Gradle acumula multiplos blocos). Sem isso o .kt nao compila (Cannot access RemoteMessage).
  config = withAppBuildGradle(config, (cfg) => {
    if (!cfg.modResults.contents.includes('firebase-messaging')) {
      cfg.modResults.contents += `\ndependencies {\n    implementation '${FIREBASE_MESSAGING}'\n}\n`;
    }
    return cfg;
  });

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
