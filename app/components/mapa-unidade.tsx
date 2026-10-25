import { Platform, StyleSheet, View } from 'react-native';
import MapView, { Marker, PROVIDER_DEFAULT } from 'react-native-maps';

/**
 * Miniatura de mapa (estilo WhatsApp) do endereço da unidade. Não interativa — o toque é tratado
 * pelo card que a envolve (abre o "Como chegar"). No Android usa liteMode (bitmap estático, leve);
 * no iOS usa o Apple Maps (PROVIDER_DEFAULT, sem chave). Não pede permissão de localização.
 */
export function MapaUnidade({
  latitude,
  longitude,
  height = 170,
}: {
  latitude: number;
  longitude: number;
  height?: number;
}) {
  return (
    <View pointerEvents="none" style={[styles.wrap, { height }]}>
      <MapView
        style={StyleSheet.absoluteFill}
        provider={PROVIDER_DEFAULT}
        liteMode={Platform.OS === 'android'}
        cacheEnabled={Platform.OS === 'android'}
        initialRegion={{ latitude, longitude, latitudeDelta: 0.006, longitudeDelta: 0.006 }}
        scrollEnabled={false}
        zoomEnabled={false}
        rotateEnabled={false}
        pitchEnabled={false}
        toolbarEnabled={false}>
        <Marker coordinate={{ latitude, longitude }} />
      </MapView>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { width: '100%', backgroundColor: '#E9EDF0' },
});
