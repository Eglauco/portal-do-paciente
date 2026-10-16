# Entrega do chat (2º "check" ✓✓) com o app FECHADO

Objetivo: marcar a mensagem da unidade como **entregue** no administrativo mesmo quando o app do
paciente está fechado — sem o paciente abrir nada. WhatsApp-style: ✓ enviado → ✓✓ entregue → ✓✓ lido.

## Como funciona (arquitetura)

1. **Backend** manda o push de chat com `mutableContent:true` e, em `data.ack`, um **token de entrega
   assinado** (HMAC-SHA256, curto, validade 15 min, escopo único "marcar entregue do paciente X no
   inquilino Y"). Segredo dedicado `app.push-ack.secret` (≠ `app.jwt.secret`).
2. **iOS — Notification Service Extension (NSE):** o iOS executa a extensão na CHEGADA de todo push
   visível com `mutable-content`, **inclusive com o app encerrado/force-quit**. A NSE lê o `ack` e faz
   `POST /push/entregue` — sem precisar da sessão do paciente, sem App Group, sem Keychain.
3. **Backend** valida o token, resolve o inquilino pelo próprio token, e marca as mensagens como
   entregues reaproveitando `marcarEntregueTodasDoPaciente` (idempotente) → emite `/topic/chat/{id}/entregue`
   → o administrativo vira ✓✓ ao vivo (o front do admin já escuta esse evento).

## Realidade por plataforma (importante, sem ilusão)

| Estado do app | iOS (NSE) | Android |
|---|---|---|
| Aberto / 1º plano | ✅ já confirmado (listener de 1º plano) | ✅ já confirmado |
| 2º plano / fechado (não force-quit) | ✅ NSE roda → ✓✓ | ⚠️ só ao reabrir |
| Force-quit (deslizado) | ⚠️ NSE **ainda roda** (best-effort) → ✓✓ | ❌ nada roda |
| Force-stop / OEM matador (Samsung/Xiaomi) | — | ❌ impossível (limite do Android) |

- **iOS** é onde o app-fechado é de fato resolvido (NSE roda até force-quit). É o grande diferencial.
- **Android:** por decisão de projeto, **não** fomos pela via *headless* (data-only), porque ela
  trocaria a confiabilidade da própria notificação do paciente por um ACK que, no público-alvo (idosos,
  aparelhos Samsung/Xiaomi que fazem force-stop ao deslizar), quase nunca dispararia. No Android o ✓✓
  sai quando o app volta ao 1º plano (rede de segurança `POST /meu/chats/entregar-pendentes`, já no ar).
  Se um dia quiser cobrir o Android em 2º plano via headless (com os riscos acima), dá pra adicionar
  depois — a fundação do backend (token + endpoint) já serve aos dois.
- **Sempre best-effort:** APNs não garante entrega; sob bateria baixa/carga o iOS pode exibir a
  notificação sem rodar a NSE; a janela da NSE é ~30s. O ✓✓ via reabrir continua como rede de segurança.

## O que já está no código (acumulado local, não commitado)

**Backend**
- `PushAckToken.java` — cunha/verifica o token de entrega (HMAC).
- `PushAckController.java` — `POST /push/entregue` público (autenticado pelo token do push).
- `PushService.java` — no chat, cunha o `ack` e manda `mutableContent:true` (só o chat; via `extrasTopo`).
- `ChatService.marcarEntregueTodasDoPaciente` — agora `@Transactional` (o endpoint público fixa o
  schema ANTES da transação abrir).
- `SecurityConfig` — `POST /push/entregue` liberado (sem Bearer).
- `application.properties` — `app.push-ack.secret` (env `APP_PUSH_ACK_SECRET`).

**App (iOS NSE)**
- `targets/notification-service/expo-target.config.js` — define a extensão (type notification-service).
- `targets/notification-service/NotificationService.swift` — lê `data.ack` e faz o POST.
- `app.json` — plugin `@bacons/apple-targets` ligado.
- `package.json` — dependência `@bacons/apple-targets`.

**App (1º plano / reabrir)** — já pronto e validado antes: `confirmarEntregaTodas()` em
`services/chat.ts`, chamado na lista de conversas e no `_layout.tsx` (push em 1º plano + volta do 2º plano).

## Passos que VOCÊ precisa rodar (não dá pra validar no meu ambiente)

> ⚠️ Depois de ligar o plugin no `app.json`, o `expo start` SÓ funciona após o `npm install`.

1. **Instalar a dependência** (no `app/`):
   ```bash
   npm install
   ```
2. **Gerar a extensão nativa (prebuild iOS):**
   ```bash
   npx expo prebuild -p ios --clean
   ```
   Isso cria o target da NSE no projeto iOS a partir de `targets/notification-service/`.
3. **Build iOS (EAS):** o EAS cria o App ID + provisioning da extensão automaticamente (credenciais
   gerenciadas). **Não precisa de App Group.**
   ```bash
   eas build -p ios --profile development   # ou --profile production p/ TestFlight
   ```
4. **Backend em produção (Railway):** definir a env **`APP_PUSH_ACK_SECRET`** com um segredo forte
   (o default do `application.properties` é só pra dev). Fazer deploy do backend.
5. **Requisitos do EAS:** CocoaPods ≥ 1.16.2, Xcode 16 — as imagens do EAS já têm. (Build local exige
   macOS; via EAS cloud não precisa de Mac.)

## Como testar (no aparelho, do jeito que o usuário real usa)

1. iPhone com o build (TestFlight apontando pro Railway, ou dev build apontando pro mesmo backend do admin).
   > A NSE usa a URL fixa do Railway (`NotificationService.swift`, constante `baseUrl`). Pra testar
   > contra um backend LOCAL, troque temporariamente essa constante pelo `http://SEU_IP:8080`.
2. **Feche o app de verdade** (deslize pra fora / force-quit).
3. Pelo administrativo, mande uma mensagem no chat desse paciente.
4. A notificação aparece no iPhone **e**, em segundos, o administrativo deve virar **✓✓ (entregue)**
   sem você abrir o app no iPhone.
5. Repita com o app só em 2º plano (deve funcionar igual).

> Dica de diagnóstico: a NSE não mostra `console.log`. Para confirmar que o POST chegou, olhe o log do
> backend (uma chamada a `POST /push/entregue`) ou o próprio ✓✓ no admin. Teste SEM o Xcode conectado
> (com debugger o iOS desliga throttles e "engana" o teste).

## Segurança (resumo)

- O token de entrega é HMAC curto, escopo único (só marca entregue), validade 15 min, segredo separado
  do JWT. Se vazar, **não vira sessão** e não lê dados — no máximo re-marca entrega (idempotente).
- O endpoint é público mas inútil sem um token válido assinado pelo backend.
