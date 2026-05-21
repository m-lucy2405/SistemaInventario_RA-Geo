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
 * Escáner AR libre que identifica productos mediante marcadores físicos
 * y carga automáticamente sus modelos 3D desde Firebase.
 */
public class EscanerArActivity extends AppCompatActivity {

    private ActivityEscanerArBinding binding;
    private DatabaseReference mDatabase;
    private Set<String> marcadoresProcesados;
    private ArModelNode currentModelNode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEscanerArBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mDatabase = FirebaseDatabase.getInstance().getReference("productos");
        marcadoresProcesados = new HashSet<>();

        configurarEscenaEscaner();

        // Listener de cada frame para rastreo de imágenes
        binding.arSceneView.setOnArFrame(this::procesarFrame);
    }

    private void configurarEscenaEscaner() {
        binding.arSceneView.getPlaneRenderer().setVisible(false); // No necesitamos planos para escaneo puro

        // Configuración de la base de datos de marcadores
        binding.arSceneView.configureSession((session, config) -> {
            AugmentedImageDatabase aid = new AugmentedImageDatabase(session);
            
            // Arreglo de marcadores reales en assets
            String[] marcadores = {
                "prod_ferr_001_martillo_de_carpintero.jpg",
                "prod_ferr_002_taladro.jpg",
                "prod_ferr_003_Casco.jpg",
                "prod_ferr_004_destornillador.jpg",
                "prod_ferr_005_Llave_tubos.jpg",
                "prod_ferr_006_escalera.jpg",
                "prod_ferr_007_Llave_Ajustable.jpg"
            };

            for (String nombreFichero : marcadores) {
                try (InputStream is = getAssets().open(nombreFichero)) {
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    aid.addImage(nombreFichero, bitmap);
                    Log.d("AR_SCANNER", "Marcador cargado: " + nombreFichero);
                } catch (IOException e) {
                    Log.e("AR_SCANNER", "Error cargando asset: " + nombreFichero);
                }
            }

            config.setAugmentedImageDatabase(aid);
            config.setFocusMode(Config.FocusMode.AUTO);
            return Unit.INSTANCE;
        });
    }

    private Unit procesarFrame(ArFrame arFrame) {
        Collection<AugmentedImage> updatedImages = arFrame.getFrame().getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage image : updatedImages) {
            if (image.getTrackingState() == TrackingState.TRACKING) {
                String nombreArchivo = image.getName();

                // Si la imagen ya está siendo procesada o mostrada, la omitimos
                if (marcadoresProcesados.contains(nombreArchivo)) continue;

                marcadoresProcesados.add(nombreArchivo);
                buscarProductoYAnclar(image);
            }
        }
        return Unit.INSTANCE;
    }

    private void buscarProductoYAnclar(AugmentedImage image) {
        // Limpiamos la extensión para buscar por nombre (ej: "taladro.jpg" -> "taladro")
        String terminoBusqueda = image.getName();
        if (terminoBusqueda.contains(".")) {
            terminoBusqueda = terminoBusqueda.substring(0, terminoBusqueda.lastIndexOf("."));
        }

        final String nombreProducto = terminoBusqueda;
        binding.progressBar.setVisibility(View.VISIBLE);

        // Consulta a Firebase por el nombre del producto
        mDatabase.orderByChild("nombre").equalTo(nombreProducto)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        binding.progressBar.setVisibility(View.GONE);
                        
                        if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                            // Obtenemos el primer producto que coincida
                            for (DataSnapshot data : snapshot.getChildren()) {
                                Productos producto = data.getValue(Productos.class);
                                if (producto != null && producto.getModelo_3d_url() != null) {
                                    crearNodoYAnclar(image, producto);
                                    break;
                                }
                            }
                        } else {
                            Log.w("AR_SCANNER", "Producto no encontrado en Firebase: " + nombreProducto);
                            marcadoresProcesados.remove(image.getName()); // Permitir reintento
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        binding.progressBar.setVisibility(View.GONE);
                        marcadoresProcesados.remove(image.getName());
                    }
                });
    }

    private void crearNodoYAnclar(AugmentedImage image, Productos producto) {
        // Creamos el nodo para este producto
        ArModelNode modelNode = new ArModelNode(binding.arSceneView.getEngine());
        modelNode.setPlacementMode(PlacementMode.BEST_AVAILABLE);
        
        // Posicionamos el modelo en el centro de la imagen física
        Anchor anchor = image.createAnchor(image.getCenterPose());
        modelNode.setAnchor(anchor);
        
        binding.arSceneView.addChild(modelNode);

        // Carga del modelo 3D
        ARModelNodeLoader.cargarModelo(modelNode, producto.getModelo_3d_url(), new ARModelNodeLoader.ARModelLoaderCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> 
                    Toast.makeText(EscanerArActivity.this, "Detectado: " + producto.getNombre(), Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onError(Exception error) {
                Log.e("AR_SCANNER", "Error cargando modelo de " + producto.getNombre());
                binding.arSceneView.removeChild(modelNode);
                marcadoresProcesados.remove(image.getName());
            }
        });
    }
}
