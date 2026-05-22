package com.example.inventario_ra.ui.fragments;

import java.util.Locale;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.inventario_ra.R;
import com.example.inventario_ra.databinding.FragmentAgregarSucursalBinding;
import com.example.inventario_ra.models.Sucursales;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AgregarSucursalFragment extends Fragment implements OnMapReadyCallback {

    private FragmentAgregarSucursalBinding binding;
    private DatabaseReference mDatabase;
    private String idEditar;
    private boolean esEdicion = false;
    private GoogleMap mMap;
    private Marker markerSeleccion;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAgregarSucursalBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDatabase = FirebaseDatabase.getInstance().getReference("sucursales");

        // Detección de modo edición por argumentos
        if (getArguments() != null) {
            idEditar = getArguments().getString("SUCURSAL_ID_EDITAR");
            esEdicion = (idEditar != null);
        }

        if (esEdicion) {
            binding.tvTituloSucursal.setText("Actualizar Sucursal");
            binding.btnGuardarSucursal.setText("Actualizar Sucursal");
            cargarDatosSucursal();
        }

        // Inicializar el mapa selector
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.map_pick);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        binding.btnGuardarSucursal.setOnClickListener(v -> validarYGuardar());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Estilo oscuro
        try {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style));
        } catch (Exception e) {
            Log.e("MAP_PICK", "Error estilo mapa: " + e.getMessage());
        }

        // Configuración inicial de cámara (El Salvador)
        LatLng centroES = new LatLng(13.7942, -88.8965);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centroES, 8.5f));

        // Si es edición y los datos ya se cargaron antes de que el mapa estuviera listo
        String latStr = binding.etLatitud.getText().toString();
        String lonStr = binding.etLongitud.getText().toString();
        if (!latStr.isEmpty() && !lonStr.isEmpty()) {
            try {
                double lat = Double.parseDouble(latStr);
                double lon = Double.parseDouble(lonStr);
                LatLng pos = new LatLng(lat, lon);
                if (markerSeleccion != null) markerSeleccion.remove();
                markerSeleccion = mMap.addMarker(new MarkerOptions().position(pos).title("Ubicación Actual"));
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
            } catch (Exception ignored) {}
        }

        // Listener de clic en mapa para capturar coordenadas
        mMap.setOnMapClickListener(latLng -> {
            if (markerSeleccion != null) {
                markerSeleccion.remove();
            }
            // MarkerOptions: Permite definir la posición visual del pin en el mapa.
            markerSeleccion = mMap.addMarker(new MarkerOptions().position(latLng).title("Nueva Ubicación"));
            
            // Auto-poblar los campos: Facilitamos la UX al capturar las coordenadas exactas del clic.
            binding.etLatitud.setText(String.format(Locale.US, "%.6f", latLng.latitude));
            binding.etLongitud.setText(String.format(Locale.US, "%.6f", latLng.longitude));
        });
    }

    private void cargarDatosSucursal() {
        binding.progressBar.setVisibility(View.VISIBLE);
        mDatabase.child(idEditar).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded() || binding == null) return;
                binding.progressBar.setVisibility(View.GONE);
                
                Sucursales sucursal = snapshot.getValue(Sucursales.class);
                if (sucursal != null) {
                    // Llenado meticuloso con formato regional de alta precisión
                    binding.etNombreSucursal.setText(sucursal.getNombre());
                    binding.etDescripcionSucursal.setText(sucursal.getDescripcion());
                    
                    // Forzamos Locale.US para asegurar que el punto decimal sea el separador en la UI
                    binding.etLatitud.setText(String.format(Locale.US, "%.6f", sucursal.getLatitud()));
                    binding.etLongitud.setText(String.format(Locale.US, "%.6f", sucursal.getLongitud()));
                    
                    binding.etRadio.setText(String.valueOf(sucursal.getRadio_metros()));

                    // Sincronización con el mapa (si ya está listo)
                    if (mMap != null) {
                        LatLng pos = new LatLng(sucursal.getLatitud(), sucursal.getLongitud());
                        actualizarMarcadorEnMapa(pos, sucursal.getNombre());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Error al cargar datos", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void actualizarMarcadorEnMapa(LatLng pos, String titulo) {
        if (markerSeleccion != null) markerSeleccion.remove();
        markerSeleccion = mMap.addMarker(new MarkerOptions()
                .position(pos)
                .title(titulo != null ? titulo : "Ubicación Actual"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f));
    }

    private void validarYGuardar() {
        String nombre = binding.etNombreSucursal.getText().toString().trim();
        String descripcion = binding.etDescripcionSucursal.getText().toString().trim();
        String latStr = binding.etLatitud.getText().toString().trim();
        String lonStr = binding.etLongitud.getText().toString().trim();
        String radioStr = binding.etRadio.getText().toString().trim();

        if (nombre.isEmpty() || latStr.isEmpty() || lonStr.isEmpty() || radioStr.isEmpty()) {
            Toast.makeText(requireContext(), "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double latitud = Double.parseDouble(latStr);
            double longitud = Double.parseDouble(lonStr);
            int radio = Integer.parseInt(radioStr);

            guardarEnFirebase(nombre, descripcion, latitud, longitud, radio);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Ingrese valores numéricos válidos", Toast.LENGTH_SHORT).show();
        }
    }

    private void guardarEnFirebase(String nombre, String descripcion, double latitud, double longitud, int radio) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnGuardarSucursal.setEnabled(false);

        String idFinal;
        if (esEdicion) {
            idFinal = idEditar;
        } else {
            // Sanitización del ID: Convertimos el nombre legible a un ID técnico para Firebase.
            // Ejemplo: "Sucursal Central" -> "sucursal_sucursal_central"
            // Esto permite que las URLs de la base de datos sean predecibles y limpias.
            String nombreLimpio = nombre.toLowerCase(Locale.getDefault())
                    .replaceAll("\\s+", "_")
                    .replaceAll("[^a-z0-9_]", "");
            idFinal = "sucursal_" + nombreLimpio;
        }

        Sucursales sucursal = new Sucursales(idFinal, nombre, latitud, longitud, radio, descripcion);
        
        if (idFinal != null) {
            mDatabase.child(idFinal).setValue(sucursal)
                    .addOnSuccessListener(aVoid -> {
                        if (isAdded()) {
                            String msg = esEdicion ? "Sucursal actualizada" : "Sucursal registrada";
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            Navigation.findNavController(requireView()).popBackStack();
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (isAdded()) {
                            binding.progressBar.setVisibility(View.GONE);
                            binding.btnGuardarSucursal.setEnabled(true);
                            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
