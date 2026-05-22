package com.example.inventario_ra.ui.fragments;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.inventario_ra.databinding.FragmentSucursalesBinding;
import com.example.inventario_ra.models.Sucursales;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SucursalesFragment extends Fragment implements OnMapReadyCallback {

    private FragmentSucursalesBinding binding;
    private GoogleMap mMap;
    private DatabaseReference mDatabase;

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

        // Configuración del botón para agregar sucursal
        binding.fabAgregarSucursal.setOnClickListener(v -> {
            Navigation.findNavController(requireView()).navigate(com.example.inventario_ra.R.id.action_sucursales_to_agregar_sucursal);
        });

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
        
        // Configuración de UI del mapa
        mMap.getUiSettings().setZoomControlsEnabled(true);
        
        // Posicionar cámara inicialmente en el centro de El Salvador
        LatLng centroES = new LatLng(13.7942, -88.8965);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(centroES, 8.5f));

        cargarSucursalesEnMapa();
    }

    private void cargarSucursalesEnMapa() {
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                for (DataSnapshot data : snapshot.getChildren()) {
                    Sucursales sucursal = data.getValue(Sucursales.class);
                    if (sucursal != null) {
                        LatLng posicion = new LatLng(sucursal.getLatitud(), sucursal.getLongitud());
                        
                        // Añadir marcador
                        mMap.addMarker(new MarkerOptions()
                                .position(posicion)
                                .title(sucursal.getNombre()));

                        // Añadir círculo de radio (Visualización del alcance GPS)
                        mMap.addCircle(new CircleOptions()
                                .center(posicion)
                                .radius(sucursal.getRadio_metros())
                                .strokeColor(Color.BLUE)
                                .fillColor(0x220000FF) // Azul semitransparente
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
