package com.papeleria.pos.services;

import com.papeleria.pos.models.Product;
import java.util.List;

/** Servicio de producción con validación, descuento y alta de PF. */
public class ProductionService {
    private final InventoryService inventory;
    private final EventBus bus;
    private final StorageService storage; // opcional para futuras recetas

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

    /** Fabrica: valida stock en base, descuenta, crea/actualiza PF y suma stock. */
    public boolean produce(String nombreFinal,
            String skuFinal,
            int piezasPorLote,
            int numeroLotes,
            List<InsumoReq> insumos,
            double costoExtraPorLote,
            Double[] precioSugeridoOut) {
        if (nombreFinal == null || nombreFinal.isBlank() || piezasPorLote <= 0 || numeroLotes <= 0)
            return false;
        double veces = numeroLotes;

        // 1) Validar stock
        for (InsumoReq in : insumos) {
            var pOpt = inventory.findBySku(in.sku());
            if (pOpt.isEmpty())
                return false;
            var p = pOpt.get();
            double reqBase = inventory.toBase(p, in.qtyInProductUnit() * veces);
            if (p.getStock() + 1e-9 < reqBase)
                return false;
        }

        // 2) Descontar insumos
        for (InsumoReq in : insumos) {
            var p = inventory.findBySku(in.sku()).get();
            double reqBase = inventory.toBase(p, in.qtyInProductUnit() * veces);
            inventory.adjustStock(p.getSku(), -reqBase);
        }

        // 3) Crear/actualizar producto final y sumar stock
        Product pf = inventory.search(nombreFinal).stream()
                .filter(p -> p.getNombre() != null && p.getNombre().equalsIgnoreCase(nombreFinal))
                .findFirst().orElse(null);
        if (pf == null) {
            String sku = (skuFinal != null && !skuFinal.isBlank()) ? skuFinal
                    : ("PF-" + Math.abs(nombreFinal.hashCode() % 100000));
            pf = new Product(sku, nombreFinal, "Producción", "pza", 0.0, 0.0, 0.0);
            inventory.upsert(pf);
        }
        double incremento = (double) piezasPorLote * veces;
        inventory.adjustStock(pf.getSku(), incremento);

        // 4) Precio sugerido simple
        double costoMat = 0.0;
        for (InsumoReq in : insumos) {
            var p = inventory.findBySku(in.sku()).get();
            costoMat += p.getPrecio() * in.qtyInProductUnit();
        }
        double costoUnit = (costoMat + costoExtraPorLote) / Math.max(1, piezasPorLote);
        if (precioSugeridoOut != null && precioSugeridoOut.length > 0)
            precioSugeridoOut[0] = costoUnit * 1.50; // 50%

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
