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
