package com.example.inventario_ra.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.example.inventario_ra.ar.ARModelNodeLoader;
import com.example.inventario_ra.databinding.ActivityEscanerArBinding;
import com.example.inventario_ra.models.Productos;
import com.google.ar.core.Anchor;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.exceptions.ImageInsufficientQualityException;
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
    private AugmentedImageDatabase aid;
    private int marcadoresCargados = 0;
    private int totalProductos = 0;
    private int descargasFinalizadas = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEscanerArBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mDatabase = FirebaseDatabase.getInstance().getReference("productos");
        productosProcesados = new HashSet<>();

        configurarEscenaEscaner();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Asegurar que el listener esté activo cada vez que la actividad vuelve al primer plano
        if (binding != null && binding.arSceneView != null) {
            binding.arSceneView.setOnArFrame(this::procesarFrame);
        }
    }

    private void configurarEscenaEscaner() {
        binding.arSceneView.getPlaneRenderer().setVisible(false);

        binding.arSceneView.configureSession((session, config) -> {
            // Inicializar base de datos vacía vinculada a la sesión
            aid = new AugmentedImageDatabase(session);
            config.setAugmentedImageDatabase(aid);
            config.setFocusMode(Config.FocusMode.AUTO);
            return Unit.INSTANCE;
        });

        // Iniciar descarga dinámica desde la nube
        descargarMarcadoresDesdeFirebase();
    }

    private void descargarMarcadoresDesdeFirebase() {
        actualizarDebugStatus("Sincronizando base de datos de marcadores...");
        
        mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    totalProductos = (int) snapshot.getChildrenCount();
                    descargasFinalizadas = 0;
                    marcadoresCargados = 0;
                    
                    if (totalProductos == 0) {
                        actualizarDebugStatus("No hay productos en la base de datos.");
                        return;
                    }

                    for (DataSnapshot data : snapshot.getChildren()) {
                        Productos producto = data.getValue(Productos.class);
                        if (producto != null && producto.getImagen_ref_url() != null) {
                            String productId = data.getKey();
                            descargarBitmapEInyectar(productId, producto.getImagen_ref_url());
                        } else {
                            verificarYFinalizarCarga();
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                actualizarDebugStatus("Error al sincronizar: " + error.getMessage());
            }
        });
    }

    private void descargarBitmapEInyectar(String productId, String url) {
        Glide.with(this)
                .asBitmap()
                .load(url)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        if (aid != null) {
                            try {
                                aid.addImage(productId, resource, 0.1f);
                                marcadoresCargados++;
                            } catch (ImageInsufficientQualityException e) {
                                Log.e("AR_SCANNER", "Baja calidad: " + productId);
                            } catch (Exception e) {
                                Log.e("AR_SCANNER", "Error: " + e.getMessage());
                            }
                        }
                        verificarYFinalizarCarga();
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) { }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        Log.e("AR_SCANNER", "Fallo descarga: " + productId);
                        verificarYFinalizarCarga();
                    }
                });
    }

    private void verificarYFinalizarCarga() {
        descargasFinalizadas++;
        actualizarDebugStatus("Sincronizando: " + descargasFinalizadas + "/" + totalProductos);

        if (descargasFinalizadas >= totalProductos) {
            // UNA SOLA ACTUALIZACIÓN DE SESIÓN AL FINAL
            binding.arSceneView.configureSession((session, config) -> {
                config.setAugmentedImageDatabase(aid);
                config.setFocusMode(Config.FocusMode.AUTO);
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                // Importante: Asegurar que el reconocimiento esté habilitado en la configuración
                return Unit.INSTANCE;
            });
            
            // Forzar el registro del listener después de configurar la sesión
            binding.arSceneView.setOnArFrame(this::procesarFrame);
            
            actualizarDebugStatus("Escáner Listo. " + marcadoresCargados + " marcadores activos.");
            Log.i("AR_SCANNER", "Sincronización finalizada. Marcadores: " + marcadoresCargados);
        }
    }

    private Unit procesarFrame(ArFrame arFrame) {
        Collection<AugmentedImage> updatedImages = arFrame.getFrame().getUpdatedTrackables(AugmentedImage.class);

        for (AugmentedImage image : updatedImages) {
            String productId = image.getName();
            TrackingState state = image.getTrackingState();
            
            Log.d("AR_TRACKING", "Imagen: " + productId + " | Estado: " + state);

            if (state == TrackingState.TRACKING) {
                if (!productosProcesados.contains(productId)) {
                    Log.i("AR_TRACKING", "¡IMAGEN RECONOCIDA!: " + productId);
                    actualizarDebugStatus("Detectado: " + productId + ". Consultando Firebase...");
                    productosProcesados.add(productId);
                    buscarProductoYAnclar(image, productId);
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

        // Consulta Directa O(1): Al usar el nombre de la imagen como clave del nodo en Realtime DB,
        // no necesitamos hacer una búsqueda (query) en toda la base, sino una ruta directa.
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
        Log.d("AR_RENDER", "Iniciando anclaje para: " + producto.getNombre());
        
        ArModelNode modelNode = new ArModelNode(binding.arSceneView.getEngine());
        modelNode.setPlacementMode(PlacementMode.BEST_AVAILABLE);
        
        // Importante: Usar el centro de la imagen detectada
        Anchor anchor = image.createAnchor(image.getCenterPose());
        modelNode.setAnchor(anchor);
        
        // Ajuste de escala por si el modelo es muy pequeño/grande
        modelNode.setScale(0.5f);
        
        binding.arSceneView.addChild(modelNode);

        Log.d("AR_RENDER", "Llamando a cargador de modelo: " + producto.getModelo_3d_url());

        ARModelNodeLoader.cargarModelo(modelNode, producto.getModelo_3d_url(), new ARModelNodeLoader.ARModelLoaderCallback() {
            @Override
            public void onSuccess() {
                Log.i("AR_RENDER", "Modelo cargado exitosamente: " + producto.getNombre());
                actualizarDebugStatus("Modelo de " + producto.getNombre() + " renderizado");
                runOnUiThread(() -> 
                    Toast.makeText(EscanerArActivity.this, "Detectado: " + producto.getNombre(), Toast.LENGTH_LONG).show()
                );
            }

            @Override
            public void onError(Exception error) {
                Log.e("AR_RENDER", "Error fatal cargando modelo: " + error.getMessage(), error);
                actualizarDebugStatus("ERROR Renderizado: " + error.getMessage());
                binding.arSceneView.removeChild(modelNode);
                productosProcesados.remove(producto.getId());
            }
        });
    }
}
