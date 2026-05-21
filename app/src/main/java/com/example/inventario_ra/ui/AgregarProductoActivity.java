package com.example.inventario_ra.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.inventario_ra.databinding.ActivityAgregarProductoBinding;
import com.example.inventario_ra.models.Productos;
import com.example.inventario_ra.models.Sucursales;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AgregarProductoActivity extends AppCompatActivity {

    private ActivityAgregarProductoBinding binding;
    private DatabaseReference mDatabase;
    private StorageReference mStorage;
    
    private List<Sucursales> listaSucursales;
    private List<String> nombresSucursales;
    
    private Uri uriImagen;
    private Uri uriModelo;

    private String idEditar;
    private boolean esEdicion;
    private Productos productoAEditar;

    // Launchers para selección de archivos
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    uriImagen = result.getData().getData();
                    binding.tvEstadoImagen.setText("Imagen seleccionada");
                }
            }
    );

    private final ActivityResultLauncher<Intent> modelPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    uriModelo = result.getData().getData();
                    binding.tvEstadoModelo.setText("Modelo 3D seleccionado");
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAgregarProductoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mDatabase = FirebaseDatabase.getInstance().getReference();
        mStorage = FirebaseStorage.getInstance().getReference();
        
        listaSucursales = new ArrayList<>();
        nombresSucursales = new ArrayList<>();

        // Detectar modo edición
        idEditar = getIntent().getStringExtra("PRODUCTO_ID_EDITAR");
        esEdicion = (idEditar != null);

        if (esEdicion) {
            binding.btnGuardar.setText("Actualizar Producto");
        }

        cargarSucursales();

        binding.btnSeleccionarImagen.setOnClickListener(v -> seleccionarImagen());
        binding.btnSeleccionarModelo.setOnClickListener(v -> seleccionarModelo());
        binding.btnGuardar.setOnClickListener(v -> validarYSubir());
    }

    private void cargarSucursales() {
        mDatabase.child("sucursales").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaSucursales.clear();
                nombresSucursales.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Sucursales sucursal = data.getValue(Sucursales.class);
                    if (sucursal != null) {
                        sucursal.setId(data.getKey());
                        listaSucursales.add(sucursal);
                        nombresSucursales.add(sucursal.getNombre());
                    }
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(AgregarProductoActivity.this,
                        android.R.layout.simple_spinner_item, nombresSucursales);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                binding.spinnerSucursal.setAdapter(adapter);

                // Si estamos editando, cargamos los datos después de tener las sucursales
                if (esEdicion) {
                    cargarDatosProducto();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AgregarProductoActivity.this, "Error al cargar sucursales", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cargarDatosProducto() {
        mDatabase.child("productos").child(idEditar).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                productoAEditar = snapshot.getValue(Productos.class);
                if (productoAEditar != null) {
                    binding.etNombre.setText(productoAEditar.getNombre());
                    binding.etDescripcion.setText(productoAEditar.getDescripcion());
                    binding.etPrecio.setText(String.valueOf(productoAEditar.getPrecio()));
                    binding.etStock.setText(String.valueOf(productoAEditar.getStock()));
                    
                    if (productoAEditar.getImagen_ref_url() != null) {
                        binding.tvEstadoImagen.setText("Imagen actual cargada");
                    }
                    if (productoAEditar.getModelo_3d_url() != null) {
                        binding.tvEstadoModelo.setText("Modelo 3D actual cargado");
                    }

                    // Seleccionar sucursal en el spinner
                    for (int i = 0; i < listaSucursales.size(); i++) {
                        if (listaSucursales.get(i).getId().equals(productoAEditar.getSucursal_id())) {
                            binding.spinnerSucursal.setSelection(i);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(AgregarProductoActivity.this, "Error al cargar producto", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void seleccionarImagen() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void seleccionarModelo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*"); // Buscamos archivos .glb/.gltf
        modelPickerLauncher.launch(intent);
    }

    private void validarYSubir() {
        String nombre = binding.etNombre.getText().toString().trim();
        String precioStr = binding.etPrecio.getText().toString().trim();
        String stockStr = binding.etStock.getText().toString().trim();

        if (nombre.isEmpty() || precioStr.isEmpty() || stockStr.isEmpty()) {
            Toast.makeText(this, "Complete los campos obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        // Si es edición y no se cambiaron archivos, usamos los existentes
        if (esEdicion && uriImagen == null && uriModelo == null && productoAEditar != null) {
            guardarEnDatabase(nombre, precioStr, stockStr, 
                    productoAEditar.getImagen_ref_url(), 
                    productoAEditar.getModelo_3d_url());
            return;
        }

        // En creación o si se seleccionaron archivos nuevos en edición
        if (!esEdicion && (uriImagen == null || uriModelo == null)) {
            Toast.makeText(this, "Debe seleccionar la imagen y el modelo 3D", Toast.LENGTH_SHORT).show();
            return;
        }

        // Si se seleccionó al menos un archivo nuevo o es creación, empezamos el flujo de subida
        if (uriImagen != null) {
            subirImagen(nombre, precioStr, stockStr);
        } else if (uriModelo != null) {
            // Caso donde solo se cambió el modelo en edición
            subirModelo(nombre, precioStr, stockStr, productoAEditar.getImagen_ref_url());
        } else {
            // Este caso solo pasaría si se olvidó validar algo arriba
            Toast.makeText(this, "Error en la selección de archivos", Toast.LENGTH_SHORT).show();
        }
    }

    private void subirImagen(String nombre, String precioStr, String stockStr) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnGuardar.setEnabled(false);

        StorageReference fileRef = mStorage.child("productos_imagenes/" + UUID.randomUUID().toString());
        fileRef.putFile(uriImagen).addOnSuccessListener(taskSnapshot -> {
            fileRef.getDownloadUrl().addOnSuccessListener(urlImagen -> {
                if (uriModelo != null) {
                    subirModelo(nombre, precioStr, stockStr, urlImagen.toString());
                } else {
                    // Si estamos editando y solo cambiamos imagen
                    guardarEnDatabase(nombre, precioStr, stockStr, urlImagen.toString(), productoAEditar.getModelo_3d_url());
                }
            });
        }).addOnFailureListener(e -> {
            ocultarCarga();
            Toast.makeText(this, "Fallo al subir imagen: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void subirModelo(String nombre, String precioStr, String stockStr, String urlImagen) {
        StorageReference fileRef = mStorage.child("productos_modelos/" + UUID.randomUUID().toString() + ".glb");
        fileRef.putFile(uriModelo).addOnSuccessListener(taskSnapshot -> {
            fileRef.getDownloadUrl().addOnSuccessListener(urlModelo -> {
                guardarEnDatabase(nombre, precioStr, stockStr, urlImagen, urlModelo.toString());
            });
        }).addOnFailureListener(e -> {
            ocultarCarga();
            Toast.makeText(this, "Fallo al subir modelo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void guardarEnDatabase(String nombre, String precioStr, String stockStr, String urlImagen, String urlModelo) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnGuardar.setEnabled(false);
        
        String descripcion = binding.etDescripcion.getText().toString().trim();
        double precio = Double.parseDouble(precioStr);
        int stock = Integer.parseInt(stockStr);
        
        int pos = binding.spinnerSucursal.getSelectedItemPosition();
        String sucursalId = listaSucursales.get(pos).getId();

        // Si es edición usamos el ID existente, si no, generamos uno nuevo
        String finalId = esEdicion ? idEditar : mDatabase.child("productos").push().getKey();
        
        Productos producto = new Productos(nombre, "", descripcion, precio, stock, urlImagen, urlModelo, "", sucursalId);
        producto.setId(finalId);

        if (finalId != null) {
            mDatabase.child("productos").child(finalId).setValue(producto)
                .addOnSuccessListener(aVoid -> {
                    String msg = esEdicion ? "Producto actualizado" : "Producto registrado exitosamente";
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    ocultarCarga();
                    Toast.makeText(this, "Error final: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        }
    }

    private void ocultarCarga() {
        binding.progressBar.setVisibility(View.GONE);
        binding.btnGuardar.setEnabled(true);
    }
}
