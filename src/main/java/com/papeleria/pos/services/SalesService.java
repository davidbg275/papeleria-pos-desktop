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
