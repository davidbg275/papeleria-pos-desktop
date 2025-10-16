#!/usr/bin/env bash
# rollback_min_estable.sh
# Restaura compatibilidad: InventoryService.findBySku -> Optional<Product>,
# SalesService usa Optional, y ProductionView usa .orElse(null)

set -euo pipefail
cd "${1:-$HOME/Documentos/papeleria-pos-desktop}"

echo ">> Restaurando InventoryService.findBySku(...) a Optional<Product>"

# 1) InventoryService.findBySku -> Optional<Product>
#   (cubre variantes con tipo importado simple Product o fully-qualified)
perl -0777 -i -pe '
  s/public\s+Product\s+findBySku\s*\(\s*String\s+sku\s*\)\s*\{[^}]*\}/
public java.util.Optional<com.papeleria.pos.models.Product> findBySku(String sku) {
    java.util.List<com.papeleria.pos.models.Product> all = list();
    for (com.papeleria.pos.models.Product p : all) {
        if (p.getSku() != null && p.getSku().equalsIgnoreCase(sku)) {
            return java.util.Optional.of(p);
        }
    }
    return java.util.Optional.empty();
}/gs
' src/main/java/com/papeleria/pos/services/InventoryService.java 2>/dev/null || true

perl -0777 -i -pe '
  s/public\s+Product\s+findBySku\s*\(\s*String\s+sku\s*\)\s*\{[^}]*\}/
public java.util.Optional<Product> findBySku(String sku) {
    java.util.List<Product> all = list();
    for (Product p : all) {
        if (p.getSku() != null && p.getSku().equalsIgnoreCase(sku)) {
            return java.util.Optional.of(p);
        }
    }
    return java.util.Optional.empty();
}/gs
' src/main/java/com/papeleria/pos/services/InventoryService.java 2>/dev/null || true

# Si ya estaba con Optional pero con cuerpo distinto, lo dejamos; arriba solo reescribe si encontró la versión "Product".

echo ">> Reescribiendo SalesService con Optional<Product> (compatible con llamadas existentes)"

cat > src/main/java/com/papeleria/pos/services/SalesService.java <<'EOF'
package com.papeleria.pos.services;

import com.papeleria.pos.models.Product;
import com.papeleria.pos.models.Sale;
import com.papeleria.pos.models.SaleItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Servicio de ventas compatible con Optional<Product> (Java 17). */
public class SalesService {

    private final InventoryService inventory;
    private final StorageService storage;
    private final EventBus bus;

    public SalesService(StorageService storage, InventoryService inventory, EventBus bus) {
        this.inventory = inventory;
        this.storage = storage;
        this.bus = bus;
    }
    public SalesService(InventoryService inventory, StorageService storage, EventBus bus) {
        this(storage, inventory, bus);
    }
    public SalesService(InventoryService inventory, EventBus bus) {
        this(new StorageService(Path.of("").toAbsolutePath()), inventory, bus);
    }

    /** Verifica si hay stock suficiente en unidad base. */
    public boolean validarStock(String sku, double cantidadBase) {
        return inventory.findBySku(sku)
                .map(p -> p.getStock() >= cantidadBase)
                .orElse(false);
    }

    /** Cobra, descuenta stock, guarda venta, genera ticket, publica eventos. */
    public void cobrarYGuardar(Sale sale) {
        // Recalcular totales
        double total = 0.0;
        for (SaleItem it : sale.getItems()) total += it.getSubtotal();
        sale.setTotal(total);
        sale.setFecha(LocalDateTime.now());

        // Descontar stock
        for (SaleItem it : sale.getItems()) {
            inventory.findBySku(it.getSku()).ifPresent(p -> {
                inventory.adjustStock(p.getSku(), -it.getCantidadBase());
            });
        }

        // Guardar venta
        List<Sale> all = new ArrayList<>(storage.loadSales());
        all.add(sale);
        storage.saveSales(all);

        // Ticket
        generarTicketTxt(sale);

        // Eventos
        if (bus != null) {
            bus.publish(EventBus.Topic.SALES_CHANGED, "SALE");
            bus.publish(EventBus.Topic.INVENTORY_CHANGED, "SALE");
        }
    }

    private void generarTicketTxt(Sale s) {
        try {
            Path dir = storage.getTicketsDir();
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            StringBuilder sb = new StringBuilder();
            sb.append("Ticket: ").append(s.getId()).append("\n")
              .append("Fecha: ").append(s.getFecha()==null? "": s.getFecha().format(f)).append("\n")
              .append("----------------------------------------\n");
            for (SaleItem it : s.getItems()) {
                sb.append(String.format("%-22s %6.2f x %6.2f = %7.2f\n",
                        it.getNombre(), it.getCantidadBase(), it.getPrecioUnitario(), it.getSubtotal()));
            }
            sb.append("----------------------------------------\n")
              .append(String.format("TOTAL: %,.2f\n", s.getTotal()))
              .append(String.format("EFECTIVO: %,.2f\n", s.getEfectivo()))
              .append(String.format("CAMBIO: %,.2f\n", Math.max(0, s.getEfectivo() - s.getTotal())));

            Path out = dir.resolve("ticket-" + s.getId() + ".txt");
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) { }
    }
}
EOF

echo ">> Reescribiendo ProductionService mínimo (no depende de Optional)"

cat > src/main/java/com/papeleria/pos/services/ProductionService.java <<'EOF'
package com.papeleria.pos.services;

public class ProductionService {
    private final InventoryService inventory;
    private final EventBus bus;

    public ProductionService(InventoryService inventory, EventBus bus) {
        this.inventory = inventory;
        this.bus = bus;
    }

    public InventoryService getInventory() { return inventory; }
    public EventBus getBus() { return bus; }
}
EOF

echo ">> Ajustando ProductionView para usar .orElse(null) con findBySku(...)"

# Línea: Product inv = inventory.findBySku(i.prod.getSku());
# ->     Product inv = inventory.findBySku(i.prod.getSku()).orElse(null);
sed -E -i 's/inventory\.findBySku\(([^)]+)\);/inventory.findBySku(\1).orElse(null);/' src/main/java/com/papeleria/pos/views/ProductionView.java

echo ">> Limpiando posibles residuos de cambios anteriores…"
# Si en algún otro archivo quedó llamado a findBySku(...).orElse(null) es válido; no tocamos más.

echo ">> Recompilando…"
mvn -q clean package

echo
echo "======================================"
echo "Rollback aplicado. Ejecuta: mvn -q javafx:run"
echo "======================================"

