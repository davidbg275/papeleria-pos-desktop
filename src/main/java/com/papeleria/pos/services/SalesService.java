package com.papeleria.pos.services;

import com.papeleria.pos.models.Role;

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

    private String generarTicketTxt(Sale s) {
        StringBuilder sb = new StringBuilder();
        try {
            java.nio.file.Path dir = storage.getTicketsDir();
            java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            sb.append("Ticket: ").append(s.getId()).append("\n")
                    .append("Fecha: ").append(s.getFecha() == null ? "" : s.getFecha().format(f)).append("\n")
                    .append("----------------------------------------\n");

            for (SaleItem it : s.getItems()) {
                sb.append(String.format("%-22s %6.3f x %6.2f = %7.2f\n",
                        it.getNombre(), it.getCantidadBase(), it.getPrecioUnitario(), it.getSubtotal()));
            }
            sb.append("----------------------------------------\n");

            double total = s.getTotal();
            double cambioRed = roundMex(Math.max(0, s.getEfectivo() - total));

            sb.append(String.format("TOTAL:    %,.2f\n", total))
                    .append(String.format("EFECTIVO: %,.2f\n", s.getEfectivo()))
                    .append(String.format("CAMBIO:   %,.2f\n", cambioRed));

            // Guarda el archivo
            java.nio.file.Path out = dir.resolve("ticket-" + s.getId() + ".txt");
            java.nio.file.Files.writeString(out, sb.toString(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

        } catch (Exception ignored) {
        }

        return sb.toString(); // 🔹 devolvemos el texto
    }

    // Cancela una venta y repone inventario. Requiere credenciales de ADMIN.
    public boolean cancelarVenta(String saleId, String adminUser, String adminPass) {
        // Validar credenciales y rol ADMIN usando los servicios existentes
        com.papeleria.pos.services.UserService us = new com.papeleria.pos.services.UserService(storage);
        if (!us.validate(adminUser, adminPass) || us.roleOf(adminUser) != com.papeleria.pos.models.Role.ADMIN) {
            return false;
        }

        java.util.List<com.papeleria.pos.models.Sale> ventas = new java.util.ArrayList<>(storage.loadSales());
        java.util.Optional<com.papeleria.pos.models.Sale> venta = ventas.stream()
                .filter(s -> s.getId() != null && s.getId().equals(saleId))
                .findFirst();
        if (venta.isEmpty())
            return false;

        // Reponer inventario
        for (com.papeleria.pos.models.SaleItem it : venta.get().getItems()) {
            inventory.adjustStock(it.getSku(), it.getCantidadBase());
        }

        // Eliminar venta y guardar
        ventas.remove(venta.get());
        storage.saveSales(ventas);

        // Borrar ticket si existe
        try {
            java.nio.file.Path t = storage.getTicketsDir().resolve("ticket-" + saleId + ".txt");
            java.nio.file.Files.deleteIfExists(t);
        } catch (Exception ignored) {
        }

        // Notificar
        if (bus != null) {
            bus.publish(EventBus.Topic.SALES_CHANGED, "CANCEL");
            bus.publish(EventBus.Topic.INVENTORY_CHANGED, "CANCEL");
        }
        return true;
    }

    public String cobrarYGuardarReturnTicket(Sale sale) {

        cobrarYGuardar(sale);
        return generarTicketTxt(sale);
    }

    // === Utilidades para historial en UI ===

    // Lista de ventas actuales (copia mutable para UI)
    public java.util.List<com.papeleria.pos.models.Sale> listSales() {
        return new java.util.ArrayList<>(storage.loadSales());
    }

    // Lee el ticket .txt de una venta para previsualizar en UI
    public String readTicket(String saleId) {
        try {
            java.nio.file.Path p = storage.getTicketsDir().resolve("ticket-" + saleId + ".txt");
            if (java.nio.file.Files.exists(p)) {
                return java.nio.file.Files.readString(p, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return "(Sin ticket generado o no disponible)";
    }

}
