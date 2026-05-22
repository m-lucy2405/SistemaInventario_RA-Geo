package com.example.inventario_ra.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.inventario_ra.databinding.FragmentAgregarSucursalBinding;
import com.example.inventario_ra.models.Sucursales;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AgregarSucursalFragment extends Fragment {

    private FragmentAgregarSucursalBinding binding;
    private DatabaseReference mDatabase;
    private String idEditar;
    private boolean esEdicion = false;

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

        binding.btnGuardarSucursal.setOnClickListener(v -> validarYGuardar());
    }

    private void cargarDatosSucursal() {
        binding.progressBar.setVisibility(View.VISIBLE);
        mDatabase.child(idEditar).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                binding.progressBar.setVisibility(View.GONE);
                Sucursales sucursal = snapshot.getValue(Sucursales.class);
                if (sucursal != null) {
                    binding.etNombreSucursal.setText(sucursal.getNombre());
                    binding.etLatitud.setText(String.valueOf(sucursal.getLatitud()));
                    binding.etLongitud.setText(String.valueOf(sucursal.getLongitud()));
                    binding.etRadio.setText(String.valueOf(sucursal.getRadio_metros()));
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

    private void validarYGuardar() {
        String nombre = binding.etNombreSucursal.getText().toString().trim();
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

            guardarEnFirebase(nombre, latitud, longitud, radio);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "Ingrese valores numéricos válidos", Toast.LENGTH_SHORT).show();
        }
    }

    private void guardarEnFirebase(String nombre, double latitud, double longitud, int radio) {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnGuardarSucursal.setEnabled(false);

        String idFinal = esEdicion ? idEditar : mDatabase.push().getKey();
        Sucursales sucursal = new Sucursales(idFinal, nombre, latitud, longitud, radio, "");
        
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
