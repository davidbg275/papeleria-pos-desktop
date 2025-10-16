package com.papeleria.pos.models;

public class Product {
    private String sku;        // código interno
    private String nombre;     // nombre amigable
    private String categoria;  // ej. Papelería
    private String unidad;     // pza, hoja, m, litro, etc.
    private double contenido;  // tamaño paquete/rollo (0 si no aplica)
    private double precio;     // precio por unidad base
    private double stock;      // stock en unidad base

    public Product() {}

    public Product(String sku, String nombre, String categoria, String unidad,
                   double contenido, double precio, double stock) {
        this.sku = sku;
        this.nombre = nombre;
        this.categoria = categoria;
        this.unidad = unidad;
        this.contenido = contenido;
        this.precio = precio;
        this.stock = stock;
    }

    public String getSku() { return sku; }
    public String getNombre() { return nombre; }
    public String getCategoria() { return categoria; }
    public String getUnidad() { return unidad; }
    public double getContenido() { return contenido; }
    public double getPrecio() { return precio; }
    public double getStock() { return stock; }

    public void setSku(String sku) { this.sku = sku; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public void setUnidad(String unidad) { this.unidad = unidad; }
    public void setContenido(double contenido) { this.contenido = contenido; }
    public void setPrecio(double precio) { this.precio = precio; }
    public void setStock(double stock) { this.stock = stock; }
}
