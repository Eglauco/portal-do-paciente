# Plano — Subdomínio, App por Cliente (A-baked) e Identidade Central do Paciente

> Evolução do multi-inquilino (Design B) do POP. Documento de referência da **regra de
> negócio fechada** + **arquitetura** + **plano de desenvolvimento por fases**.
> Base de código: back (Spring Boot `com.example.pop`), front (Angular 20 SSR, admin/back-office),
> app (Expo "Integra Saúde", paciente). Prod no Railway, já no Design B.

---

## 1. Objetivo

1. **App personalizado por cliente (A-baked):** cada clínica tem o seu próprio app nas lojas,
   com marca própria desde a tela de abertura.
2. **Subdomínio por cliente no web:** `clinicaX.trilharsaude.app.br` personaliza o **login do back-office
   (equipe/admin)** antes do login. (O paciente NÃO usa web — só app.)
3. **Identidade central do paciente:** um login único (CPF + senha) que funciona em todas as
   clínicas do paciente, sem recadastro nem múltiplas senhas — mantendo o **dado clínico isolado
   por inquilino**.

---

## 2. Regra de negócio (FECHADA)

### 2.1. Modelo de app — A-baked (siloado)
- Um app por clínica, marca própria. **Um codebase, N configs de build.**
- **Cada app é um silo:** o app da Clínica 1 mostra **só** a Clínica 1. Não vira hub multi-clínica.
  - *Por quê:* se o app de uma clínica mostrasse as outras, mataria o produto vendido às demais,
    nenhuma clínica quer exibir concorrente no próprio app, e colapsaria o A-baked em A-picker.
  - A dor de "baixar outro app" é mitigada pelo **login central** (2º app = 1 instalação + 1 login,
    depois biometria) + deep link na SMS. Paciente multi-clínica é minoria.
- Publicar cada app **na conta Apple/Google do próprio cliente** (evita a diretriz 4.2.6/4.3 de
  apps-clone da Apple; o cliente vira dono do app).

### 2.2. Subdomínio (web/admin) — domínio dedicado `trilharsaude.app.br`
- **Domínio dedicado só pros portais das clínicas:** `trilharsaude.app.br` (comprado no registro.br).
  A landing do produto fica num domínio **separado** (`trilharsaude.com.br`, na TurboCloud) — os dois
  não se encostam.
- `clinicaX.trilharsaude.app.br` → personaliza o **login do admin** daquela clínica.
- **Apex** (`trilharsaude.app.br` sem subdomínio) → portal do **super-admin**, marca neutra da plataforma.
- `inquilino.subdominio` (label DNS, único) resolve o inquilino **pré-login**; **pós-login o claim
  `inq` do JWT continua mandando** (subdomínio é só branding pré-login, é spoofável e só afeta
  tela pública).

### 2.3. Identidade central do paciente
- **Credencial única:** um CPF + uma senha (PIN) valem em qualquer app/clínica.
- **O que vive no `public` (mínimo):** CPF, nome, data de nascimento, **1 telefone** (o do OTP) e a
  **credencial** (hash do PIN, contador de tentativas/lockout, estado do OTP).
- **O que fica por-clínica (schema do inquilino):** todo o **dado clínico** (agendamentos,
  prontuário, TCLE, chat, documentos) + o **cadastro/perfil clínico** do paciente (inclusive
  telefones de contato de cada clínica). **Dado clínico NUNCA é centralizado** (LGPD, isolamento).
- **Vínculo 1→N:** uma identidade central ↔ várias clínicas (hoje `paciente_login` é CPF→1 inquilino;
  vira tabela de vínculo identidade↔inquilino).
- **Auto-vínculo (regra 4):** quando uma clínica cadastra/agenda um CPF que já existe central,
  **vincula automaticamente, sem tela de autorização** (base legal = relação de tratamento).
  Coberto por um **aviso único de plataforma** no 1º uso / nos termos.
- **Responsável / perfis:** a identidade central é **só o login**. "Quem cuida de quem" fica
  **por-clínica**, reaproveitando o "trocar-perfil" existente, escopado à clínica do app.
- **Exclusão de conta:** excluir na Clínica X = **desvincula** da Clínica X (inativa o dado clínico
  lá conforme a lei), sem apagar a identidade central nem o vínculo com outras. A identidade central
  só morre quando **não sobra nenhum vínculo**.

