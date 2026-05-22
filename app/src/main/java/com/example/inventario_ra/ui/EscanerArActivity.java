package com.example.inventario_ra.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.inventario_ra.ar.ARModelNodeLoader;
import com.example.inventario_ra.databinding.ActivityEscanerArBinding;
import com.example.inventario_ra.models.Productos;
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.TrackingState;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import io.github.sceneview.ar.arcore.ArFrame;
import io.github.sceneview.ar.node.ArModelNode;
import io.github.sceneview.ar.node.PlacementMode;
import kotlin.Unit;

/**
 * Escáner AR de alto rendimiento que utiliza IDs Semánticos para consultas O(1) en Firebase.
 */
public class EscanerArActivity extends AppCompatActivity {

    private ActivityEscanerArBinding binding;
    private DatabaseReference mDatabase;
    private Set<String> productosProcesados;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEscanerArBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mDatabase = FirebaseDatabase.getInstance().getReference("productos");
        productosProcesados = new HashSet<>();

        configurarEscenaEscaner();

        // Listener de cada frame para rastreo de imágenes
        binding.arSceneView.setOnArFrame(this::procesarFrame);
    }

    private void configurarEscenaEscaner() {
        binding.arSceneView.getPlaneRenderer().setVisible(false);

        binding.arSceneView.configureSession((session, config) -> {
            AugmentedImageDatabase aid = new AugmentedImageDatabase(session);
            
            try {
                // Listar dinámicamente todos los archivos en assets
                String[] files = getAssets().list("");
                if (files != null) {
                    for (String fileName : files) {
                        // Filtrar solo imágenes compatibles
                        if (fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".png")) {
                            try (InputStream is = getAssets().open(fileName)) {
                                Bitmap bitmap = BitmapFactory.decodeStream(is);
                                aid.addImage(fileName, bitmap);
                                Log.d("AR_SCANNER", "Marcador cargado dinámicamente: " + fileName);
                            } catch (IOException e) {
                                Log.e("AR_SCANNER", "Error abriendo asset: " + fileName);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("AR_SCANNER", "Error listando assets", e);
            }

            config.setAugmentedImageDatabase(aid);
            config.setFocusMode(Config.FocusMode.AUTO);
            return Unit.INSTANCE;
        });
    }

    private Unit procesarFrame(ArFrame arFrame) {
        Collection<AugmentedImage> updatedImages = arFrame.getFrame().getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage image : updatedImages) {
            String fileName = image.getName();
            TrackingState state = image.getTrackingState();
            
            actualizarDebugStatus("Marcador: " + fileName + " [" + state + "]");

            if (state == TrackingState.TRACKING) {
                // Extraer el ID semántico (quitar extensión)
                String productoId = fileName;
                int dotIndex = fileName.lastIndexOf('.');
                if (dotIndex > 0) {
                    productoId = fileName.substring(0, dotIndex);
                }

                // Control de redundancia: consultar solo si no ha sido procesado
                if (!productosProcesados.contains(productoId)) {
                    actualizarDebugStatus("Detectado: " + productoId + ". Consultando Firebase...");
                    productosProcesados.add(productoId);
                    buscarProductoYAnclar(image, productoId);
                }
            }
        }
        return Unit.INSTANCE;
    }

    private void actualizarDebugStatus(String mensaje) {
        runOnUiThread(() -> {
            if (binding != null) {
                binding.tvDebugStatus.setText(mensaje);
            }
        });
    }

    private void buscarProductoYAnclar(AugmentedImage image, String productoId) {
        binding.progressBar.setVisibility(View.VISIBLE);

        // Consulta Directa O(1) usando el ID Semántico
        mDatabase.child(productoId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                binding.progressBar.setVisibility(View.GONE);
                
                if (snapshot.exists()) {
                    actualizarDebugStatus("Producto " + productoId + " encontrado. Descargando 3D...");
                    Productos producto = snapshot.getValue(Productos.class);
                    if (producto != null && producto.getModelo_3d_url() != null) {
                        producto.setId(snapshot.getKey()); // Asegurar el ID
                        crearNodoYAnclar(image, producto);
                    }
                } else {
                    actualizarDebugStatus("ERROR: ID " + productoId + " no existe en Firebase");
                    Log.w("AR_SCANNER", "ID de producto no encontrado en Firebase: " + productoId);
                    // Opcional: remover de procesados para permitir reintento posterior si se desea
                    // productosProcesados.remove(productoId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                binding.progressBar.setVisibility(View.GONE);
                actualizarDebugStatus("ERROR Firebase: " + error.getMessage());
                productosProcesados.remove(productoId);
            }
        });
    }

    private void crearNodoYAnclar(AugmentedImage image, Productos producto) {
        ArModelNode modelNode = new ArModelNode(binding.arSceneView.getEngine());
        modelNode.setPlacementMode(PlacementMode.BEST_AVAILABLE);
        
        Anchor anchor = image.createAnchor(image.getCenterPose());
        modelNode.setAnchor(anchor);
        
        binding.arSceneView.addChild(modelNode);

        ARModelNodeLoader.cargarModelo(modelNode, producto.getModelo_3d_url(), new ARModelNodeLoader.ARModelLoaderCallback() {
            @Override
            public void onSuccess() {
                actualizarDebugStatus("Modelo de " + producto.getNombre() + " renderizado");
                runOnUiThread(() -> 
                    Toast.makeText(EscanerArActivity.this, "Detectado: " + producto.getNombre(), Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onError(Exception error) {
                actualizarDebugStatus("ERROR Renderizado: " + error.getMessage());
                Log.e("AR_SCANNER", "Error cargando modelo de " + producto.getNombre());
                binding.arSceneView.removeChild(modelNode);
                // Permitir reintento si falla la carga del modelo 3D
                productosProcesados.remove(producto.getId());
            }
        });
    }
}
