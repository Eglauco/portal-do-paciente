# Controle de versão (back / front / app)

Cada camada tem a **sua própria versão**, **independente** das outras. **Princípio: cada componente
é dono só da SUA versão, no seu próprio arquivo** — assim sabemos exatamente a versão **real** que
está rodando em cada um, sem um atrapalhar o outro.

## Onde fica a versão de cada um (arquivo próprio = fonte da verdade)

| Componente | Arquivo (bump aqui) | Formato |
|---|---|---|
| **Back** | `back/src/main/resources/versao.properties` | `versao.back.numero` + `versao.back.data` |
| **Front** | `front/src/environments/versao-front.ts` | `export const VERSAO_FRONT = { versao, data }` |
| **App** | `app/app.json` | `"version"` (número) + `"extra.dataApp"` (data) |

Datas sempre em ISO `yyyy-MM-dd`; a UI formata para `dd/MM/aaaa`.

## Como cada um é exibido (cada surface mostra a SUA versão + a do back)

O backend expõe **só a versão dele** em **`GET /versao`** (público, liberado no `SecurityConfig`
junto de `/tema` e `/marca`):

```json
{ "versao": "1.0.0", "data": "2026-11-06" }
```

- **App → Perfil → "Sobre":** **Aplicativo** (lido do `app.json` via `Constants` = versão real
  instalada) **+ Servidor** (lido do `GET /versao`).
- **Admin → Configurações → "Sobre / Versões":** **Backend (API)** (do `GET /versao`) **+ Admin
  (web)** (lido do `versao-front.ts`, embutido no build = versão real no ar). A **sidebar** e o
  **login** também mostram a versão do front a partir do `versao-front.ts`.

> O admin **não** mostra a versão do app: o back não a guarda e não há como saber a versão
> realmente instalada em cada aparelho. A versão do app é vista no próprio app.

## Esquema de numeração (semver `MAJOR.MINOR.PATCH`)

- **correção / bugfix** → `+0.0.1` (patch)
- **melhoria / feature** → `+0.1.0` (minor)
- **quebra / retrabalho grande** → `+1.0.0` (major)

## Processo de bump — a cada correção/melhoria

Atualize **só o arquivo do componente que mudou** (número conforme o semver + `data` = dia do release):

| Mudou no… | Atualize | Observação |
|---|---|---|
| **back** | `versao.back.numero` + `versao.back.data` em `versao.properties` | Reinicie o backend para o `/versao` refletir. |
| **front** | `versao` + `data` em `versao-front.ts` | Entra no build do front; aparece no próximo deploy. |
| **app** | `"version"` + `"extra.dataApp"` em `app.json` | Só chega ao usuário num **novo build/distribuição** (não há OTA). |

Cada arquivo é independente — mexer num **não** afeta os outros.

## Baseline

Back **1.0.0** · Front **1.0.0** · App **1.0.1** — todos **06/11/2026**.
