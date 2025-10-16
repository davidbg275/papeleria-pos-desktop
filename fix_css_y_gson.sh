#!/usr/bin/env bash
set -euo pipefail
cd "${1:-$HOME/Documentos/papeleria-pos-desktop}"

echo ">> Arreglando CSS (sin variables) y Gson LocalDateTime..."

# ========== CSS SIN VARIABLES (colores literales válidos para JavaFX) ==========
cat <<'EOF' > src/main/resources/css/app.css
/* ===== Tema claro tipo dashboard con alto contraste (SIN variables CSS) ===== */

/* Base general */
.root {
  -fx-background-color: #F5F7FB;
  -fx-font-family: "Inter", "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  -fx-text-fill: #0F172A;
}

/* Sidebar */
.sidebar {
  -fx-background-color: #081226;
  -fx-padding: 16;
  -fx-spacing: 14;
  -fx-pref-width: 76;
}
.nav-btn {
  -fx-background-color: transparent;
  -fx-text-fill: #A4B0C9;
  -fx-background-radius: 12;
  -fx-font-size: 18px;
  -fx-font-weight: 900;
  -fx-alignment: CENTER;
  -fx-padding: 12 0;
}
.nav-btn:hover { -fx-background-color: #0F203F; -fx-text-fill: #E6EDF9; }
.nav-btn.active {
  -fx-background-color: rgba(6,182,212,0.25);
  -fx-text-fill: #EAF8FB;
  -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 12, 0.25, 0, 2);
}

