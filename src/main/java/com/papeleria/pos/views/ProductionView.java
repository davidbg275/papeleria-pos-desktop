package com.papeleria.pos.views;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.papeleria.pos.components.AlertBanner;
import com.papeleria.pos.components.AutoCompleteCombo;
import com.papeleria.pos.models.Product;
import com.papeleria.pos.models.RecipeItem;
import com.papeleria.pos.services.EventBus;
import com.papeleria.pos.services.InventoryService;
import com.papeleria.pos.services.ProductionService;
import com.papeleria.pos.services.SessionService;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Armar Productos: presentaciones correctas, recetas y precio directo/sugerido.
 */
public class ProductionView extends BorderPane {

    // Servicios
    private final SessionService session;
    private final ProductionService production;
    private final InventoryService inventory;
    private final EventBus bus;

    // Host para banners
    private VBox bannerHost;

    // Paso 1
    private final TextField nombreFinal = new TextField();
    private final TextField skuFinal = new TextField();

    // Paso 2
    private final Spinner<Integer> cantidadPorLote = new Spinner<>(1, 1_000_000, 1, 1);
    private final Spinner<Integer> numeroLotes = new Spinner<>(1, 1_000_000, 1, 1);
    private final Label totalAFabricar = new Label("1");

    // Paso 3
    private final ComboBox<Product> material = new ComboBox<>();
    private final ChoiceBox<String> presentacion = new ChoiceBox<>();
    private final Spinner<Double> cantPorProducto = new Spinner<>(0.01, 1_000_000.0, 1.0, 1.0);
    private final Label disponibleLbl = new Label("—");
    private final FlowPane listaInsumos = new FlowPane(10, 10);
    private final ObservableList<Insumo> insumos = FXCollections.observableArrayList();

    // Resumen
    private final Label kCostoMat = new Label("$0.00");
    private final Label kCostoUnit = new Label("$0.00");
    private final Label kPrecioSug = new Label("$0.00");
    private final Spinner<Double> manoObraUnit = new Spinner<>(0.0, 1_000_000.0, 0.0, 1.0);
    private final Spinner<Double> margen = new Spinner<>(0.0, 1000.0, 50.0, 1.0);
    private final TextField precioDirecto = new TextField();

    private final Gson gson = new Gson();

    public ProductionView(SessionService session,
            ProductionService production,
            InventoryService inventory,
            EventBus bus) {
        this.session = session;
        this.production = production;
        this.inventory = inventory;
        this.bus = bus;

        setPadding(new Insets(12));

        // Encabezado
        Label titulo = new Label("Armar Productos");
        titulo.getStyleClass().add("h1");
        Label subt = new Label("Crea productos usando tus materiales disponibles");
        subt.getStyleClass().add("subtle");
        HBox pasos = stepper();
        VBox header = new VBox(6, titulo, subt, pasos);
        setTop(header);
        this.bannerHost = header;

        // Página
        VBox page = new VBox(12);
        page.getStyleClass().add("page");
        page.getChildren().addAll(
                cardPaso1Producto(),
                cardPaso2Cantidades(),
                cardPaso3Materiales(),
                cardResumenCostos());

        ScrollPane sc = new ScrollPane(page);
        sc.setFitToWidth(true);
        sc.setStyle("-fx-background-color: transparent;");
        setCenter(sc);

        // Orientation LTR
        nombreFinal.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        skuFinal.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        material.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        material.getEditor().setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

        // Eventos
        cantidadPorLote.valueProperty().addListener((o, a, v) -> recalc());
        numeroLotes.valueProperty().addListener((o, a, v) -> recalc());
        manoObraUnit.valueProperty().addListener((o, a, v) -> recalc());
        margen.valueProperty().addListener((o, a, v) -> recalc());
        precioDirecto.textProperty().addListener((o, a, v) -> recalc());
        bus.subscribe(EventBus.Topic.INVENTORY_CHANGED, e -> javafx.application.Platform.runLater(this::recalc));

        cargarCatalogo();
        recalc();
    }