### 2.4. `conta_app` — split
- Hoje `conta_app` (tenant, V49/V108) mistura credencial + sessão. Passa a dividir:
  - **Credencial → central (`public`):** `cpf`, `senha_hash`, `senha_tentativas`, estado do OTP.
  - **Sessão/aparelho ativo + push token → por clínica-app:** `dispositivo_ativo` e `dispositivo`
    (push) ficam por (identidade, inquilino).
- **Regra "1 sessão ativa" passa a valer POR APP-DE-CLÍNICA** — abrir o app da Clínica 2 **não**
  desloga o app da Clínica 1.
- O `cid` do token (hoje = `conta_app.id`) passa a representar a sessão por *(identidade central,
  clínica)*.

### 2.5. Login do paciente — UX (CPF-first, pensada para idosos)
- **Tira os 2 botões iniciais; começa pelo CPF.** O app faz uma pré-checagem e **conduz** o paciente.
- Estados após o CPF (a pré-checagem **nunca vaza outras clínicas**):
  | Estado | Ação do app |
  |---|---|
  | Tem senha, já é desta clínica | "Digite sua senha" |
  | Tem senha, 1º acesso **nesta** clínica (já agendado) | "Use a sua senha de sempre" → entra + vincula |
  | Sem senha ainda | "Enviamos um código por SMS" → cria senha |
  | CPF não está nesta clínica | "Procure a recepção" |
- **2 regras que evitam a troca acidental de senha:**
  1. Se já existe senha, o app mostra **só** "digite sua senha" — **nunca** oferece "criar senha".
  2. O **único** caminho que troca o PIN central é "**Esqueci minha senha**" → OTP, com **aviso**
     de que a nova senha **vale para todas as clínicas**.
- **Privacidade:** **não listar** as clínicas do paciente. A frase de apoio é "é a mesma senha de
  sempre" (sem citar onde mais ele tem cadastro).
- **1º acesso num app novo:** **só senha** (sem OTP), vincula o aparelho em silêncio. OTP só para
  **cadastro novo de verdade** e para **reset de senha**.
- **Acessibilidade (idoso):** lembrar o CPF no aparelho, **biometria** após o 1º login, teclado
  numérico no CPF/senha, letra grande, alto contraste, linguagem simples ("senha", "código por SMS"
  — nada de "PIN/token/autenticação").

### 2.6. Status do app no back-office
- A clínica vê o status do **app dela** (para decidir o envio do SMS-convite):
  "Sem app" / "App ativo".
- **+ aviso genérico** "Paciente já tem acesso ao sistema (vai usar a senha que já tem)" —
  **sem nomear** clínicas, **sem** contagem. Vem de **um único booleano** da identidade central
  (`tem credencial ativa?`), nunca de uma lista.

### 2.7. Admin / back-office
- **Admin NÃO é centralizado** — fica por-clínica. Como o **subdomínio** já diz o inquilino, o login
  passa a ser **(inquilino + e-mail)** → o **mesmo e-mail pode repetir** entre clínicas.
- Mesma pessoa admin de 2 clínicas = 2 logins distintos, cada um pelo subdomínio da clínica
  (sessões separadas). O apex é só super-admin — não há login de admin genérico sem subdomínio.

### 2.8. Push / SMS-convite / links de loja — tudo por clínica
- Push por (clínica, instalação) — cada app A-baked tem seu Firebase/token.
- SMS-convite aponta para a **loja do app daquela clínica** (com deep link); "está sem app" vira
  "sem vínculo a **esta** clínica". Os 2 links de loja nas Configurações viram **por inquilino**.

### 2.9. Segurança / LGPD
- **Blast radius aceito:** login único = senha roubada expõe o paciente em todas as clínicas dele.
  Inerente ao login único (desejado). Mitigado por lockout central + OTP para reset + dispositivo
  vinculado + biometria.
- **Lockout central** (errar 5× trava em todos os apps até reset por SMS).
- **Telefone do OTP** = o verificado na ativação (os telefones de contato de cada clínica são
  separados, dado clínico).
- **LGPD:** cada clínica é controladora (TCLE/consentimento por clínica) + aviso único de plataforma
  sobre o login compartilhado. Auditar ações de identidade (vínculo, desvínculo, troca de PIN central).

---

## 3. Arquitetura resultante — o que vive onde

**`public` (plataforma + identidade, sem dado clínico):**
- `inquilino` (+ nova coluna `subdominio`)
- `usuario_login` (admin: e-mail→inquilino)
- `paciente_identidade` (NOVO: CPF, nome, DOB, telefone OTP, credencial central)
- `paciente_vinculo` (NOVO/evolução do `paciente_login`: identidade ↔ inquilino, 1→N)
- `assinatura_roteamento`, `configuracao_plataforma`

