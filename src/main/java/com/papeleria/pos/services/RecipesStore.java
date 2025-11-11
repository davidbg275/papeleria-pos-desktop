package com.papeleria.pos.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class RecipesStore {

    public static class Item {
        public String sku;
        public double cantidadBase; // SIEMPRE en unidad base (pzas, hojas, m)
    }

    public static class Recipe {
        public String nombre;
        public String sku; // SKU del producto final sugerido
        public double margen; // 0 si no aplica
        public double precioDirecto; // 0 si no aplica
        public double manoObraUnit;
        public List<Item> items = new ArrayList<>();
    }

    private final Path file = Path.of("data", "recipes.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Type LIST = new TypeToken<List<Recipe>>() {
    }.getType();

    public synchronized List<Recipe> list() {
        try {
            if (!Files.exists(file))
                return new ArrayList<>();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<Recipe> rs = gson.fromJson(json, LIST);
            return rs == null ? new ArrayList<>() : rs;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public synchronized Optional<Recipe> getByName(String nombre) {
        return list().stream().filter(r -> r.nombre.equalsIgnoreCase(nombre)).findFirst();
    }

    public synchronized void upsert(Recipe r) {
        try {
            Files.createDirectories(file.getParent());
            List<Recipe> all = list();
            all.removeIf(x -> x.nombre.equalsIgnoreCase(r.nombre));
            all.add(r);
            all.sort(Comparator.comparing(a -> a.nombre.toLowerCase(Locale.ROOT)));
            Files.writeString(file, gson.toJson(all, LIST), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
        }
    }
}
