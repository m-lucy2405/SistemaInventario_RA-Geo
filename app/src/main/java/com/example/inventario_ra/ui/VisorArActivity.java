package com.example.inventario_ra.ui;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.inventario_ra.ar.ARModelNodeLoader;
import com.example.inventario_ra.databinding.ActivityVisorArBinding;
import com.example.inventario_ra.models.Productos;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import io.github.sceneview.ar.node.ArModelNode;
import io.github.sceneview.ar.node.PlacementMode;
import kotlin.Unit;

/**
 * Controlador limpio para la visualización de modelos en Realidad Aumentada.
 */
public class VisorArActivity extends AppCompatActivity {

    private ActivityVisorArBinding binding;
    private ArModelNode modelNode;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVisorArBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mDatabase = FirebaseDatabase.getInstance().getReference("productos");

        // Configuración inicial de la escena AR
        configurarEscena();

        // Obtención del ID del producto desde el Intent
        String productoId = getIntent().getStringExtra("PRODUCTO_ID");

        if (productoId != null && !productoId.isEmpty()) {
            cargarDatosDesdeFirebase(productoId);
        } else {
            Toast.makeText(this, "ID de producto no válido", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Listener para posicionar el modelo
        binding.arSceneView.setOnTapAr((hitResult, motionEvent) -> {
            if (modelNode != null) {
                modelNode.anchor();
                Toast.makeText(this, "Producto anclado", Toast.LENGTH_SHORT).show();
            }
            return Unit.INSTANCE;
        });
    }

    private void configurarEscena() {
        binding.arSceneView.getPlaneRenderer().setVisible(true);
        modelNode = new ArModelNode(binding.arSceneView.getEngine());
        modelNode.setPlacementMode(PlacementMode.BEST_AVAILABLE);
        binding.arSceneView.addChild(modelNode);
    }

    private void cargarDatosDesdeFirebase(String id) {
        mDatabase.child(id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Productos producto = snapshot.getValue(Productos.class);
                if (producto != null && producto.getModelo_3d_url() != null && !producto.getModelo_3d_url().isEmpty()) {
                    prepararModelo(producto);
                } else {
                    Toast.makeText(VisorArActivity.this, "El producto no tiene un modelo 3D disponible", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(VisorArActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void prepararModelo(Productos producto) {
        Toast.makeText(this, "Preparando: " + producto.getNombre(), Toast.LENGTH_SHORT).show();
        ARModelNodeLoader.cargarModelo(modelNode, producto.getModelo_3d_url(), new ARModelNodeLoader.ARModelLoaderCallback() {
            @Override
            public void onSuccess() {
                // Modelo cargado exitosamente
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(VisorArActivity.this, "Error al cargar modelo 3D", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
