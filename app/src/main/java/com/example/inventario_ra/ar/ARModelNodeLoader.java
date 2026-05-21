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

        modelNode.loadModelGlbAsync(
                url,
                true,
                0.3f,
                null,
                error -> {
                    if (callback != null) callback.onError(error);
                    return Unit.INSTANCE;
                },
                modelInstance -> {
                    modelNode.setVisible(true);
                    if (callback != null) callback.onSuccess();
                    return Unit.INSTANCE;
                }
        );
    }
}
