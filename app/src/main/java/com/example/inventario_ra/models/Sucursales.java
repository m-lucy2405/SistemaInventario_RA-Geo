package com.example.inventario_ra.models;

public class Sucursales {
    private String id;
    private String nombre;
    private double latitud;
    private double longitud;
    private int radio_metros;
    private String descripcion;

    // Constructor vacío requerido por Firebase Realtime Database
    public Sucursales() {
    }

    public Sucursales(String id, String nombre, double latitud, double longitud, int radio_metros, String descripcion) {
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
        return latitud;
    }

    public void setLatitud(double latitud) {
        this.latitud = latitud;
    }

    public double getLongitud() {
        return longitud;
    }

    public void setLongitud(double longitud) {
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
