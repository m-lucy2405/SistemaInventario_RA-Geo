package com.example.inventario_ra.ui.fragments;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.inventario_ra.databinding.FragmentInventarioBinding;
import com.example.inventario_ra.location.GPSManager;
import com.example.inventario_ra.models.Productos;
import com.example.inventario_ra.models.Sucursales;
import com.example.inventario_ra.ui.EscanerArActivity;
import com.example.inventario_ra.ui.VisorArActivity;
import com.example.inventario_ra.ui.adapters.ProductoAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class InventarioFragment extends Fragment {

    private FragmentInventarioBinding binding;
    private GPSManager gpsManager;
    private ProductoAdapter adapter;
    private DatabaseReference mDatabase;
    private Query productosQuery;
    private ValueEventListener productosListener;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentInventarioBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        gpsManager = new GPSManager(requireContext());

        configurarRecyclerView();
        iniciarFlujoGeolocalizacion();

        binding.fabEscanerAr.setOnClickListener(v -> {
            Intent intent = new Intent(requireActivity(), EscanerArActivity.class);
            startActivity(intent);
        });
    }

    private void configurarRecyclerView() {
        binding.rvProductos.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new ProductoAdapter(new ArrayList<>(),
                producto -> {
                    Intent intent = new Intent(requireActivity(), VisorArActivity.class);
                    intent.putExtra("PRODUCTO_ID", producto.getId());
                    startActivity(intent);
                },
                this::mostrarOpcionesProducto
        );
        binding.rvProductos.setAdapter(adapter);
    }

    private void mostrarOpcionesProducto(Productos producto) {
        String[] opciones = {"Editar Detalles", "Eliminar del Inventario"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(producto.getNombre())
                .setIcon(android.R.drawable.ic_dialog_info)
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) {
                        // Navegar al fragmento de agregar en modo edición
                        Bundle bundle = new Bundle();
                        bundle.putString("PRODUCTO_ID_EDITAR", producto.getId());
                        Navigation.findNavController(requireView()).navigate(com.example.inventario_ra.R.id.action_inventario_to_agregar, bundle);
                    } else {
                        confirmarEliminacion(producto);
                    }
                })
                .show();
    }

    private void confirmarEliminacion(Productos producto) {
        new MaterialAlertDialogBuilder(requireContext(), com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered)
                .setTitle("¡Atención!")
                .setMessage("¿Estás seguro de que deseas eliminar " + producto.getNombre() + "? Esta acción no se puede deshacer.")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    mDatabase.child("productos").child(producto.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(requireContext(), "Inventario actualizado", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(requireContext(), "Error al eliminar", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void iniciarFlujoGeolocalizacion() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvMensaje.setVisibility(View.GONE);

        gpsManager.obtenerUbicacionActual(new GPSManager.LocationResultListener() {
            @Override
            public void onSuccess(Location location) {
                buscarSucursalCercana(location);
            }

            @Override
            public void onPermissionNeeded() {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE);
            }

            @Override
            public void onError(String mensaje) {
                mostrarError(mensaje);
            }
        });
    }

    private void buscarSucursalCercana(Location miUbicacion) {
        mDatabase.child("sucursales").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Sucursales sucursalActual = null;

                for (DataSnapshot data : snapshot.getChildren()) {
                    Sucursales s = data.getValue(Sucursales.class);
                    if (s != null) {
                        s.setId(data.getKey());
                        if (gpsManager.estaEnRango(miUbicacion, s)) {
                            sucursalActual = s;
                            break;
                        }
                    }
                }

                if (sucursalActual != null) {
                    cargarProductosDeSucursal(sucursalActual.getId());
                    binding.tvTitulo.setText("Catálogo - " + sucursalActual.getNombre());
                } else {
                    mostrarError("No se encuentra cerca de ninguna sucursal registrada.");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                mostrarError("Error en Firebase: " + error.getMessage());
            }
        });
    }

    private void cargarProductosDeSucursal(String sucursalId) {
        if (productosQuery != null && productosListener != null) {
            productosQuery.removeEventListener(productosListener);
        }

        productosQuery = mDatabase.child("productos").orderByChild("sucursal_id").equalTo(sucursalId);
        productosListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Productos> listaFiltrada = new ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Productos p = data.getValue(Productos.class);
                    if (p != null) {
                        p.setId(data.getKey());
                        listaFiltrada.add(p);
                    }
                }

                if (isAdded()) {
                    binding.progressBar.setVisibility(View.GONE);
                    if (listaFiltrada.isEmpty()) {
                        binding.tvMensaje.setText("No hay productos disponibles en esta sucursal.");
                        binding.tvMensaje.setVisibility(View.VISIBLE);
                        adapter.actualizarLista(new ArrayList<>());
                    } else {
                        binding.tvMensaje.setVisibility(View.GONE);
                        adapter.actualizarLista(listaFiltrada);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) mostrarError("Error al cargar productos: " + error.getMessage());
            }
        };

        productosQuery.addValueEventListener(productosListener);
    }

    private void mostrarError(String mensaje) {
        binding.progressBar.setVisibility(View.GONE);
        binding.tvMensaje.setText(mensaje);
        binding.tvMensaje.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarFlujoGeolocalizacion();
            } else {
                mostrarError("Se requieren permisos de ubicación para mostrar el catálogo cercano");
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (productosQuery != null && productosListener != null) {
            productosQuery.removeEventListener(productosListener);
        }
        binding = null;
    }
}
