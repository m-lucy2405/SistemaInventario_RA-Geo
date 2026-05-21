package com.example.inventario_ra.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.inventario_ra.databinding.ActivityMenuPrincipalBinding;
import com.example.inventario_ra.location.GPSManager;
import com.example.inventario_ra.models.Productos;
import com.example.inventario_ra.models.Sucursales;
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

public class MenuPrincipalActivity extends AppCompatActivity {

    private ActivityMenuPrincipalBinding binding;
    private GPSManager gpsManager;
    private ProductoAdapter adapter;
    private DatabaseReference mDatabase;
    private Query productosQuery;
    private ValueEventListener productosListener;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMenuPrincipalBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mDatabase = FirebaseDatabase.getInstance().getReference();
        gpsManager = new GPSManager(this);

        configurarRecyclerView();

        // Evento para agregar nuevo producto
        binding.fabAgregar.setOnClickListener(v -> {
            Intent intent = new Intent(MenuPrincipalActivity.this, AgregarProductoActivity.class);
            startActivity(intent);
        });

        iniciarFlujoGeolocalizacion();
    }

    private void configurarRecyclerView() {
        binding.rvProductos.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductoAdapter(new ArrayList<>(), 
            producto -> {
                Intent intent = new Intent(MenuPrincipalActivity.this, VisorArActivity.class);
                intent.putExtra("PRODUCTO_ID", producto.getId());
                startActivity(intent);
            },
            this::mostrarOpcionesProducto
        );
        binding.rvProductos.setAdapter(adapter);
    }

    private void mostrarOpcionesProducto(Productos producto) {
        String[] opciones = {"Editar", "Eliminar"};
        new MaterialAlertDialogBuilder(this)
                .setTitle(producto.getNombre())
                .setItems(opciones, (dialog, which) -> {
                    if (which == 0) {
                        // Editar
                        Intent intent = new Intent(this, AgregarProductoActivity.class);
                        intent.putExtra("PRODUCTO_ID_EDITAR", producto.getId());
                        startActivity(intent);
                    } else {
                        // Eliminar
                        confirmarEliminacion(producto);
                    }
                })
                .show();
    }

    private void confirmarEliminacion(Productos producto) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirmar eliminación")
                .setMessage("¿Estás seguro de que deseas eliminar este producto?")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    mDatabase.child("productos").child(producto.getId()).removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Error al eliminar", Toast.LENGTH_SHORT).show());
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
                ActivityCompat.requestPermissions(MenuPrincipalActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
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
                            break; // Encontramos la sucursal donde está el usuario
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
        // Limpiar listener previo si existe
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

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                mostrarError("Error al cargar productos: " + error.getMessage());
            }
        };

        productosQuery.addValueEventListener(productosListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (productosQuery != null && productosListener != null) {
            productosQuery.removeEventListener(productosListener);
        }
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                iniciarFlujoGeolocalizacion(); // Reintentamos después de pedir permisos
            } else {
                mostrarError("Se requieren permisos de ubicacion para mostrar el catalogo cercano");
            }
        }
    }
}
