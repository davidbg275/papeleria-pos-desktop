package com.papeleria.pos.services;

import com.papeleria.pos.models.Product;
import java.util.List;
import java.util.Optional;

/**
 * Produce: valida en BASE, descuenta en unidad del producto y crea PF con
 * esquema del inventario.
 */
public class ProductionService {
    private final InventoryService inventory;
    private final EventBus bus;
    private final StorageService storage; // no usado

    public ProductionService(InventoryService inventory, EventBus bus) {
        this(inventory, bus, null);
    }

    public ProductionService(InventoryService inventory, EventBus bus, StorageService storage) {
        this.inventory = inventory;
        this.bus = bus;
        this.storage = storage;
    }

    public record InsumoReq(String sku, double qtyInProductUnit) {
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public boolean produce(String nombreFinal,
            String skuFinal,
            int piezasPorLote,
            int numeroLotes,
            List<InsumoReq> insumos,
            double costoExtraPorLote,
            Double[] precioSugeridoOut) {

        if (nombreFinal == null || nombreFinal.isBlank() || piezasPorLote <= 0 || numeroLotes <= 0)
            return false;
        final int totalPzas = piezasPorLote * numeroLotes;

        // 1) validar stock en BASE
        for (InsumoReq in : insumos) {
            Optional<Product> pOpt = inventory.findBySku(in.sku());
            if (pOpt.isEmpty())
                return false;
            Product p = pOpt.get();
            double reqBase = inventory.toBase(p, in.qtyInProductUnit() * totalPzas);
            double stockBase = inventory.toBase(p, p.getStock());
            if (stockBase + 1e-9 < reqBase)
                return false;
        }

        // 2) descontar insumos en unidad del producto
        for (InsumoReq in : insumos) {
            Product p = inventory.findBySku(in.sku()).get();
            double delta = in.qtyInProductUnit() * totalPzas;
            inventory.adjustStock(p.getSku(), -delta);
        }

        // 3) PF con el formato del inventario: Unidad="Unidad", Contenido=1
        Product pf = null;
        if (skuFinal != null && !skuFinal.isBlank())
            pf = inventory.findBySku(skuFinal.trim()).orElse(null);
        if (pf == null) {
            pf = inventory.search(nombreFinal).stream()
                    .filter(x -> x.getNombre() != null && x.getNombre().equalsIgnoreCase(nombreFinal))
                    .findFirst().orElse(null);
        }
        if (pf == null) {
            String sku = (skuFinal != null && !skuFinal.isBlank()) ? skuFinal.trim()
                    : ("PF-" + Math.abs(nombreFinal.hashCode() % 100000));
            pf = new Product(
                    sku, // Código
                    nombreFinal, // Producto
                    "Producción", // Categoría
                    "Unidad", // Unidad (igual que tu tabla)
                    1.0, // Contenido = 1 pzas
                    0.0, // Precio (se fija afuera)
                    0.0 // Stock inicial
            );
            inventory.upsert(pf);
        } else {
            if (pf.getCategoria() == null || pf.getCategoria().isBlank())
                pf.setCategoria("Producción");
            pf.setUnidad("Unidad");
            pf.setContenido(1.0);
            pf.setPrecio(round2(pf.getPrecio()));
            pf.setStock(round2(pf.getStock()));
            inventory.upsert(pf);
        }

        // 4) sumar stock del PF en su unidad del producto
        inventory.adjustStock(pf.getSku(), totalPzas);

        // 5) precio sugerido para UI
        double costoMatUnit = 0.0;
        for (InsumoReq in : insumos) {
            Product p = inventory.findBySku(in.sku()).get();
            costoMatUnit += p.getPrecio() * in.qtyInProductUnit();
        }
        double costoUnitario = (costoMatUnit + costoExtraPorLote) / Math.max(1, piezasPorLote);
        if (precioSugeridoOut != null && precioSugeridoOut.length > 0) {
            precioSugeridoOut[0] = round2(costoUnitario * 1.50);
        }

        if (bus != null)
            bus.publish(EventBus.Topic.INVENTORY_CHANGED, "PRODUCTION");
        return true;
    }

    public InventoryService getInventory() {
        return inventory;
    }

    public EventBus getBus() {
        return bus;
    }
}
