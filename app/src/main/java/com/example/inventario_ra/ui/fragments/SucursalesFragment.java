package com.example.inventario_ra.ui.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.inventario_ra.R;
import com.example.inventario_ra.databinding.FragmentSucursalesBinding;
import com.example.inventario_ra.models.Sucursales;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SucursalesFragment extends Fragment implements OnMapReadyCallback {

    private FragmentSucursalesBinding binding;
    private GoogleMap mMap;
    private DatabaseReference mDatabase;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSucursalesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDatabase = FirebaseDatabase.getInstance().getReference("sucursales");

        // Inicializar el mapa
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(com.example.inventario_ra.R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        
        // Aplicar Estilo Modo Oscuro
        try {
            boolean success = mMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style));
            if (!success) {
                Log.e("MAP_STYLE", "Fallo al cargar el estilo del mapa.");
            }
        } catch (Resources.NotFoundException e) {
            Log.e("MAP_STYLE", "No se encontró el archivo de estilo: ", e);
        }

        // Habilitar vista de relieve (opcional, se puede alternar)
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL); 
        
        // Configuración de UI del mapa
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Habilitar ubicación en tiempo real (Puntero Azul)
        activarUbicacionRealTime();
        
        // Listener para clics en la ventana de información (Para Editar/Eliminar)
        mMap.setOnInfoWindowClickListener(marker -> {
            Sucursales sucursal = (Sucursales) marker.getTag();
            if (sucursal != null) {
                mostrarOpcionesSucursal(sucursal);
            }
        });
        
        // Posicionar cámara inicialmente en el centro de El Salvador
        LatLng centroES = new LatLng(13.7942, -88.8965);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centroES, 8.5f));

        cargarSucursalesEnMapa();
    }

    private void mostrarOpcionesSucursal(Sucursales sucursal) {
        String[] opciones = {"Editar Detalles", "Eliminar Sucursal"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(sucursal.getNombre())
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) {
                        // Navegar a Editar Sucursal
                        Bundle bundle = new Bundle();
                        bundle.putString("SUCURSAL_ID_EDITAR", sucursal.getId());
                        Navigation.findNavController(requireView()).navigate(R.id.action_sucursales_to_agregar_sucursal, bundle);
                    } else {
                        // Confirmar Eliminación
                        confirmarEliminacionSucursal(sucursal);
                    }
                })
                .show();
    }

    private void confirmarEliminacionSucursal(Sucursales sucursal) {
        new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle("Eliminar Sucursal")
                .setMessage("¿Estás seguro de que deseas eliminar '" + sucursal.getNombre() + "'? Se perderán las geocercas asociadas.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    mDatabase.child(sucursal.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(requireContext(), "Sucursal eliminada", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(requireContext(), "Error al eliminar", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void activarUbicacionRealTime() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (mMap != null) {
                mMap.setMyLocationEnabled(true);
            }
        } else {
            // Solicitar permisos si no han sido concedidos
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                activarUbicacionRealTime();
            }
        }
    }

    private void cargarSucursalesEnMapa() {
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || mMap == null) return;

                mMap.clear(); // Limpiar mapa para redibujar todo en tiempo real

                for (DataSnapshot data : snapshot.getChildren()) {
                    Sucursales sucursal = data.getValue(Sucursales.class);
                    if (sucursal != null) {
                        LatLng posicion = new LatLng(sucursal.getLatitud(), sucursal.getLongitud());
                        
                        // Añadir marcador
                        Marker marker = mMap.addMarker(new MarkerOptions()
                                .position(posicion)
                                .title(sucursal.getNombre())
                                .snippet("Toca para gestionar"));
                        
                        if (marker != null) {
                            marker.setTag(sucursal); // Guardamos el objeto para recuperarlo en el clic
                        }

                        // Añadir círculo de radio
                        mMap.addCircle(new CircleOptions()
                                .center(posicion)
                                .radius(sucursal.getRadio_metros())
                                .strokeColor(Color.BLUE)
                                .fillColor(0x220000FF)
                                .strokeWidth(2));
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Error al cargar sucursales: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