/* Página contenedora */
.page { -fx-padding: 16; }
.h1 { -fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: #0F172A; }
.subtle { -fx-text-fill: #5B6476; -fx-font-size: 13px; }

/* Cards y paneles */
.card, .panel, .cart, .kpi, .product-card {
  -fx-background-color: #FFFFFF;
  -fx-background-radius: 14;
  -fx-padding: 16;
  -fx-effect: dropshadow(gaussian, rgba(16,24,40,0.12), 22, 0.35, 0, 6);
  -fx-border-color: #D7DFEA;
  -fx-border-radius: 14;
}

/* Inputs */
.text-field, .combo-box-base, .spinner, .choice-box {
  -fx-background-color: #FFFFFF;
  -fx-background-radius: 10;
  -fx-border-color: #D7DFEA;
  -fx-border-radius: 10;
  -fx-padding: 10 12;
  -fx-text-fill: #0F172A;
  -fx-prompt-text-fill: #7E8799;
}

/* Botones */
.button {
  -fx-background-radius: 10;
  -fx-font-weight: 900;
  -fx-text-fill: #FFFFFF;
  -fx-padding: 10 14;
}
.button.primary { -fx-background-color: #0EA5E9; }
.button.success { -fx-background-color: #10B981; }
.button.danger  { -fx-background-color: #EF4444; }
.button.warn    { -fx-background-color: #F59E0B; }
.button.info    { -fx-background-color: #38BDF8; }
.button.ghost {
  -fx-background-color: transparent;
  -fx-text-fill: #0F172A;
  -fx-border-color: #D7DFEA;
  -fx-border-width: 1;
  -fx-border-radius: 10;
}
.button:disabled {
  -fx-opacity: 1.0;
  -fx-background-color: #E9EEF7;
  -fx-text-fill: #9AA4B2;
  -fx-border-color: #D7DFEA;
}

/* Tabla */
.table-view {
  -fx-background-color: #FFFFFF;
  -fx-background-radius: 14;
  -fx-border-color: #D7DFEA;
  -fx-border-radius: 14;
}
.table-view .column-header-background {
  -fx-background-color: #F3F6FC;
  -fx-background-radius: 14 14 0 0;
  -fx-border-color: #D7DFEA;
  -fx-border-width: 0 0 1 0;
}
.table-view .column-header, .table-view .filler { -fx-background-color: transparent; -fx-size: 42px; }
.table-row-cell:odd { -fx-background-color: #FAFBFF; }
.table-row-cell:selected { -fx-background-color: rgba(14,165,233,0.16); }
.table-cell { -fx-text-fill: #0F172A; -fx-padding: 10 12; }

/* Grid de productos (Ventas) */
.product-card { -fx-pref-width: 230; }
.product-card .name  { -fx-font-size: 14.5px; -fx-font-weight: 900; }
.product-card .code  { -fx-text-fill: #5B6476; }
.product-card .price { -fx-text-fill: #059669; -fx-font-weight: 900; }
.product-add { -fx-background-color: #0EA5E9; -fx-background-radius: 10; -fx-padding: 8 12; -fx-font-weight: 900; }

/* Carrito */
.cart { -fx-pref-width: 360; }
.cart .line { -fx-border-color: #D7DFEA; -fx-border-width: 0 0 1 0; -fx-padding: 8 0; }

/* KPIs */
.kpi-title { -fx-text-fill: #5B6476; -fx-font-size: 12.5px; }
.kpi-value { -fx-font-size: 22px; -fx-font-weight: 900; }

/* Alertas */
.alert { -fx-background-radius: 12; -fx-padding: 10 12; -fx-font-weight: 900; }
.alert-info    { -fx-background-color: rgba(56,189,248,.18); -fx-text-fill: #075985; }
.alert-warn    { -fx-background-color: rgba(245,158,11,.18); -fx-text-fill: #92400E; }
.alert-danger  { -fx-background-color: rgba(239,68,68,.18);  -fx-text-fill: #7F1D1D; }
.alert-success { -fx-background-color: rgba(16,185,129,.18); -fx-text-fill: #065F46; }
EOF

# ========== Gson LocalDateTime Adapter ==========
apply_java_patch () {
  cat <<'EOF' > src/main/java/com/papeleria/pos/services/StorageService.java
package com.papeleria.pos.services;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.papeleria.pos.models.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class StorageService {
    private final Path dataDir;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Gson con adaptadores para LocalDateTime (evita errores de módulos)
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (src, t, ctx) ->
                            src == null ? JsonNull.INSTANCE : new JsonPrimitive(ISO.format(src)))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonDeserializer<LocalDateTime>) (json, t, ctx) ->
                            (json == null || json.isJsonNull() || json.getAsString().isBlank())
                                    ? null : LocalDateTime.parse(json.getAsString(), ISO))
            .setPrettyPrinting()
            .create();

    private final Path usersPath;
    private final Path productsPath;
    private final Path salesPath;
    private final Path recipesPath;
    private final Path sessionPath;

    public StorageService(Path baseDir) {
        this.dataDir = baseDir.resolve("data");
        this.usersPath = dataDir.resolve("users.json");
        this.productsPath = dataDir.resolve("products.json");
        this.salesPath = dataDir.resolve("sales.json");
        this.recipesPath = dataDir.resolve("recipes.json");
        this.sessionPath = dataDir.resolve("session.json");
        ensureFiles();
    }

    private void ensureFiles() {
        try {
            if (Files.notExists(dataDir)) Files.createDirectories(dataDir);
            createIfMissing(usersPath, "[]");
            createIfMissing(productsPath, "[]");
            createIfMissing(salesPath, "[]");
            createIfMissing(recipesPath, "[]");
            createIfMissing(sessionPath, "{\"username\":\"\",\"role\":\"\"}");
        } catch (IOException e) {
            throw new RuntimeException("No se pudieron preparar archivos de datos", e);
        }
    }

    private void createIfMissing(Path path, String defaultContent) throws IOException {
        if (Files.notExists(path)) {
            Files.write(path, defaultContent.getBytes(StandardCharsets.UTF_8));
        }
    }

    // Tipados
    public List<Product> loadProducts(){
        try (Reader r = Files.newBufferedReader(productsPath, StandardCharsets.UTF_8)){
            Type t = new TypeToken<List<Product>>(){}.getType();
            List<Product> list = gson.fromJson(r, t);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e){
            return new ArrayList<>();
        }
    }

    public void saveProducts(List<Product> products){
        writeJson(productsPath, products);
    }

    public List<User> loadUsers(){
        try (Reader r = Files.newBufferedReader(usersPath, StandardCharsets.UTF_8)){
            Type t = new TypeToken<List<User>>(){}.getType();
            List<User> list = gson.fromJson(r, t);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e){
            return new ArrayList<>();
        }
    }

    public void saveUsers(List<User> users){
        writeJson(usersPath, users);
    }

    public List<Sale> loadSales(){
        try (Reader r = Files.newBufferedReader(salesPath, StandardCharsets.UTF_8)){
            Type t = new TypeToken<List<Sale>>(){}.getType();
            List<Sale> list = gson.fromJson(r, t);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e){
            return new ArrayList<>();
        }
    }

    public void saveSales(List<Sale> sales){
        writeJson(salesPath, sales);
    }

    public List<RecipeItem> loadRecipes(){
        try (Reader r = Files.newBufferedReader(recipesPath, StandardCharsets.UTF_8)){
            Type t = new TypeToken<List<RecipeItem>>(){}.getType();
            List<RecipeItem> list = gson.fromJson(r, t);
            return list != null ? list : new ArrayList<>();
        } catch (IOException e){
            return new ArrayList<>();
        }
    }

    public void saveRecipes(List<RecipeItem> recipes){
        writeJson(recipesPath, recipes);
    }

    public Map<String, String> loadSession(){
        try (Reader r = Files.newBufferedReader(sessionPath, StandardCharsets.UTF_8)){
            Type t = new TypeToken<Map<String,String>>(){}.getType();
            Map<String,String> map = gson.fromJson(r, t);
            return map != null ? map : new HashMap<>();
        } catch (IOException e){
            return new HashMap<>();
        }
    }

    public void saveSession(String username, String role){
        Map<String, String> m = new HashMap<>();
        m.put("username", username);
        m.put("role", role);
        writeJson(sessionPath, m);
    }

    private void writeJson(Path path, Object obj){
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)){
            gson.toJson(obj, w);
        } catch (IOException e){
            throw new RuntimeException("No se pudo escribir: " + path, e);
        }
    }

    public Path getTicketsDir(){
        Path p = dataDir.resolve("tickets");
        try { if (Files.notExists(p)) Files.createDirectories(p); } catch (IOException ignored) {}
        return p;
    }

    public Path getDataDir() { return dataDir; }
}
EOF
}
apply_java_patch

echo ">> Compilando..."
mvn -q clean package

echo
echo "========================================"
echo "Arreglos aplicados. Ejecuta:"
echo "mvn -q javafx:run"
echo "========================================"

