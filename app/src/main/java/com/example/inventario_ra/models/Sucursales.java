package com.example.inventario_ra.models;

public class Sucursales {
    private String id;
    private String nombre;
    private Object latitud;
    private Object longitud;
    private int radio_metros;
    private String descripcion;

    // Constructor vacío requerido por Firebase Realtime Database
    public Sucursales() {
    }

    public Sucursales(String id, String nombre, Object latitud, Object longitud, int radio_metros, String descripcion) {
        this.id = id;
        this.nombre = nombre;
        this.latitud = latitud;
        this.longitud = longitud;
        this.radio_metros = radio_metros;
        this.descripcion = descripcion;
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

    public double getLatitud() {
        if (latitud instanceof String) {
            try {
                return Double.parseDouble((String) latitud);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        } else if (latitud instanceof Number) {
            return ((Number) latitud).doubleValue();
        }
        return 0.0;
    }

    public void setLatitud(Object latitud) {
        this.latitud = latitud;
    }

    public double getLongitud() {
        if (longitud instanceof String) {
            try {
                return Double.parseDouble((String) longitud);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        } else if (longitud instanceof Number) {
            return ((Number) longitud).doubleValue();
        }
        return 0.0;
    }

    public void setLongitud(Object longitud) {
        this.longitud = longitud;
    }

    public int getRadio_metros() {
        return radio_metros;
    }

    public void setRadio_metros(int radio_metros) {
        this.radio_metros = radio_metros;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
