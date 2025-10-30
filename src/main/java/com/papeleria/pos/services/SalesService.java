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

    // Redondeo mexicano: múltiplos de $0.50, mínimo $0.50 si hay monto > 0
    private double roundMex(double value) {
        if (value <= 0)
            return 0.0;
        double half = Math.round(value * 2.0) / 2.0; // 0.50-steps
        if (half < 0.50)
            half = 0.50;
        return half;
    }

    /** Cobra, descuenta stock, guarda venta, genera ticket, publica eventos. */
    public void cobrarYGuardar(Sale sale) {
        // 1) Total crudo desde items
        double raw = 0.0;
        for (SaleItem it : sale.getItems())
            raw += it.getSubtotal();

        // 2) Redondeo a $0.50 (con mínimo $0.50 si hay monto)
        double totalRounded = roundMex(raw);

        // 3) Fecha y total (esto actualiza el 'cambio' dentro de Sale)
        sale.setFecha(java.time.LocalDateTime.now());
        sale.setTotal(totalRounded);

        // 4) Descontar stock (en unidad del producto)
        for (SaleItem it : sale.getItems()) {
            inventory.findBySku(it.getSku()).ifPresent(p -> {
                inventory.adjustStock(p.getSku(), -it.getCantidadBase());
            });
        }

        // 5) Guardar venta
        java.util.List<Sale> all = new java.util.ArrayList<>(storage.loadSales());
        all.add(sale);
        storage.saveSales(all);

        // 6) Ticket (con cambio redondeado a $0.50)
        generarTicketTxt(sale);

        // 7) Eventos
        if (bus != null) {
            bus.publish(EventBus.Topic.SALES_CHANGED, "SALE");
            bus.publish(EventBus.Topic.INVENTORY_CHANGED, "SALE");
        }
    }

    private void generarTicketTxt(Sale s) {
        try {
            java.nio.file.Path dir = storage.getTicketsDir();
            java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            StringBuilder sb = new StringBuilder();
            sb.append("Ticket: ").append(s.getId()).append("\n")
                    .append("Fecha: ").append(s.getFecha() == null ? "" : s.getFecha().format(f)).append("\n")
                    .append("----------------------------------------\n");
            for (SaleItem it : s.getItems()) {
                sb.append(String.format("%-22s %6.3f x %6.2f = %7.2f\n",
                        it.getNombre(), it.getCantidadBase(), it.getPrecioUnitario(), it.getSubtotal()));
            }
            sb.append("----------------------------------------\n");

            double total = s.getTotal();
            double cambioRed = roundMex(Math.max(0, s.getEfectivo() - total)); // 👈 redondeo a $0.50

            sb.append(String.format("TOTAL:    %,.2f\n", total))
                    .append(String.format("EFECTIVO: %,.2f\n", s.getEfectivo()))
                    .append(String.format("CAMBIO:   %,.2f\n", cambioRed));

            java.nio.file.Path out = dir.resolve("ticket-" + s.getId() + ".txt");
            java.nio.file.Files.writeString(out, sb.toString(), java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        } catch (java.io.IOException ignored) {
        }
    }

}
