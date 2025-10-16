package com.papeleria.pos.models;

public class RecipeItem {
    private String sku;
    private String nombre;
    private double cantidadBase; // en unidad base

    public RecipeItem() {}

    public RecipeItem(String sku, String nombre, double cantidadBase) {
        this.sku = sku;
        this.nombre = nombre;
        this.cantidadBase = cantidadBase;
    }

    public String getSku() { return sku; }
    public String getNombre() { return nombre; }
    public double getCantidadBase() { return cantidadBase; }

    public void setSku(String sku) { this.sku = sku; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setCantidadBase(double cantidadBase) { this.cantidadBase = cantidadBase; }
}