    // ===== Layout =====
    private HBox stepper() {
        HBox s = new HBox(20);
        s.getStyleClass().add("stepper");
        s.getChildren().addAll(step("1", "Elegir producto", true), sep(), step("2", "Agregar materiales", false),
                sep(), step("3", "Fabricar", false));
        return s;
    }

    private Region sep() {
        Region r = new Region();
        r.setMinWidth(18);
        r.getStyleClass().add("step-sep");
        return r;
    }

    private VBox step(String n, String t, boolean active) {
        Label num = new Label(n);
        num.getStyleClass().addAll("step-num");
        if (active)
            num.getStyleClass().add("active");
        Label txt = new Label(t);
        txt.getStyleClass().add("step-text");
        return new VBox(2, num, txt);
    }

    private VBox cardPaso1Producto() {
        nombreFinal.setPromptText("Ej: Cuaderno personalizado");
        skuFinal.setPromptText("Ej: CUA-PERS-001");

        // Solo productos de categoría "Producción" como candidatos
        List<String> nombres = inventory.list().stream()
                .filter(p -> "Producción".equalsIgnoreCase(safe(p.getCategoria())))
                .map(p -> safe(p.getNombre()))
                .filter(s -> !s.isEmpty())
                .sorted()
                .toList();

        ComboBox<String> nombreCombo = new ComboBox<>(FXCollections.observableArrayList(nombres));
        nombreCombo.setEditable(true);
        new AutoCompleteCombo<>(cast(nombreCombo), nombres, s -> s);
        nombreCombo.getEditor().textProperty().bindBidirectional(nombreFinal.textProperty());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(8));
        grid.addRow(0, new Label("Nombre del producto"), nombreCombo);
        grid.addRow(1, new Label("Código del producto (SKU)"), skuFinal);

