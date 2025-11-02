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

    public List<Product> list() {
        return storage.loadProducts();
    }

    public List<Product> search(String q) {
        if (q == null || q.isBlank())
            return list();
        String s = q.toLowerCase();
        return list().stream().filter(p -> (p.getSku() != null && p.getSku().toLowerCase().contains(s)) ||
                (p.getNombre() != null && p.getNombre().toLowerCase().contains(s)) ||
                (p.getCategoria() != null && p.getCategoria().toLowerCase().contains(s))).collect(Collectors.toList());
    }

    public Optional<Product> findBySku(String sku) {
        for (Product p : list()) {
            if (p.getSku() != null && p.getSku().equalsIgnoreCase(sku))
                return Optional.of(p);
        }
        return Optional.empty();
    }

    public void upsert(Product np) {
        List<Product> all = list();
        Optional<Product> ex = all.stream().filter(p -> p.getSku().equals(np.getSku())).findFirst();
        if (ex.isPresent()) {
            Product e = ex.get();
            e.setNombre(np.getNombre());
            e.setCategoria(np.getCategoria());
            e.setUnidad(np.getUnidad());
            e.setContenido(np.getContenido());
            e.setPrecio(round2(np.getPrecio()));
            e.setStock(np.getStock());
        } else {
            all.add(np);
        }
        storage.saveProducts(all);
        bus.publish(EventBus.Topic.INVENTORY_CHANGED, null);
    }

    public void removeBySku(String sku) {
        List<Product> all = list();
        all.removeIf(p -> Objects.equals(p.getSku(), sku));
        storage.saveProducts(all);
        bus.publish(EventBus.Topic.INVENTORY_CHANGED, null);
    }

    public void clearAll() {
        storage.saveProducts(new ArrayList<>());
        bus.publish(EventBus.Topic.INVENTORY_CHANGED, null);
    }

    public void adjustStock(String sku, double delta) {
        List<Product> all = list();
        for (Product p : all) {
            if (Objects.equals(p.getSku(), sku)) {
                p.setStock(Math.max(0, p.getStock() + delta));
                break;
            }
        }
        storage.saveProducts(all);
        bus.publish(EventBus.Topic.INVENTORY_CHANGED, null);
    }

    // ===== Importar desde Excel (.xlsx) =====
    public int importFromExcel(Path xlsxPath) throws IOException {
        if (xlsxPath == null || !Files.exists(xlsxPath))
            throw new IOException("Archivo no encontrado");
        java.io.InputStream in = Files.newInputStream(xlsxPath);
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(in)) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = wb.getSheetAt(0);
            if (sheet == null)
                return 0;

            Map<String, Product> map = new LinkedHashMap<>();
            for (Product p : list())
                map.put(p.getSku(), p);

            int count = 0;
            int rowIndex = 0;
            for (Row row : sheet) {
                rowIndex++;
                if (rowIndex == 1)
                    continue; // header
                org.apache.poi.ss.usermodel.Cell cSku = row.getCell(0);
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

                Product np = new Product(sku, nombre, categoria, unidad, contenido, precio, stock);
                map.put(sku, np);
                count++;
            }
            storage.saveProducts(new ArrayList<>(map.values()));
            bus.publish(EventBus.Topic.INVENTORY_CHANGED, null);
            return count;
        }
    }

    private String getString(Row row, int idx) {
        org.apache.poi.ss.usermodel.Cell c = row.getCell(idx);
        return c == null ? "" : c.toString().trim();
    }

    private double getDouble(Row row, int idx) {
        org.apache.poi.ss.usermodel.Cell c = row.getCell(idx);
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

    // ===== Conversión y pretty =====
    /** Convierte cantidad en UNIDAD del producto a su unidad base para stock. */
    public double toBase(Product p, double qtyInProductUnit) {
        String u = p.getUnidad() == null ? "" : p.getUnidad().trim().toLowerCase();
        double c = Math.max(0.0, p.getContenido());
        if (u.equals("paquete") || u.equals("caja") || u.equals("rollo"))
            return c > 0 ? qtyInProductUnit * c : qtyInProductUnit;
        return qtyInProductUnit; // m/metro/metros o unidad ya están en base
    }

    /** Texto legible de equivalencia base. */
    public String prettyBase(Product p, double qtyInProductUnit) {
        double base = toBase(p, qtyInProductUnit);
        String u = p.getUnidad() == null ? "" : p.getUnidad().trim().toLowerCase();
        if (u.equals("rollo"))
            return base >= 1 ? String.format("≈ %.2f m", base) : String.format("≈ %.0f cm", base * 100);
        String name = p.getNombre() == null ? "" : p.getNombre().toLowerCase();
        String menor = name.contains("hoja") ? "hojas" : "pzas";
        return String.format("≈ %.0f %s", base, menor);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

}
