package com.example.inventario_ra.ui.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.inventario_ra.databinding.FragmentAgregarBinding;
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

public class AgregarFragment extends Fragment {

    private FragmentAgregarBinding binding;
    private DatabaseReference mDatabase;
    private StorageReference mStorage;

    private List<Sucursales> listaSucursales;
    private List<String> nombresSucursales;

    private Uri uriImagen;
    private Uri uriModelo;

    private String idEditar;
    private boolean esEdicion;
    private Productos productoAEditar;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    uriImagen = result.getData().getData();
                    binding.tvEstadoImagen.setText("Imagen seleccionada");
                }
            }
    );

    private final ActivityResultLauncher<Intent> modelPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    uriModelo = result.getData().getData();
                    binding.tvEstadoModelo.setText("Modelo 3D seleccionado");
                }
            }
    );

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAgregarBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        mStorage = FirebaseStorage.getInstance().getReference();

        listaSucursales = new ArrayList<>();
        nombresSucursales = new ArrayList<>();

        if (getArguments() != null) {
            idEditar = getArguments().getString("PRODUCTO_ID_EDITAR");
            esEdicion = (idEditar != null);
        }

        if (esEdicion) {
            binding.tvTituloFormulario.setText("Actualizar Producto");
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
                if (isAdded()) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_spinner_item, nombresSucursales);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    binding.spinnerSucursal.setAdapter(adapter);

                    if (esEdicion) {
                        cargarDatosProducto();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) Toast.makeText(requireContext(), "Error al cargar sucursales", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void cargarDatosProducto() {
        mDatabase.child("productos").child(idEditar).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                productoAEditar = snapshot.getValue(Productos.class);
                if (productoAEditar != null && isAdded()) {
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
                if (isAdded()) Toast.makeText(requireContext(), "Error al cargar producto", Toast.LENGTH_SHORT).show();
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
        intent.setType("*/*");
        modelPickerLauncher.launch(intent);
    }

    private void validarYSubir() {
        String nombre = binding.etNombre.getText().toString().trim();
        String precioStr = binding.etPrecio.getText().toString().trim();
        String stockStr = binding.etStock.getText().toString().trim();

        if (nombre.isEmpty() || precioStr.isEmpty() || stockStr.isEmpty()) {
            Toast.makeText(requireContext(), "Complete los campos obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        if (esEdicion && uriImagen == null && uriModelo == null && productoAEditar != null) {
            guardarEnDatabase(nombre, precioStr, stockStr,
                    productoAEditar.getImagen_ref_url(),
                    productoAEditar.getModelo_3d_url());
            return;
        }

        if (!esEdicion && (uriImagen == null || uriModelo == null)) {
            Toast.makeText(requireContext(), "Debe seleccionar la imagen y el modelo 3D", Toast.LENGTH_SHORT).show();
            return;
        }

        if (uriImagen != null) {
            subirImagen(nombre, precioStr, stockStr);
        } else if (uriModelo != null) {
            subirModelo(nombre, precioStr, stockStr, productoAEditar.getImagen_ref_url());
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
                    guardarEnDatabase(nombre, precioStr, stockStr, urlImagen.toString(), productoAEditar.getModelo_3d_url());
                }
            });
        }).addOnFailureListener(e -> {
            ocultarCarga();
            if (isAdded()) Toast.makeText(requireContext(), "Fallo al subir imagen: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void subirModelo(String nombre, String precioStr, String stockStr, String urlImagen) {
        if (!binding.btnGuardar.isEnabled()) {
            binding.progressBar.setVisibility(View.VISIBLE);
        }

        StorageReference fileRef = mStorage.child("productos_modelos/" + UUID.randomUUID().toString() + ".glb");
        fileRef.putFile(uriModelo).addOnSuccessListener(taskSnapshot -> {
            fileRef.getDownloadUrl().addOnSuccessListener(urlModelo -> {
                guardarEnDatabase(nombre, precioStr, stockStr, urlImagen, urlModelo.toString());
            });
        }).addOnFailureListener(e -> {
            ocultarCarga();
            if (isAdded()) Toast.makeText(requireContext(), "Fallo al subir modelo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void guardarEnDatabase(String nombre, String precioStr, String stockStr, String urlImagen, String urlModelo) {
        String descripcion = binding.etDescripcion.getText().toString().trim();
        double precio = Double.parseDouble(precioStr);
        int stock = Integer.parseInt(stockStr);

        int pos = binding.spinnerSucursal.getSelectedItemPosition();
        String sucursalId = listaSucursales.get(pos).getId();

        String finalId = esEdicion ? idEditar : mDatabase.child("productos").push().getKey();

        Productos producto = new Productos(nombre, "", descripcion, precio, stock, urlImagen, urlModelo, "", sucursalId);
        producto.setId(finalId);

        if (finalId != null) {
            mDatabase.child("productos").child(finalId).setValue(producto)
                    .addOnSuccessListener(aVoid -> {
                        if (isAdded()) {
                            String msg = esEdicion ? "Producto actualizado" : "Producto registrado exitosamente";
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                            Navigation.findNavController(requireView()).popBackStack();
                        }
                    })
                    .addOnFailureListener(e -> {
                        ocultarCarga();
                        if (isAdded()) Toast.makeText(requireContext(), "Error final: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void ocultarCarga() {
        if (binding != null) {
            binding.progressBar.setVisibility(View.GONE);
            binding.btnGuardar.setEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