**Schema do inquilino (`principal` + clínicas) — dado clínico:**
- `paciente` (cadastro clínico), relações de responsável, agendamentos, prontuário, etc.
- sessão/dispositivo ativo + push por (identidade, inquilino)

**A "dica" de inquilino pré-login (não mexe em toda a API):**
- Só nos endpoints públicos que montam o login: `GET /tema`, `/marca`, `/funcionalidades` + o login.
- **Web:** derivada do `window.location.hostname` (subdomínio) → header `X-Inquilino` nas chamadas.
- **App:** a constante `INQUILINO` embutida no build → mesma dica.
- **Pós-login:** o claim `inq` do JWT manda. Um `SubdominioTenantFilter` só age quando **não há JWT**.

---

## 4. Plano de desenvolvimento por fases

> Ordem pensada pra entregar valor cedo e isolar o risco. Fase A é independente e de baixo risco;
> Fase B é o núcleo (mexe em dado real); C depende de B; D depende de B/C; E é a virada de prod.

### Fase A — Subdomínio do admin (independente, baixo risco)
**Objetivo:** `clinicaX.trilharsaude.app.br` mostra a marca da clínica no login do back-office.
- **DB (db/platform):** V155 = `ALTER TABLE public.inquilino ADD COLUMN IF NOT EXISTS subdominio` +
  índice único **parcial** (`WHERE subdominio IS NOT NULL`). V156 = trocar a unicidade de
  `usuario_login`: **remover o único global do e-mail** e criar **único por `(inquilino_id, email)`**
  (permite o mesmo e-mail de admin em clínicas diferentes). Tudo **idempotente** (lição do incidente Flyway).
- **Back:** `InquilinoRepository.findBySubdominio`, `InquilinoService.schemaPorSubdominio` (cache);
  `SubdominioTenantFilter` (OncePerRequestFilter instanciado no `SecurityConfig`, **depois** do
  `TenantFilter`; 1ª linha `if (atualBruto()!=null) return;`); `server.forward-headers-strategy=framework`;
  CORS para `allowedOriginPatterns` com `https://*.trilharsaude.app.br`. Super-admin: campo `subdominio`
  (request/response + validação `@Pattern` DNS).
- **Login admin por (inquilino + e-mail):** `AuthController.login` resolve o inquilino pela **dica do
  subdomínio** (não mais pelo e-mail global) e autentica contra `(inquilino, e-mail)`; a checagem de
  duplicidade no `SuperAdminController` passa a ser **por (inquilino, e-mail)** (não global). **Login de
  admin acontece sempre num subdomínio de clínica**; o apex `trilharsaude.app.br` é **só super-admin**
  (sem inquilino não há como resolver o e-mail repetido).
- **Front:** interceptor funcional (reusar o gate `ehApi`) que injeta `X-Inquilino` lido do
  hostname **só no browser** (`isPlatformBrowser`); super-admin ganha o campo subdomínio.
- **Infra (Railway + registro.br):** domínio customizado wildcard `*.trilharsaude.app.br` no serviço do
  **front** → o Railway dá um CNAME alvo + uma TXT de verificação (ACME). No **DNS do registro.br**
  (nameservers nativos, **sem proxy** — então sem as pegadinhas de Cloudflare): criar o CNAME curinga
  `*` → alvo do Railway + a TXT `_acme-challenge`. Cert wildcard emitido/renovado pelo Railway.
- **Validação:** abrir `clinicaX.trilharsaude.app.br` → login do admin com a cor/logo/nome da clínica;
  apex (`trilharsaude.app.br`) neutro/super-admin; pós-login tudo normal.
- **Risco:** host atrás de proxy (resolver com forward-headers); CORS curinga; emissão do cert wildcard.

### Fase B — Identidade central do paciente (núcleo, refactor)
**Objetivo:** login único do paciente no `public`; dado clínico segue por-tenant.
- **DB (db/platform):** `paciente_identidade` (CPF único, nome, DOB, telefone OTP, `senha_hash`,
  `senha_tentativas`, OTP state) + `paciente_vinculo` (identidade↔inquilino, 1→N) evoluindo o
  `paciente_login`. Sessão/`dispositivo_ativo`/push ficam por (identidade, inquilino).
