package com.papeleria.pos.services;

import com.papeleria.pos.models.Product;
import java.util.List;

/**
 * Servicio de producción: valida en UNIDAD BASE y descuenta en UNIDAD DEL
 * PRODUCTO.
 */
public class ProductionService {
    private final InventoryService inventory;
    private final EventBus bus;
    private final StorageService storage; // sin uso por ahora

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

    /**
     * @param nombreFinal       nombre del producto final
     * @param skuFinal          SKU del producto final (si no existe se crea)
     * @param piezasPorLote     piezas resultantes por lote
     * @param numeroLotes       lotes a fabricar
     * @param insumos           cantidades POR PRODUCTO en UNIDAD DEL PRODUCTO de
     *                          cada insumo
     * @param costoExtraPorLote mano de obra total por lote
     * @param precioSugeridoOut salida opcional del precio unitario sugerido
     */
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

        // 1) Validación de stock en UNIDAD BASE
        for (InsumoReq in : insumos) {
            var pOpt = inventory.findBySku(in.sku());
            if (pOpt.isEmpty())
                return false;
            Product p = pOpt.get();

            // requerimiento total expresado en BASE
            double reqBase = inventory.toBase(p, in.qtyInProductUnit() * totalPzas);

            // stock convertido a BASE para comparar correctamente
            double stockBase = inventory.toBase(p, p.getStock());
            if (stockBase + 1e-9 < reqBase)
                return false;
        }

        // 2) Descuento de insumos en UNIDAD DEL PRODUCTO
        // adjustStock espera delta en la misma unidad en que se guarda el stock del
        // producto.
        for (InsumoReq in : insumos) {
            Product p = inventory.findBySku(in.sku()).get();
            double reqInProductUnit = in.qtyInProductUnit() * totalPzas;
            inventory.adjustStock(p.getSku(), -reqInProductUnit);
        }

        // 3) Crear o localizar producto final
        Product pf = null;
        if (skuFinal != null && !skuFinal.isBlank())
            pf = inventory.findBySku(skuFinal.trim()).orElse(null);
        if (pf == null) {
            pf = inventory.search(nombreFinal).stream()
                    .filter(x -> x.getNombre() != null && x.getNombre().equalsIgnoreCase(nombreFinal))
                    .findFirst().orElse(null);
        }
        if (pf == null) {
            String sku = (skuFinal != null && !skuFinal.isBlank())
                    ? skuFinal.trim()
                    : ("PF-" + Math.abs(nombreFinal.hashCode() % 100000));
            // Por defecto el PF se maneja en "pza"
            pf = new Product(sku, nombreFinal, "Producción", "pza", 0.0, 0.0, 0.0);
            inventory.upsert(pf);
        }

        // 4) Sumar stock del PF en su UNIDAD DEL PRODUCTO (pza)
        inventory.adjustStock(pf.getSku(), totalPzas);

        // 5) Precio sugerido simple: costo materiales por pieza + MO por pieza con
        // margen 50%
        double costoMatUnit = 0.0;
        for (InsumoReq in : insumos) {
            Product p = inventory.findBySku(in.sku()).get();
            // p.getPrecio() está definido por UNIDAD DEL PRODUCTO de p
            costoMatUnit += p.getPrecio() * in.qtyInProductUnit();
        }
        double costoUnitario = (costoMatUnit + costoExtraPorLote) / Math.max(1, piezasPorLote);
        if (precioSugeridoOut != null && precioSugeridoOut.length > 0) {
            precioSugeridoOut[0] = costoUnitario * 1.50;
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