        return card("¿Qué producto quieres fabricar?", grid);
    }

    private VBox cardPaso2Cantidades() {
        totalAFabricar.getStyleClass().add("total-fabricar");
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(8));
        cantidadPorLote.setEditable(true);
        numeroLotes.setEditable(true);
        g.addRow(0, new Label("Cantidad por lote"), cantidadPorLote);
        g.addRow(1, new Label("Número de lotes"), numeroLotes);
        VBox wrap = new VBox(6, g, rightAligned(new Label("Total a fabricar: "), totalAFabricar));
        return card("¿Cuántos productos quieres fabricar?", wrap);
    }

    private VBox cardPaso3Materiales() {
        material.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Product p) {
                return p == null ? "" : p.getNombre() + " (" + p.getSku() + ")";
            }

            @Override
            public Product fromString(String s) {
                return inventory.list().stream()
                        .filter(p -> (p.getNombre() + " (" + p.getSku() + ")").equalsIgnoreCase(s))
                        .findFirst().orElse(null);
            }
        });
        new AutoCompleteCombo<>(material, FXCollections.observableArrayList(inventory.list()),
                p -> p.getNombre() + " (" + p.getSku() + ")");

        presentacion.getItems().setAll("Unidad");
        presentacion.getSelectionModel().selectFirst();

        cantPorProducto.setEditable(true);
        disponibleLbl.getStyleClass().add("ok-pill");

        Button agregar = new Button("+ Agregar Material");
        agregar.getStyleClass().add("primary");
        agregar.setOnAction(e -> agregarInsumo());

        Button guardarReceta = new Button("Guardar receta");
        guardarReceta.getStyleClass().add("ghost");
        guardarReceta.setOnAction(e -> saveRecipe(nombreFinal.getText().trim()));

        Button cargarReceta = new Button("Cargar receta");
        cargarReceta.getStyleClass().add("ghost");
        cargarReceta.setOnAction(e -> {
            var rs = loadRecipe(nombreFinal.getText().trim());
            if (rs.isEmpty()) {
                flash(AlertBanner.warn("Sin receta para ese nombre"));
                return;
            }
            insumos.clear();
            for (var r : rs) {
                Product p = inventory.findBySku(r.getSku()).orElse(null);
                if (p != null)
                    insumos.add(new Insumo(p, fromBaseToProductUnit(p, r.getCantidadBase())));
            }
            renderInsumos();
            recalc();
        });

        GridPane fila = new GridPane();
        fila.setHgap(12);
        fila.setVgap(10);
        fila.setPadding(new Insets(8));
        fila.addRow(0, new Label("Material"), material);
        fila.addRow(1, new Label("Presentación"), presentacion);
        fila.addRow(2, new Label("Cantidad por producto"), cantPorProducto);
        fila.addRow(3, new Label("Disponible"), disponibleLbl);

        material.valueProperty().addListener((o, a, v) -> {
            presentacion.getItems().clear();
            if (v != null) {
                presentacion.getItems().add(unidadLabel(v));
                String u = safe(v.getUnidad()).toLowerCase();
                double c = Math.max(0.0, v.getContenido());
                if (c > 0 && (u.equals("paquete") || u.equals("caja"))) {
                    presentacion.getItems().add(nombreMenor(v)); // Hoja o Pieza
                }
                if (c > 0 && u.equals("rollo")) {
                    if (!presentacion.getItems().contains("Metro"))
                        presentacion.getItems().add("Metro");
                    presentacion.getItems().add("Centímetro");
                }
                if (u.equals("m") || u.equals("metro") || u.equals("metros")) {
                    presentacion.getItems().add("Centímetro");
                }
            }
            if (presentacion.getItems().isEmpty())
                presentacion.getItems().add("Unidad");
            presentacion.getSelectionModel().selectFirst();
            updateDisponible();
        });
        cantPorProducto.valueProperty().addListener((o, a, v) -> updateDisponible());

        listaInsumos.setPrefWrapLength(520);

        HBox acciones = rightAligned(new Region(), new HBox(8, guardarReceta, cargarReceta, agregar));
        VBox cardBody = new VBox(10, fila, acciones, listaInsumos);

        return card("Materiales necesarios", cardBody);
    }

    private VBox cardResumenCostos() {
        VBox k1 = kpi("$0.00", "Costo de materiales\nPor cada producto");
        VBox k2 = kpi("$0.00", "Costo unitario total\nIncluye mano de obra");
        VBox k3 = kpi("$0.00", "Precio final\nDirecto o por margen");

        ((Label) ((VBox) k1.getChildren().get(0)).getChildren().get(0)).textProperty().bind(kCostoMat.textProperty());
        ((Label) ((VBox) k2.getChildren().get(0)).getChildren().get(0)).textProperty().bind(kCostoUnit.textProperty());
        ((Label) ((VBox) k3.getChildren().get(0)).getChildren().get(0)).textProperty().bind(kPrecioSug.textProperty());

        manoObraUnit.setEditable(true);
        margen.setEditable(true);
        precioDirecto.setPromptText("Precio unitario final (opcional)");

        HBox opts = new HBox(10, new Label("Margen %"), margen, new Region(),
                new Label("Precio directo"), precioDirecto);
        HBox.setHgrow(opts.getChildren().get(2), Priority.ALWAYS);

        HBox kpis = new HBox(12, k1, k2, k3);
        Button fabricar = new Button();
        fabricar.getStyleClass().add("success");
        fabricar.textProperty().bind(Bindings.createStringBinding(
                () -> "Fabricar " + totalAFabricar.getText(),
                totalAFabricar.textProperty()));
        fabricar.setOnAction(e -> fabricarAccion());

        return card("Resumen de costos", new VBox(12, kpis, opts, fabricar));
    }

    private VBox card(String title, Region content) {
        Label t = new Label(title);
        t.getStyleClass().add("card-title");
        VBox c = new VBox(10, t, content);
        c.getStyleClass().add("card");
        c.setPadding(new Insets(12));
        return c;
    }

    private HBox rightAligned(javafx.scene.Node left, javafx.scene.Node right) {
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox h = new HBox(8, left, sp, right);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private VBox kpi(String value, String subtitle) {
        Label v = new Label(value);
        v.getStyleClass().add("kpi-value");
        Label s = new Label(subtitle);
        s.getStyleClass().add("subtle");
        VBox inner = new VBox(4, v, s);
        VBox card = new VBox(inner);
        card.getStyleClass().add("soft-card");
        card.setPadding(new Insets(12));
        card.setPrefWidth(230);
        return card;
    }

    // ===== Lógica =====
    private void cargarCatalogo() {
        material.setItems(FXCollections.observableArrayList(inventory.list()));
    }

    private void agregarInsumo() {
        Product p = material.getValue();
        double qUser = cantPorProducto.getValue();
        if (p == null) {
            flash(AlertBanner.warn("Selecciona un material"));
            return;
        }
        if (qUser <= 0) {
            flash(AlertBanner.warn("Cantidad inválida"));
            return;
        }

        // Convertir desde presentación elegida a UNIDAD DEL PRODUCTO (exacto)
        double q = toProductUnit(p, qUser);

        Insumo in = new Insumo(p, q);
        for (Insumo x : new ArrayList<>(insumos)) {
            if (x.prod.getSku().equalsIgnoreCase(p.getSku())) {
                x.cantidadPorProducto += q;
                renderInsumos();
                recalc();
                limpiarEditor();
                return;
            }
        }
        insumos.add(in);
        renderInsumos();
        recalc();
        limpiarEditor();
    }

    private void limpiarEditor() {
        material.getSelectionModel().clearSelection();
        material.getEditor().clear();
        presentacion.getItems().setAll("Unidad");
        presentacion.getSelectionModel().selectFirst();
        cantPorProducto.getValueFactory().setValue(1.0);
        updateDisponible();
    }

    private void renderInsumos() {
        listaInsumos.getChildren().clear();
        for (Insumo i : insumos) {
            Label name = new Label(i.prod.getNombre());
            Label qty = new Label("× " + trim2(i.cantidadPorProducto));
            Label disp = new Label(disponibleTexto(i.prod, i.cantidadPorProducto));
            disp.getStyleClass().add(okClase(i));
            Button del = new Button("🗑");
            del.getStyleClass().add("danger");
            HBox chip = new HBox(10, name, qty, new Region(), disp, del);
            HBox.setHgrow(chip.getChildren().get(2), Priority.ALWAYS);
            chip.getStyleClass().addAll("chip", "card");
            chip.setAlignment(Pos.CENTER_LEFT);
            del.setOnAction(e -> {
                insumos.remove(i);
                renderInsumos();
                recalc();
            });
            listaInsumos.getChildren().add(chip);
        }
    }

    private void updateDisponible() {
        Product p = material.getValue();
        double qUser = cantPorProducto.getValue();
        double qProdUnit = p == null ? 0.0 : toProductUnit(p, qUser);
        disponibleLbl.setText(p == null ? "—" : disponibleTexto(p, qProdUnit));
    }

    // ======= FIX CLAVE: comparar en UNIDAD BASE y mostrar con etiqueta base
    // =======
    private String disponibleTexto(Product p, double qPorProducto_enUnidadDelProducto) {
        double reqBase = inventory.toBase(p, qPorProducto_enUnidadDelProducto
                * numeroLotes.getValue() * cantidadPorLote.getValue());

        // convertir el stock actual a BASE antes de comparar
        double stockBase = inventory.toBase(p,
                inventory.findBySku(p.getSku()).map(Product::getStock).orElse(p.getStock()));

        boolean ok = stockBase + 1e-9 >= reqBase;
        String labelBase = baseLabel(p);
        return trim2(stockBase) + " " + labelBase + "  " + (ok ? "✔" : "✖");
    }

    private String okClase(Insumo i) {
        double reqBase = inventory.toBase(i.prod,
                i.cantidadPorProducto * numeroLotes.getValue() * cantidadPorLote.getValue());
        double stockBase = inventory.toBase(i.prod,
                inventory.findBySku(i.prod.getSku()).map(Product::getStock).orElse(i.prod.getStock()));
        return (stockBase + 1e-9 >= reqBase) ? "ok-pill" : "warn-pill";
    }

    private void recalc() {
        int total = Math.max(1, cantidadPorLote.getValue()) * Math.max(1, numeroLotes.getValue());
        totalAFabricar.setText(String.valueOf(total));

        double costoMatUnit = 0.0;
        for (Insumo i : insumos)
            costoMatUnit += i.prod.getPrecio() * i.cantidadPorProducto;
        double costoUnitario = costoMatUnit + manoObraUnit.getValue();

        double precioDir = parseDouble(precioDirecto.getText(), -1.0);
        double sugerido = precioDir > 0 ? precioDir : costoUnitario * (1.0 + (margen.getValue() / 100.0));

        kCostoMat.setText(money(costoMatUnit));
        kCostoUnit.setText(money(costoUnitario));
        kPrecioSug.setText(money(sugerido));
    }

    private void fabricarAccion() {
        if (!session.isAdmin()) {
            flash(AlertBanner.warn("Solo ADMIN puede fabricar"));
            return;
        }
        String nombre = nombreFinal.getText() == null ? "" : nombreFinal.getText().trim();
        if (nombre.isEmpty()) {
            flash(AlertBanner.warn("Escribe el nombre del producto final"));
            return;
        }
        if (insumos.isEmpty()) {
            flash(AlertBanner.warn("Agrega al menos un material"));
            return;
        }

        List<ProductionService.InsumoReq> reqs = new java.util.ArrayList<>();
        for (Insumo in : insumos)
            reqs.add(new ProductionService.InsumoReq(in.prod.getSku(), in.cantidadPorProducto));

        Double[] sug = new Double[1];
        boolean ok = production.produce(
                nombre,
                skuFinal.getText(),
                cantidadPorLote.getValue(),
                numeroLotes.getValue(),
                reqs,
                manoObraUnit.getValue(),
                sug);

        if (!ok) {
            flash(AlertBanner.warn("Stock insuficiente o datos inválidos"));
            return;
        }

        // fijar precio final redondeado y normalizar PF
        double precioDir = parseDouble(precioDirecto.getText(), -1.0);
        double precioFinal = precioDir > 0 ? precioDir : (sug[0] == null ? 0.0 : sug[0]);
        precioFinal = Math.round(precioFinal * 100.0) / 100.0;

        com.papeleria.pos.models.Product pf = null;
        if (skuFinal.getText() != null && !skuFinal.getText().isBlank()) {
            pf = inventory.findBySku(skuFinal.getText().trim()).orElse(null);
        }
        if (pf == null) {
            pf = inventory.search(nombre).stream()
                    .filter(p -> p.getNombre() != null && p.getNombre().equalsIgnoreCase(nombre))
                    .findFirst().orElse(null);
        }
        if (pf != null) {
            pf.setPrecio(precioFinal);
            pf.setCategoria("Producción");
            pf.setUnidad("Unidad");
            pf.setContenido(1.0);
            inventory.upsert(pf);
        }

        flash(AlertBanner.success("Producción registrada"));
        insumos.clear();
        renderInsumos();
        recalc();
    }

    // ===== Conversiones de presentaciones =====
    private double toProductUnit(Product p, double cantidadSegunPresentacion) {
        String u = safe(p.getUnidad()).toLowerCase();
        String pres = presentacion.getValue() == null ? "" : presentacion.getValue();
        double c = Math.max(0.0, p.getContenido());

        switch (pres) {
            case "Paquete", "Caja", "Rollo", "Unidad":
                return cantidadSegunPresentacion; // ya está en unidad del producto
            case "Pieza", "Hoja":
                return c > 0 ? cantidadSegunPresentacion / c : cantidadSegunPresentacion;
            case "Metro":
                if (u.equals("rollo") && c > 0)
                    return cantidadSegunPresentacion / c;
                if (u.equals("m") || u.equals("metro") || u.equals("metros"))
                    return cantidadSegunPresentacion;
                return cantidadSegunPresentacion;
            case "Centímetro":
                double metros = cantidadSegunPresentacion / 100.0;
                if (u.equals("rollo") && c > 0)
                    return metros / c;
                if (u.equals("m") || u.equals("metro") || u.equals("metros"))
                    return metros;
                return metros;
            default:
                return cantidadSegunPresentacion;
        }
    }

    private double fromBaseToProductUnit(Product p, double base) {
        String u = safe(p.getUnidad()).toLowerCase();
        double c = Math.max(0.0, p.getContenido());
        if ((u.equals("paquete") || u.equals("caja") || u.equals("rollo")) && c > 0)
            return base / c;
        return base;
    }

    private String unidadLabel(Product p) {
        String u = safe(p.getUnidad()).toLowerCase();
        return switch (u) {
            case "paquete" -> "Paquete";
            case "caja" -> "Caja";
            case "rollo" -> "Rollo";
            case "m", "metro", "metros" -> "Metro";
            default -> "Unidad";
        };
    }

    private String nombreMenor(Product p) {
        String n = safe(p.getNombre()).toLowerCase();
        return n.contains("hoja") ? "Hoja" : "Pieza";
    }

    // etiqueta de unidad base para mostrar “Disponible”
    private String baseLabel(Product p) {
        String u = safe(p.getUnidad()).toLowerCase();
        if (u.equals("rollo") || u.equals("m") || u.equals("metro") || u.equals("metros"))
            return "m";
        if (u.equals("paquete") || u.equals("caja")) {
            // heurística por nombre
            String name = safe(p.getNombre()).toLowerCase();
            return name.contains("hoja") ? "hojas" : "pzas";
        }
        return "pzas";
    }

    // ===== Recetas locales data/recetas/*.json =====
    private Path recetasDir() {
        return Path.of("").toAbsolutePath().resolve("data").resolve("recetas");
    }

    private void saveRecipe(String nombreProducto) {
        try {
            if (nombreProducto == null || nombreProducto.isBlank()) {
                flash(AlertBanner.warn("Escribe el nombre del producto para guardar receta"));
                return;
            }
            Files.createDirectories(recetasDir());
            List<RecipeItem> items = new ArrayList<>();
            for (Insumo i : insumos) {
                items.add(new RecipeItem(i.prod.getSku(), i.prod.getNombre(),
                        inventory.toBase(i.prod, i.cantidadPorProducto)));
            }
            Path p = recetasDir().resolve(nombreProducto.replaceAll("[^\\p{L}0-9_-]+", "_") + ".json");
            Files.writeString(p, gson.toJson(items), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            flash(AlertBanner.success("Receta guardada"));
        } catch (Exception ex) {
            flash(AlertBanner.warn("No se pudo guardar receta"));
        }
    }

    private List<RecipeItem> loadRecipe(String nombreProducto) {
        try {
            if (nombreProducto == null || nombreProducto.isBlank())
                return Collections.emptyList();
            Path p = recetasDir().resolve(nombreProducto.replaceAll("[^\\p{L}0-9_-]+", "_") + ".json");
            if (!Files.exists(p))
                return Collections.emptyList();
            String s = Files.readString(p, StandardCharsets.UTF_8);
            Type T = new TypeToken<List<RecipeItem>>() {
            }.getType();
            List<RecipeItem> items = gson.fromJson(s, T);
            return items == null ? Collections.emptyList() : items;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    // ===== Utils UI =====
    private void flash(AlertBanner banner) {
        VBox root = (VBox) getTop();
        if (root == null)
            return;
        root.getChildren().removeIf(n -> n instanceof AlertBanner);
        root.getChildren().add(0, banner);
        PauseTransition t = new PauseTransition(Duration.seconds(2.5));
        t.setOnFinished(e -> root.getChildren().remove(banner));
        t.play();
    }

    private String money(double v) {
        return String.format("$%,.2f", v);
    }

    private String trim2(double v) {
        return String.format("%.2f", v);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.replace(",", ".").trim());
        } catch (Exception e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ComboBox<T> cast(ComboBox<?> cb) {
        return (ComboBox<T>) cb;
    }

    // Modelo interno
    private static class Insumo {
        final Product prod;
        double cantidadPorProducto; // en UNIDAD DEL PRODUCTO

        Insumo(Product p, double q) {
            this.prod = p;
            this.cantidadPorProducto = q;
        }
    }
}
