package com.example.inventario_ra.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.app.ActivityCompat;

import com.example.inventario_ra.models.Sucursales;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Gestor de geolocalización robusto para el sistema de inventario.
 */

public class GPSManager {
    private final FusedLocationProviderClient fusedLocationClient;
    private final Context context;

    /**
     * Interfaz para gestionar los resultados de la obtención de ubicación.
     */
    public interface LocationResultListener {
        void onSuccess(Location location);
        void onPermissionNeeded();
        void onError(String mensaje);
    }

    public GPSManager(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /**
     * Obtiene la ubicación actual del dispositivo validando permisos y estado del hardware.
     * @param listener Callback para manejar el éxito, falta de permisos o errores.
     */

    public void obtenerUbicacionActual(LocationResultListener listener) {
        // 1. Validación de permisos
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            listener.onPermissionNeeded();
            return;
        }

        // 2. Validación de estado del GPS (Hardware/Configuración)
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = false;
        boolean isNetworkEnabled = false;

        try {
            isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            listener.onError("Error al verificar el estado de los servicios de ubicación: " + e.getMessage());
            return;
        }

        if (!isGpsEnabled && !isNetworkEnabled) {
            listener.onError("El GPS está desactivado. Por favor, habilite la ubicación en los ajustes.");
            return;
        }

        // 3. Solicitud de ubicación actual con Google Play Services
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        listener.onSuccess(location);
                    } else {
                        listener.onError("No se pudo determinar la ubicación actual (ubicación nula).");
                    }
                })
                .addOnFailureListener(e -> {
                    listener.onError("Fallo en el servicio de ubicación: " + e.getMessage());
                });
    }

    /**
     * Calcula si el usuario está dentro del radio de acción de una sucursal específica.
     * @param ubicacionActual Ubicación obtenida del sensor GPS.
     * @param sucursal Objeto con las coordenadas y el radio de la sucursal.
     * @return true si el usuario está dentro del rango permitido.
     */
    public boolean estaEnRango(Location ubicacionActual, Sucursales sucursal) {
        if (ubicacionActual == null || sucursal == null) {
            return false;
        }

        float[] distance = new float[1];
        Location.distanceBetween(
                ubicacionActual.getLatitude(),
                ubicacionActual.getLongitude(),
                sucursal.getLatitud(),
                sucursal.getLongitud(),
                distance
        );

        // Comparamos la distancia calculada contra el radio definido en la sucursal
        return distance[0] <= sucursal.getRadio_metros();
    }
}
