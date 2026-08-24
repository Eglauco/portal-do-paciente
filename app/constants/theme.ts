/**
 * Below are the colors that are used in the app. The colors are defined in the light and dark mode.
 * There are many other ways to style your app. For example, [Nativewind](https://www.nativewind.dev/), [Tamagui](https://tamagui.dev/), [unistyles](https://reactnativeunistyles.vercel.app), etc.
 */

import { Platform } from 'react-native';

const tintColorLight = '#0a7ea4';
const tintColorDark = '#fff';

export const Colors = {
  light: {
    text: '#11181C',
    background: '#fff',
    tint: tintColorLight,
    icon: '#687076',
    tabIconDefault: '#687076',
    tabIconSelected: tintColorLight,
  },
  dark: {
    text: '#ECEDEE',
    background: '#151718',
    tint: tintColorDark,
    icon: '#9BA1A6',
    tabIconDefault: '#9BA1A6',
    tabIconSelected: tintColorDark,
  },
};

/**
 * Portal do Paciente — paleta de marca ("cuidado sereno").
 * Espelha a identidade usada no portal web (Angular).
 */
export const Brand = {
  bg: '#F5F8F6',
  surface: '#FFFFFF',
  ink: '#0C1F1C',
  muted: '#566863',
  line: '#E2EAE6',
  brand: '#0E8C7F',
  brandDeep: '#0A5F57',
  brandPine: '#072E2B',
  glow: '#7FE0C3',
  onBrand: '#EAFAF4',
};

/** Cores de status (agendamentos) — fundo suave + texto. */
export const Status: Record<string, { fg: string; bg: string }> = {
  confirmado: { fg: '#0A7D5A', bg: '#E3F6EC' },
  aguardando: { fg: '#A5741A', bg: '#FBF0D6' },
  realizado: { fg: '#2F6DF6', bg: '#E9F0FE' },
  cancelado: { fg: '#B23B4E', bg: '#FBE4E7' },
  reagendado: { fg: '#7A5AF5', bg: '#EFEAFE' },
};

/** Classificação NPS por faixa de nota. */
export const Nps = {
  promotor: { fg: '#0A7D5A', bg: '#E3F6EC' },
  neutro: { fg: '#A5741A', bg: '#FBF0D6' },
  detrator: { fg: '#B23B4E', bg: '#FBE4E7' },
};

/** Cores por tipo de documento do prontuário. */
export const DocTipo: Record<string, { fg: string; bg: string; icon: string }> = {
  exame: { fg: '#2F6DF6', bg: '#E9F0FE', icon: 'flask-outline' },
  receita: { fg: '#0E8C7F', bg: '#DCF1EC', icon: 'medkit-outline' },
  atestado: { fg: '#A5741A', bg: '#FBF0D6', icon: 'shield-checkmark-outline' },
  ficha: { fg: '#7A5AF5', bg: '#EFEAFE', icon: 'clipboard-outline' },
  laudo: { fg: '#B23B4E', bg: '#FBE4E7', icon: 'document-text-outline' },
};

export const Fonts = Platform.select({
  ios: {
    /** iOS `UIFontDescriptorSystemDesignDefault` */
    sans: 'system-ui',
    /** iOS `UIFontDescriptorSystemDesignSerif` */
    serif: 'ui-serif',
    /** iOS `UIFontDescriptorSystemDesignRounded` */
    rounded: 'ui-rounded',
    /** iOS `UIFontDescriptorSystemDesignMonospaced` */
    mono: 'ui-monospace',
  },
  default: {
    sans: 'normal',
    serif: 'serif',
    rounded: 'normal',
    mono: 'monospace',
  },
  web: {
    sans: "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
    serif: "Georgia, 'Times New Roman', serif",
    rounded: "'SF Pro Rounded', 'Hiragino Maru Gothic ProN', Meiryo, 'MS PGothic', sans-serif",
    mono: "SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace",
  },
});
