import { Image } from 'expo-image';
import { ImageStyle, StyleProp, Text, TextStyle, View, ViewStyle } from 'react-native';

import { usePerfilFoto } from '@/hooks/use-perfil-foto';

interface FotoProps {
  /** Foto a exibir; quando ausente, mostra o círculo com iniciais. */
  fotoUrl?: string | null;
  /** Iniciais mostradas quando não há foto. */
  iniciais: string;
  /** Diâmetro do círculo (a foto usa o mesmo tamanho, redonda). */
  tamanho: number;
  /** Estilo do círculo de fallback (iniciais). */
  estiloCirculo?: StyleProp<ViewStyle>;
  /** Estilo do texto das iniciais. */
  estiloTexto?: StyleProp<TextStyle>;
  /** Estilo extra da foto (ex.: margens para alinhar igual ao círculo). */
  estiloFoto?: StyleProp<ImageStyle>;
}

/** Avatar genérico: mostra a foto (se houver) ou o círculo com iniciais igual antes. */
export function AvatarFoto({ fotoUrl, iniciais, tamanho, estiloCirculo, estiloTexto, estiloFoto }: FotoProps) {
  if (fotoUrl) {
    return (
      <Image
        source={fotoUrl}
        style={[{ width: tamanho, height: tamanho, borderRadius: tamanho / 2 }, estiloFoto]}
        contentFit="cover"
        transition={200}
      />
    );
  }
  return (
    <View style={estiloCirculo}>
      <Text style={estiloTexto}>{iniciais}</Text>
    </View>
  );
}

/**
 * Avatar do PACIENTE logado: usa a foto compartilhada via {@link usePerfilFoto}.
 * Use apenas onde o círculo representa o próprio paciente — para autores de
 * comentário use {@link AvatarFoto} com a foto do autor.
 */
export function AvatarPaciente(props: Omit<FotoProps, 'fotoUrl'>) {
  const { fotoUrl } = usePerfilFoto();
  return <AvatarFoto fotoUrl={fotoUrl} {...props} />;
}
