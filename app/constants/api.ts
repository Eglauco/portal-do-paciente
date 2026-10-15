import Constants from 'expo-constants';
import { Platform } from 'react-native';

/**
 * URL base da API (back-end Spring Boot).
 *
 * Precedência:
 * 1) EXPO_PUBLIC_API_URL — override manual (.env.local em dev, ou eas.json no build);
 * 2) build de produção (APK/AAB): o backend hospedado no Railway;
 * 3) DEV num aparelho físico: descobre o IP do notebook pelo HOST DO METRO (o back roda no MESMO
 *    PC, porta 8080) → segue o IP sozinho, via Wi-Fi, sem adb e mesmo que o DHCP troque o IP;
 * 4) fallback de dev: hosts locais padrão (emulador Android → 10.0.2.2; iOS sim/web → localhost).
 */

/** Backend hospedado no Railway (produção). Ajuste aqui se o domínio mudar. */
const API_PRODUCAO = 'https://amiable-flow-production-a405.up.railway.app';

/** Porta do back em dev (roda no mesmo PC que o Metro). */
const PORTA_BACK_DEV = 8080;

const PADRAO_LOCAL = Platform.select({
  android: `http://10.0.2.2:${PORTA_BACK_DEV}`,
  default: `http://localhost:${PORTA_BACK_DEV}`,
});

/**
 * Em DEV, o back roda no MESMO computador que o Metro. O app já conecta no Metro pelo IP do notebook
 * (ex.: 192.168.18.11:8081) para carregar o JS — então reaproveitamos esse IP e só trocamos para a
 * porta do back (8080). Assim o aparelho físico acha o back sozinho via Wi-Fi, sem adb e mesmo que o
 * DHCP troque o IP. Retorna null quando não há host de dev (produção → Railway) ou quando o host é
 * local (localhost/127.* ou 10.0.2.2 do emulador → aí usa o PADRAO_LOCAL).
 */
function urlBackPeloMetro(): string | null {
  const fontes = Constants as unknown as {
    expoConfig?: { hostUri?: string } | null;
    expoGoConfig?: { debuggerHost?: string } | null;
    manifest2?: { extra?: { expoClient?: { hostUri?: string } } } | null;
  };
  const hostUri =
    fontes.expoConfig?.hostUri ??
    fontes.expoGoConfig?.debuggerHost ??
    fontes.manifest2?.extra?.expoClient?.hostUri ??
    null;
  const host = hostUri?.split(':')[0]?.trim();
  if (!host || host === 'localhost' || host.startsWith('127.') || host === '10.0.2.2') {
    return null;
  }
  return `http://${host}:${PORTA_BACK_DEV}`;
}

export const API_URL =
  process.env.EXPO_PUBLIC_API_URL ??
  (__DEV__ ? (urlBackPeloMetro() ?? PADRAO_LOCAL) : API_PRODUCAO);

/**
 * Inquilino (subdomínio da clínica) "assado" no build — A-baked: cada clínica tem seu próprio
 * build com EXPO_PUBLIC_INQUILINO definido no perfil do eas.json. Null = build genérico/dev sem
 * clínica fixa (o backend cai no padrão da plataforma no pré-login). Em dev dá pra simular uma
 * clínica definindo EXPO_PUBLIC_INQUILINO no .env.local.
 */
export const INQUILINO = process.env.EXPO_PUBLIC_INQUILINO?.trim() || null;

/**
 * Header da "dica" de inquilino para o backend resolver a clínica ANTES do login (branding + tenant
 * do login). Pós-login o claim `inq` do JWT manda e o backend ignora este header, então é seguro
 * mandar sempre. Vazio quando não há inquilino assado (build genérico/dev).
 */
export function inquilinoHeader(): Record<string, string> {
  return INQUILINO ? { 'X-Inquilino': INQUILINO } : {};
}
