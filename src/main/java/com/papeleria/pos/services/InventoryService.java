package com.papeleria.pos.services;

import com.papeleria.pos.models.Product;
import org.apache.poi.ss.usermodel.Row;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class InventoryService {
    private final StorageService storage;
    private final EventBus bus;

    public InventoryService(StorageService storage, EventBus bus) {
        this.storage = storage;
        this.bus = bus;
    }

    /* ======================= Lectura / Búsqueda ======================= */

    /** Siempre lee desde disco para evitar caché desactualizado en otras vistas. */
    public synchronized List<Product> list() {
        return storage.loadProducts();
    }

    public synchronized List<Product> search(String q) {
        if (q == null || q.isBlank())
            return list();
        String s = q.toLowerCase(Locale.ROOT);
        return list().stream().filter(p -> (p.getSku() != null && p.getSku().toLowerCase(Locale.ROOT).contains(s)) ||
                (p.getNombre() != null && p.getNombre().toLowerCase(Locale.ROOT).contains(s)) ||
                (p.getCategoria() != null && p.getCategoria().toLowerCase(Locale.ROOT).contains(s)))
                .collect(Collectors.toList());
    }

    public synchronized Optional<Product> findBySku(String sku) {
        if (sku == null)
            return Optional.empty();
        String target = sku.trim();
        for (Product p : list()) {
            if (p.getSku() != null && p.getSku().equalsIgnoreCase(target))
                return Optional.of(p);
        }
        return Optional.empty();
    }

    /* ======================= Escritura / Mutaciones ======================= */

    public synchronized void upsert(Product np) {
        if (np == null || np.getSku() == null || np.getSku().isBlank())
            return;

        // Normaliza campos y precio a 2 decimales
        np.setPrecio(round2(np.getPrecio()));
        if (np.getContenido() < 0)
            np.setContenido(0);
        if (np.getStock() < 0)
            np.setStock(0);

        List<Product> all = new ArrayList<>(list());

        // Reemplazo por SKU case-insensitive
        int idx = indexOfSku(all, np.getSku());
        if (idx >= 0) {
            Product e = all.get(idx);
            e.setNombre(np.getNombre());
            e.setCategoria(np.getCategoria());
            e.setUnidad(np.getUnidad());
            e.setContenido(np.getContenido());
            e.setPrecio(round2(np.getPrecio()));
            e.setStock(np.getStock());
        } else {
            all.add(np);
        }

        saveAndNotify(all, "upsert:" + np.getSku());
    }

    public synchronized void removeBySku(String sku) {
        if (sku == null || sku.isBlank())
            return;
        List<Product> all = new ArrayList<>(list());
        all.removeIf(p -> p.getSku() != null && p.getSku().equalsIgnoreCase(sku));
        saveAndNotify(all, "delete:" + sku);
    }

    public synchronized void clearAll() {
        saveAndNotify(new ArrayList<>(), "clear");
    }

    public synchronized void adjustStock(String sku, double delta) {
        if (sku == null || sku.isBlank() || delta == 0)
            return;
        List<Product> all = new ArrayList<>(list());
        int idx = indexOfSku(all, sku);
        if (idx >= 0) {
            Product p = all.get(idx);
            p.setStock(Math.max(0, p.getStock() + delta));
            saveAndNotify(all, "adjust:" + sku);
        }
    }

    /* ======================= Importación Excel ======================= */

    public synchronized int importFromExcel(Path xlsxPath) throws IOException {
        if (xlsxPath == null || !Files.exists(xlsxPath))
            throw new IOException("Archivo no encontrado");
        try (var in = Files.newInputStream(xlsxPath);
                var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            var sheet = wb.getSheetAt(0);
            if (sheet == null)
                return 0;

            // Carga actual en mapa por SKU case-insensitive
            Map<String, Product> map = new LinkedHashMap<>();
            for (Product p : list())
                map.put(keySku(p.getSku()), p);

            int count = 0;
            int rowIndex = 0;
            for (Row row : sheet) {
                rowIndex++;
                if (rowIndex == 1)
                    continue; // header

                var cSku = row.getCell(0);
                if (cSku == null)
                    continue;
                String sku = cSku.toString().trim();
                if (sku.isEmpty())
                    continue;

                String nombre = getString(row, 1);
                String categoria = getString(row, 2);
                String unidad = getString(row, 3);
                double contenido = getDouble(row, 4);
                double precio = getDouble(row, 5);
                double stock = getDouble(row, 6);

                Product np = new Product(sku, nombre, categoria, unidad, contenido, round2(precio),
                        stock < 0 ? 0 : stock);
                map.put(keySku(sku), np);
                count++;
            }

            List<Product> merged = new ArrayList<>(map.values());
            saveAndNotify(merged, "bulk-import");
            return count;
        }
    }

    private String getString(Row row, int idx) {
        var c = row.getCell(idx);
        return c == null ? "" : c.toString().trim();
    }

    private double getDouble(Row row, int idx) {
        var c = row.getCell(idx);
        if (c == null)
            return 0.0;
        try {
            switch (c.getCellType()) {
                case NUMERIC:
                    return c.getNumericCellValue();
                case STRING:
                default:
                    String s = c.toString().trim().replace(",", ".");
                    return s.isEmpty() ? 0.0 : Double.parseDouble(s);
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    /* ======================= Utilidades de conversión ======================= */

    /** Convierte cantidad en UNIDAD del producto a su unidad base para stock. */
    public synchronized double toBase(Product p, double qtyInProductUnit) {
        if (p == null)
            return 0.0;
        String u = p.getUnidad() == null ? "" : p.getUnidad().trim().toLowerCase(Locale.ROOT);
        double c = Math.max(0.0, p.getContenido());
        if (u.equals("paquete") || u.equals("caja") || u.equals("rollo"))
            return c > 0 ? qtyInProductUnit * c : qtyInProductUnit;
        // m / metro / metros y "unidad" ya se consideran base
        return qtyInProductUnit;
    }

    /** Texto legible de equivalencia base. */
    public synchronized String prettyBase(Product p, double qtyInProductUnit) {
        double base = toBase(p, qtyInProductUnit);
        String u = p.getUnidad() == null ? "" : p.getUnidad().trim().toLowerCase(Locale.ROOT);
        if (u.equals("rollo"))
            return base >= 1 ? String.format(Locale.US, "≈ %.2f m", base)
                    : String.format(Locale.US, "≈ %.0f cm", base * 100);
        String name = p.getNombre() == null ? "" : p.getNombre().toLowerCase(Locale.ROOT);
        String menor = name.contains("hoja") ? "hojas" : "pzas";
        return String.format(Locale.US, "≈ %.0f %s", base, menor);
    }

    /* ======================= Helpers internos ======================= */

    private int indexOfSku(List<Product> list, String sku) {
        if (sku == null)
            return -1;
        for (int i = 0; i < list.size(); i++) {
            Product p = list.get(i);
            if (p.getSku() != null && p.getSku().equalsIgnoreCase(sku))
                return i;
        }
        return -1;
    }

    private String keySku(String sku) {
        return sku == null ? "" : sku.trim().toLowerCase(Locale.ROOT);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** Guarda ordenado por nombre y emite INVENTORY_CHANGED. */
    private void saveAndNotify(List<Product> all, String reason) {
        // orden consistente por nombre y luego SKU
        all.sort(Comparator.comparing(
                (Product p) -> p.getNombre() == null ? "" : p.getNombre().toLowerCase(Locale.ROOT))
                .thenComparing(p -> p.getSku() == null ? "" : p.getSku().toLowerCase(Locale.ROOT)));

        storage.saveProducts(all);
        if (bus != null)
            bus.publish(EventBus.Topic.INVENTORY_CHANGED, reason);
    }
}
