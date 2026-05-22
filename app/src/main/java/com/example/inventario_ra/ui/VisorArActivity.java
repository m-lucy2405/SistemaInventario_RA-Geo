package com.example.inventario_ra.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.inventario_ra.ar.ARModelNodeLoader;
import com.example.inventario_ra.databinding.ActivityVisorArBinding;
import com.example.inventario_ra.models.Productos;
import com.google.ar.core.Config;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Locale;

import io.github.sceneview.ar.node.ArModelNode;
import io.github.sceneview.ar.node.PlacementMode;
import kotlin.Unit;

/**
 * Controlador mejorado para la visualización de modelos en Realidad Aumentada.
 * Incluye guía de usuario (UX), información del producto y gestión de estados de colocación.
 */
public class VisorArActivity extends AppCompatActivity {

    private ActivityVisorArBinding binding;
    private ArModelNode modelNode;
    private DatabaseReference mDatabase;
    private boolean isModelPlaced = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVisorArBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mDatabase = FirebaseDatabase.getInstance().getReference("productos");

        // UI Listeners
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnReset.setOnClickListener(v -> resetearColocacion());
        binding.btnScaleUp.setOnClickListener(v -> ajustarEscala(0.05f));
        binding.btnScaleDown.setOnClickListener(v -> ajustarEscala(-0.05f));

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

        // Listener para posicionar el modelo manualmente al tocar una superficie
        binding.arSceneView.setOnTapAr((hitResult, motionEvent) -> {
            if (!isModelPlaced && modelNode != null) {
                modelNode.setAnchor(hitResult.createAnchor());
                isModelPlaced = true;
                actualizarInterfazUX();
                Toast.makeText(this, "Producto posicionado en superficie", Toast.LENGTH_SHORT).show();
            }
            return Unit.INSTANCE;
        });

        // Listener de fotogramas para actualizar instrucciones
        binding.arSceneView.setOnArFrame(arFrame -> {
            actualizarInstruccionesUX();
            return Unit.INSTANCE;
        });

        // Guía UX basada en calidad de rastreo (Fortalece la detección)
        binding.arSceneView.setOnArTrackingFailureChanged(reason -> {
            if (isModelPlaced) return Unit.INSTANCE;

            String mensaje = "Mueve el teléfono para detectar superficies";
            if (reason != null) {
                switch (reason) {
                    case BAD_STATE: mensaje = "Reiniciando cámara..."; break;
                    case EXCESSIVE_MOTION: mensaje = "Muévete más lento"; break;
                    case INSUFFICIENT_LIGHT: mensaje = "Necesitas más luz"; break;
                    case INSUFFICIENT_FEATURES: mensaje = "Apunta a una zona con textura (suelo/mesa)"; break;
                    case CAMERA_UNAVAILABLE: mensaje = "Cámara no disponible"; break;
                }
            }
            binding.tvInstruccionTexto.setText(mensaje);
            return Unit.INSTANCE;
        });
    }

    private void configurarEscena() {
        binding.arSceneView.getPlaneRenderer().setVisible(true);

        // Fortalecer la sesión de AR
        binding.arSceneView.configureSession((session, config) -> {
            // 1. Enfoque Automático: Ayuda a ver mejor las texturas del suelo
            config.setFocusMode(Config.FocusMode.AUTO);

            // 2. Depth API: Si el sensor lo permite, detecta profundidad real
            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                config.setDepthMode(Config.DepthMode.AUTOMATIC);
            }

            // 3. Estimación de Luz: Mejora el realismo y sombras
            config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);

            // 4. Instant Placement: Permite colocar objetos más rápido
            config.setInstantPlacementMode(Config.InstantPlacementMode.LOCAL_Y_UP);

            return Unit.INSTANCE;
        });

        modelNode = new ArModelNode(binding.arSceneView.getEngine());
        // PlacementMode.BEST_AVAILABLE aprovecha planos e Instant Placement
        modelNode.setPlacementMode(PlacementMode.BEST_AVAILABLE);
        
        // Habilitar manipulación por gestos (Mover, Rotar, Escalar con los dedos)
        modelNode.setEditable(true);
        
        binding.arSceneView.addChild(modelNode);
    }

    private void cargarDatosDesdeFirebase(String id) {
        binding.loaderAr.setVisibility(View.VISIBLE);
        mDatabase.child(id).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Productos producto = snapshot.getValue(Productos.class);
                if (producto != null) {
                    actualizarTarjetaInfo(producto);
                    if (producto.getModelo_3d_url() != null && !producto.getModelo_3d_url().isEmpty()) {
                        prepararModelo(producto);
                    } else {
                        Toast.makeText(VisorArActivity.this, "El producto no tiene un modelo 3D disponible", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(VisorArActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void actualizarTarjetaInfo(Productos p) {
        binding.tvNombreVisor.setText(p.getNombre());
        binding.tvCategoriaVisor.setText(p.getCategoria());
        binding.tvPrecioVisor.setText(String.format(Locale.getDefault(), "$ %.2f", p.getPrecio()));
    }

    private void prepararModelo(Productos producto) {
        ARModelNodeLoader.cargarModelo(modelNode, producto.getModelo_3d_url(), new ARModelNodeLoader.ARModelLoaderCallback() {
            @Override
            public void onSuccess() {
                binding.loaderAr.setVisibility(View.GONE);
            }

            @Override
            public void onError(Exception error) {
                binding.loaderAr.setVisibility(View.GONE);
                Toast.makeText(VisorArActivity.this, "Error al cargar modelo 3D", Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }

    private void actualizarInstruccionesUX() {
        if (isModelPlaced) {
            binding.cardInstrucciones.setVisibility(View.GONE);
            return;
        }

        binding.cardInstrucciones.setVisibility(View.VISIBLE);
        // Cambiar icono e instrucciones sutilmente si detecta que puede colocar
        // Nota: SceneView 0.10.0 maneja la renderización de planos, si el usuario ve puntos/red, sabe que puede tocar.
    }

    private void actualizarInterfazUX() {
        if (isModelPlaced) {
            binding.btnReset.setVisibility(View.VISIBLE);
            binding.layoutControlesEscala.setVisibility(View.VISIBLE);
            binding.cardInstrucciones.setVisibility(View.GONE);
        } else {
            binding.btnReset.setVisibility(View.GONE);
            binding.layoutControlesEscala.setVisibility(View.GONE);
            binding.cardInstrucciones.setVisibility(View.VISIBLE);
            binding.tvInstruccionTexto.setText("Mueve el teléfono para detectar superficies");
        }
    }

    private void ajustarEscala(float delta) {
        if (modelNode != null) {
            float escalaActual = modelNode.getScale().getX();
            float nuevaEscala = Math.max(0.05f, Math.min(3.0f, escalaActual + delta));
            modelNode.setScale(nuevaEscala);
        }
    }

    private void resetearColocacion() {
        if (modelNode != null) {
            modelNode.detachAnchor();
            isModelPlaced = false;
            actualizarInterfazUX();
            Toast.makeText(this, "Puedes posicionar el modelo nuevamente", Toast.LENGTH_SHORT).show();
        }
    }
}
