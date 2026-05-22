package com.example.inventario_ra.ar;

import io.github.sceneview.ar.node.ArModelNode;
import kotlin.Unit;

/**
 * Clase utilitaria para la carga asíncrona de modelos AR.
 */
public class ARModelNodeLoader {

    public interface ARModelLoaderCallback {
        void onSuccess();
        void onError(Exception error);
    }

    public static void cargarModelo(ArModelNode modelNode, String url, ARModelLoaderCallback callback) {
        if (modelNode == null || url == null || url.isEmpty()) {
            if (callback != null) callback.onError(new Exception("Parámetros inválidos"));
            return;
        }

        // loadModelGlbAsync: Descarga y renderiza el modelo en segundo plano (Asíncrono).
        modelNode.loadModelGlbAsync(
                url,            // URL remota del archivo .glb (optimizado para RA).
                true,           // autoAnimate: Reproduce automáticamente animaciones si el modelo las tiene.
                0.3f,           // scaleUnits: Escala del modelo. 1.0f = tamaño real (metros). 0.3f = 30cm aprox.
                null,           // centerOrigin: Alineación del modelo respecto a su anclaje.
                error -> {
                    if (callback != null) callback.onError(error);
                    return Unit.INSTANCE;
                },
                modelInstance -> {
                    // El modelo se hace visible solo cuando la GPU ha terminado de procesar sus mallas.
                    modelNode.setVisible(true);
                    if (callback != null) callback.onSuccess();
                    return Unit.INSTANCE;
                }
        );
    }
}
