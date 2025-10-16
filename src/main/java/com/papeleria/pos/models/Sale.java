package com.papeleria.pos.models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Sale {
    private String id;
    private LocalDateTime fecha;
    private List<SaleItem> items = new ArrayList<>();
    private double total;
    private double efectivo;
    private double cambio;

    public Sale() {
        this.fecha = LocalDateTime.now();
    }

    public Sale(String id) {
        this.id = id;
        this.fecha = LocalDateTime.now();
    }

    public String getId() { return id; }
    public LocalDateTime getFecha() { return fecha; }
    public List<SaleItem> getItems() { return items; }
    public double getTotal() { return total; }
    public double getEfectivo() { return efectivo; }
    public double getCambio() { return cambio; }

    public void setId(String id) { this.id = id; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public void setItems(List<SaleItem> items) {
        this.items = (items == null) ? new ArrayList<>() : items;
        recalcular();
    }

    public void setEfectivo(double efectivo) {
        this.efectivo = efectivo;
        this.cambio = this.efectivo - total;
    }

    public void agregarItem(SaleItem item){
        if (item != null) {
            items.add(item);
            recalcular();
        }
    }

    /** Compatibilidad con código existente que llama setTotal(...).
     *  Mantiene consistente el campo cambio. */
    public void setTotal(double total) {
        this.total = total;
        this.cambio = this.efectivo - total;
    }

    public void recalcular(){
        total = items.stream()
                     .mapToDouble(SaleItem::getSubtotal)
                     .sum();
        this.cambio = this.efectivo - total;
    }
}