- **Back:** `conta_app` split (credencial → central; sessão → por clínica); `PacienteAuthController`
  passa a validar a **credencial central** e o **vínculo** com o `inq` (vindo da dica baked/subdomínio);
  pré-checagem `/iniciar` retorna o **estado** (tem senha? vinculado a esta clínica? existe nesta
  clínica?) **sem vazar outras**; **lockout e OTP centrais** (reset zera o PIN central + aviso);
  **auto-vínculo** (regra 4); booleano "já tem login" para o back-office; token com `inq` +
  identidade + paciente do tenant.
- **Validação:** criar identidade, logar em 2 clínicas com a mesma senha, confirmar isolamento
  clínico, lockout central, reset com aviso, auto-vínculo.
- **Risco:** o mais alto (mexe em credencial e dado de paciente). Testar com schema de dev antes.

### Fase C — App A-baked (EAS + login central no app)
**Objetivo:** app por cliente, branded desde a abertura, usando o login central.
- **EAS/config:** `app.config.js` dinâmico + build profiles por cliente (bundle id, nome, ícone,
  splash, cor, `INQUILINO`, `google-services.json`/`GoogleService-Info.plist`).
- **App:** login CPF-first (remove os 2 botões, estados da pré-checagem, não-listar clínicas,
  "mesma senha de sempre"); **biometria** + lembrar CPF; **só-senha** no 1º acesso de app novo;
  aviso no "esqueci senha"; manda a dica `INQUILINO` pré-login + login; branding embutido (splash)
  + refino via `/tema` e `/marca`.
- **Validação:** build de 2 clínicas; mesmo CPF+senha entra nos dois; branding correto; biometria.
- **Risco:** publicação por conta do cliente (coordenar contas Apple/Google); manutenção N builds.

### Fase D — Features por clínica (ajustes)
**Objetivo:** amarrar as features ao novo modelo.
- Push por (clínica, instalação); SMS-convite com link de loja por clínica + deep link; status do app
  no back-office (app da própria clínica + booleano "já tem login"); "sem app" = "sem vínculo a esta
  clínica".
- **Validação:** agendamento na Clínica 2 → SMS com o app certo; back-office mostra o status correto.

### Fase E — Virada no Railway + rollout
**Objetivo:** subir o novo modelo no Railway.
- **Railway NÃO é produção real ainda** (sem clínica/paciente real; dados atuais são teste — o user
  autorizou apagar/resetar tudo). Então **não precisa de backfill cuidadoso**: pode **resetar e
  re-semear fresh** o banco com o schema novo (`paciente_identidade` + `paciente_vinculo`, credencial
  no `public`). Backfill de dado real só quando houver produção de verdade.
- **Migrations aditivas/idempotentes** + baseline continuam valendo (lição do 1º deploy do Design B —
  é sobre não derrubar o boot, não sobre dados). Backup é opcional (higiene).
- **Validação:** app loga com o login central; subdomínio do admin; isolamento clínico entre schemas.

---

## 5. Dependências
- **A** independe de tudo (web/admin) — pode ser a primeira.
- **B** independe de A, mas é o núcleo; **C** depende de B (login central); **D** depende de B/C;
  **E** fecha (prod) depois de B/C/D validados em dev.

## 6. Riscos e atenção
- Flyway: **migrations aditivas e idempotentes** (`ADD COLUMN IF NOT EXISTS`), nunca repetir número de
  versão (contador global entre `db/migration` e `db/platform`; próximos livres = **V155, V156…**).
- **Railway ainda não é produção real** (dados de teste, descartáveis — pode resetar/re-semear); por
  isso as migrações de dado não precisam de backfill cuidadoso **por enquanto**.
- Subdomínio atrás de proxy (forward-headers) e CORS curinga.
- Blast radius do login único (aceito; mitigado).
- Manutenção de N apps (automação EAS; reenvio anual por target-SDK).

## 7. Checklist — onboarding de um novo cliente (A-baked)
1. Criar inquilino no super-admin (nome, schema, **subdomínio**, 1º admin).
2. DNS: `clinicaX.trilharsaude.app.br` já coberto pelo wildcard (nada a fazer no registro.br por clínica).
3. EAS: novo build profile (bundle id, nome, ícone, splash, cor, `INQUILINO`, arquivos Firebase).
4. Contas de loja **do cliente** (Apple $99/ano, Google $25) + acesso pra publicar.
5. Links de loja da clínica nas Configurações (SMS-convite).
6. Publicar app (Play/App Store) sob a conta do cliente.
