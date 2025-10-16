package com.papeleria.pos.models;

public class SaleItem {
    private String sku;
    private String nombre;
    private double cantidadBase;   // en unidad base
    private double precioUnitario; // por unidad base
    private double subtotal;

    public SaleItem() {}

    public SaleItem(String sku, String nombre, double cantidadBase, double precioUnitario) {
        this.sku = sku;
        this.nombre = nombre;
        this.cantidadBase = cantidadBase;
        this.precioUnitario = precioUnitario;
        this.subtotal = cantidadBase * precioUnitario;
    }

    public String getSku() { return sku; }
    public String getNombre() { return nombre; }
    public double getCantidadBase() { return cantidadBase; }
    public double getPrecioUnitario() { return precioUnitario; }
    public double getSubtotal() { return subtotal; }

    public void setSku(String sku) { this.sku = sku; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setCantidadBase(double cantidadBase) { this.cantidadBase = cantidadBase; this.subtotal = cantidadBase * precioUnitario; }
    public void setPrecioUnitario(double precioUnitario) { this.precioUnitario = precioUnitario; this.subtotal = cantidadBase * precioUnitario; }
}
