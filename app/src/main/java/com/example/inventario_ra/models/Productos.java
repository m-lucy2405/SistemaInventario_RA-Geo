package com.example.inventario_ra.models;

public class Productos {
    private String id; // Este lo usamos localmente para saber el ID del nodo (ej. prod_99823)
    private String nombre;
    private String categoria;
    private String descripcion;
    private Object precio;
    private int stock;
    private String imagen_ref_url;
    private String modelo_3d_url;
    private String ubicacion_interna;
    private String sucursal_id;

    public Productos() {
    }

    // Constructor con parámetros (opcional, pero útil para crear productos desde la app)
    public Productos(String nombre, String categoria, String descripcion, Object precio,
                    int stock, String imagen_ref_url, String modelo_3d_url,
                    String ubicacion_interna, String sucursal_id) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.descripcion = descripcion;
        this.precio = precio;
        this.stock = stock;
        this.imagen_ref_url = imagen_ref_url;
        this.modelo_3d_url = modelo_3d_url;
        this.ubicacion_interna = ubicacion_interna;
        this.sucursal_id = sucursal_id;
    }

    // Getters y Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public double getPrecio() {
        // Firebase es flexible: un número puede guardarse como String o como Number.
        // Validamos el tipo para evitar errores de fundición (ClassCastException) en tiempo de ejecución.
        if (precio instanceof String) {
            try {
                return Double.parseDouble((String) precio);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        } else if (precio instanceof Number) {
            return ((Number) precio).doubleValue();
        }
        return 0.0;
    }

    public void setPrecio(Object precio) {
        this.precio = precio;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getImagen_ref_url() {
        return imagen_ref_url;
    }

    public void setImagen_ref_url(String imagen_ref_url) {
        this.imagen_ref_url = imagen_ref_url;
    }

    public String getModelo_3d_url() {
        return modelo_3d_url;
    }

    public void setModelo_3d_url(String modelo_3d_url) {
        this.modelo_3d_url = modelo_3d_url;
    }

    public String getUbicacion_interna() {
        return ubicacion_interna;
    }

    public void setUbicacion_interna(String ubicacion_interna) {
        this.ubicacion_interna = ubicacion_interna;
    }

    public String getSucursal_id() {
        return sucursal_id;
    }

    public void setSucursal_id(String sucursal_id) {
        this.sucursal_id = sucursal_id;
    }
}
