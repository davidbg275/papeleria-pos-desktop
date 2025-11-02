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
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * UI de armado con recetas, SKU auto, modos NUEVO/REPRODUCIR, precios claros y
 * materiales legibles.
 */
public class ProductionView extends BorderPane {

    // Servicios
    private final SessionService session;
    private final ProductionService production;
    private final InventoryService inventory;
    private final EventBus bus;

    // Host para banners
    private VBox bannerHost;

    // ====== Modo de trabajo ======
    private final ToggleGroup modo = new ToggleGroup();
    private final RadioButton rbNuevo = new RadioButton("Crear NUEVO producto");
    private final RadioButton rbRepro = new RadioButton("Reproducir uno MÍO");

    // Recetas guardadas (solo mías)
    private final ComboBox<String> recetaSelect = new ComboBox<>();

    // Paso 1: Datos del PF
    private final TextField nombreFinal = new TextField();
    private final TextField skuFinal = new TextField(); // se autogenera, readonly en "Nuevo"
    private final Button regenSku = new Button("↻");

    // Paso 2: Cantidades
    private final Spinner<Integer> cantidadPorLote = new Spinner<>(1, 1_000_000, 1, 1);
    private final Spinner<Integer> numeroLotes = new Spinner<>(1, 1_000_000, 1, 1);
    private final Label totalAFabricar = new Label("1");

    // Paso 3: Materiales
    private final ComboBox<Product> material = new ComboBox<>();
    private final ChoiceBox<String> presentacion = new ChoiceBox<>();
    private final Spinner<Double> cantPorProducto = new Spinner<>(0.01, 1_000_000.0, 1.0, 1.0);
    private final Label disponibleLbl = new Label("—");
    private final FlowPane listaInsumos = new FlowPane(10, 10);
    private final ObservableList<Insumo> insumos = FXCollections.observableArrayList();

    // Resumen / Precio
    private final Label kCostoMat = new Label("$0.00");
    private final Label kCostoUnit = new Label("$0.00");
    private final Label kPrecioFinal = new Label("$0.00");
    private final Spinner<Double> manoObraUnit = new Spinner<>(0.0, 1_000_000.0, 0.0, 1.0);
    private final ToggleGroup precioModo = new ToggleGroup();
    private final RadioButton pmMargen = new RadioButton("Margen %");
    private final RadioButton pmDirecto = new RadioButton("Precio directo");
    private final Spinner<Double> margen = new Spinner<>(0.0, 1000.0, 50.0, 1.0);
    private final TextField precioDirecto = new TextField();

    // Utilidades
    private final Gson gson = new Gson();

    // Estado receta cargada
    private RecipeFile recetaActual = null; // si está en modo Reproducir

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
        Label subt = new Label("Crea productos nuevos o reproduce los que ya registraste");
        subt.getStyleClass().add("subtle");
        VBox header = new VBox(6, titulo, subt, stepper());
        setTop(header);
        this.bannerHost = header;

        // Página con scroll
        VBox page = new VBox(12);
        page.getStyleClass().add("page");
        page.getChildren().addAll(
                cardModo(),
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

        // Eventos globales
        cantidadPorLote.valueProperty().addListener((o, a, v) -> recalc());
        numeroLotes.valueProperty().addListener((o, a, v) -> recalc());
        manoObraUnit.valueProperty().addListener((o, a, v) -> recalc());
        margen.valueProperty().addListener((o, a, v) -> recalc());
        precioDirecto.textProperty().addListener((o, a, v) -> recalc());
        pmMargen.setOnAction(e -> recalc());
        pmDirecto.setOnAction(e -> recalc());

        bus.subscribe(EventBus.Topic.INVENTORY_CHANGED, e -> javafx.application.Platform.runLater(this::recalc));

        // Inicializar catálogo y recetas
        cargarCatalogo();
        refrescarListaRecetas();
        // Modo por defecto
        rbNuevo.setSelected(true);
        aplicarModo();

        recalc();
    }

    // ====== UI building ======
    private HBox stepper() {
        HBox s = new HBox(20);
        s.getStyleClass().add("stepper");
        s.getChildren().addAll(step("1", "Elegir / cargar", true), sep(), step("2", "Materiales", false),
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

    private VBox cardModo() {
        rbNuevo.setToggleGroup(modo);
        rbRepro.setToggleGroup(modo);
        rbNuevo.setSelected(true);

        recetaSelect.setPromptText("Selecciona una de tus recetas");
        recetaSelect.setDisable(true);
        recetaSelect.setMaxWidth(Double.MAX_VALUE);
        recetaSelect.valueProperty().addListener((o, a, v) -> {
            if (rbRepro.isSelected() && v != null)
                cargarRecetaPorNombre(v, true);
        });

        modo.selectedToggleProperty().addListener((o, a, v) -> aplicarModo());

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(8));
        g.addRow(0, rbNuevo, rbRepro);
        g.addRow(1, new Label("Mis recetas"), recetaSelect);

        return card("¿Qué quieres hacer?", g);
    }

    private VBox cardPaso1Producto() {
        nombreFinal.setPromptText("Ej: Flor azul personalizada");
        // SKU readonly (auto)
        skuFinal.setPromptText("Se genera automáticamente");
        skuFinal.setEditable(false);
        regenSku.setOnAction(e -> skuFinal.setText(genSkuUniqueFromName(nombreFinal.getText())));
        regenSku.setFocusTraversable(false);

        // Validación de duplicados de nombre
        nombreFinal.textProperty().addListener((o, a, v) -> onNombreChange());

        // autocompleto de nombres para NUEVO pero solo como referencia, NO fuerza
        // selección
        List<String> nombres = inventory.list().stream()
                .map(p -> safe(p.getNombre())).filter(s -> !s.isEmpty()).sorted().toList();
        ComboBox<String> nombreCombo = new ComboBox<>(FXCollections.observableArrayList(nombres));
        nombreCombo.setEditable(true);
        new AutoCompleteCombo<>(cast(nombreCombo), nombres, s -> s);
        nombreCombo.getEditor().textProperty().bindBidirectional(nombreFinal.textProperty());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(8));
        grid.addRow(0, new Label("Nombre del producto"), nombreCombo);
        grid.addRow(1, new Label("Código (SKU)"), new HBox(6, skuFinal, regenSku));
        HBox.setHgrow(skuFinal, Priority.ALWAYS);

        return card("Producto final", grid);
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
        return card("Lotes y cantidades", wrap);
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
        guardarReceta.setOnAction(e -> saveRecipeDialog());

        GridPane fila = new GridPane();
        fila.setHgap(12);
        fila.setVgap(10);
        fila.setPadding(new Insets(8));
        fila.addRow(0, new Label("Material"), material);
        fila.addRow(1, new Label("Presentación"), presentacion);
        fila.addRow(2, new Label("Cantidad x producto"), cantPorProducto);
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

        HBox acciones = rightAligned(new Region(), new HBox(8, guardarReceta, agregar));
        VBox cardBody = new VBox(10, fila, acciones, listaInsumos);

        return card("Materiales", cardBody);
    }

    private VBox cardResumenCostos() {
        VBox k1 = kpi("$0.00", "Costo materiales (por unidad)");
        VBox k2 = kpi("$0.00", "Costo unitario (incluye MO)");
        VBox k3 = kpiGrande("$0.00", "Precio unitario final");

        ((Label) ((VBox) k1.getChildren().get(0)).getChildren().get(0)).textProperty().bind(kCostoMat.textProperty());
        ((Label) ((VBox) k2.getChildren().get(0)).getChildren().get(0)).textProperty().bind(kCostoUnit.textProperty());
        ((Label) ((VBox) k3.getChildren().get(0)).getChildren().get(0)).textProperty()
                .bind(kPrecioFinal.textProperty());

        manoObraUnit.setEditable(true);

        pmMargen.setToggleGroup(precioModo);
        pmDirecto.setToggleGroup(precioModo);
        pmMargen.setSelected(true);

        margen.setEditable(true);
        precioDirecto.setPromptText("Ej: 35.00");
        precioDirecto.setDisable(true); // solo habilita si pmDirecto
        pmDirecto.selectedProperty().addListener((o, a, v) -> precioDirecto.setDisable(!v));

        HBox selector = new HBox(12,
                pmMargen, new Label("%"), margen,
                new Region(),
                pmDirecto, new Label("$"), precioDirecto);
        HBox.setHgrow(selector.getChildren().get(2), Priority.ALWAYS);

        Button fabricar = new Button();
        fabricar.getStyleClass().add("success");
        fabricar.textProperty().bind(Bindings.createStringBinding(
                () -> (recetaActual != null ? "Fabricar (receta)" : "Fabricar") + " " + totalAFabricar.getText(),
                totalAFabricar.textProperty()));
        fabricar.setOnAction(e -> fabricarAccion());

        return card("Precio y fabricación", new VBox(12, new HBox(12, k1, k2, k3), selector, fabricar));
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
        card.setPrefWidth(240);
        return card;
    }

    private VBox kpiGrande(String value, String subtitle) {
        Label v = new Label(value);
        v.getStyleClass().addAll("kpi-value", "big");
        Label s = new Label(subtitle);
        s.getStyleClass().add("subtle");
        VBox inner = new VBox(4, v, s);
        VBox card = new VBox(inner);
        card.getStyleClass().add("soft-card");
        card.setPadding(new Insets(12));
        card.setPrefWidth(260);
        return card;
    }

    // ====== Lógica ======
    private void aplicarModo() {
        boolean esNuevo = rbNuevo.isSelected();

        recetaSelect.setDisable(esNuevo);
        nombreFinal.setDisable(!esNuevo);
        regenSku.setDisable(!esNuevo);

        if (esNuevo) {
            recetaActual = null;
            // Generar SKU si falta
            if (skuFinal.getText() == null || skuFinal.getText().isBlank()) {
                skuFinal.setText(genSkuUniqueFromName(nombreFinal.getText()));
            }
        } else {
            // Modo reproducir: exigir selección
            if (recetaSelect.getItems().isEmpty()) {
                flash(AlertBanner.warn("No tienes recetas guardadas aún"));
            }
        }
    }

    private void onNombreChange() {
        if (!rbNuevo.isSelected())
            return;
        String nombre = safe(nombreFinal.getText()).trim();
        skuFinal.setText(genSkuUniqueFromName(nombre));

        if (nombre.isEmpty())
            return;

        boolean existeReceta = Files.exists(pathReceta(nombre));
        boolean existeEnInventario = inventory.search(nombre).stream()
                .anyMatch(p -> safe(p.getNombre()).equalsIgnoreCase(nombre));

        if (existeReceta) {
            var opt = confirm(
                    "Este nombre ya tiene receta guardada",
                    "¿Quieres reproducir ese producto en lugar de crear otro?",
                    "Reproducir", "Seguir con nuevo");
            if (opt == 0) { // Reproducir
                rbRepro.setSelected(true);
                aplicarModo();
                recetaSelect.getSelectionModel().select(nombre);
                cargarRecetaPorNombre(nombre, true);
            }
            return;
        }
        if (existeEnInventario) {
            flash(AlertBanner
                    .warn("Ya existe un producto en inventario con ese nombre. Cambia el nombre o usa 'Reproducir'."));
        }
    }

    private void cargarCatalogo() {
        material.setItems(FXCollections.observableArrayList(inventory.list()));
    }

    private void refrescarListaRecetas() {
        try {
            List<String> nombres = new ArrayList<>();
            if (Files.exists(recetasDir())) {
                try (var s = Files.list(recetasDir())) {
                    s.filter(p -> p.toString().endsWith(".json"))
                            .forEach(p -> nombres.add(p.getFileName().toString().replace(".json", "")));
                }
            }
            Collections.sort(nombres);
            recetaSelect.setItems(FXCollections.observableArrayList(nombres));
        } catch (Exception ignored) {
        }
    }

    private void cargarRecetaPorNombre(String nombre, boolean autofillCampos) {
        RecipeFile rf = loadRecipeFile(nombre);
        if (rf == null) {
            flash(AlertBanner.warn("No se pudo cargar la receta"));
            return;
        }
        recetaActual = rf;
        insumos.clear();

        // productos
        if (autofillCampos) {
            nombreFinal.setText(rf.nombre);
            skuFinal.setText(safe(rf.sku));
        }

        // insumos
        for (RecipeItem it : rf.items) {
            Product p = inventory.findBySku(it.getSku()).orElse(null);
            if (p != null) {
                double qtyProdUnit = fromBaseToProductUnit(p, it.getCantidadBase());
                insumos.add(new Insumo(p, qtyProdUnit, humanQtyFor(p, qtyProdUnit)));
            }
        }
        renderInsumos();
        // precio / MO si almacenado
        if (rf.manoObraUnit > 0)
            manoObraUnit.getValueFactory().setValue(rf.manoObraUnit);
        if (rf.precioDirecto > 0) {
            pmDirecto.setSelected(true);
            precioDirecto.setText(fmt2(rf.precioDirecto));
        } else {
            pmMargen.setSelected(true);
            margen.getValueFactory().setValue(rf.margen);
        }

        recalc();
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

        double q = toProductUnit(p, qUser); // convertir a unidad del producto
        String human = humanQtyFor(p, q); // "2 m (0.20 rollos)" etc

        for (Insumo x : new ArrayList<>(insumos)) {
            if (x.prod.getSku().equalsIgnoreCase(p.getSku())) {
                x.cantidadPorProducto += q;
                x.humanResumen = humanQtyFor(p, x.cantidadPorProducto);
                renderInsumos();
                recalc();
                limpiarEditor();
                return;
            }
        }
        insumos.add(new Insumo(p, q, human));
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
            Label qty = new Label("Agregado: " + i.humanResumen); // texto humano
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
        double q = cantPorProducto.getValue();
        double qProdUnit = p == null ? 0.0 : toProductUnit(p, q);
        disponibleLbl.setText(p == null ? "—" : disponibleTexto(p, qProdUnit));
    }

    private String disponibleTexto(Product p, double qPorProducto_enUnidadDelProducto) {
        double reqBase = inventory.toBase(p, qPorProducto_enUnidadDelProducto
                * numeroLotes.getValue() * cantidadPorLote.getValue());

        double stockBase = inventory.toBase(p,
                inventory.findBySku(p.getSku()).map(Product::getStock).orElse(p.getStock()));

        boolean ok = stockBase + 1e-9 >= reqBase;
        String labelBase = baseLabel(p);
        // También una equivalencia en unidad del producto
        double stockProd = inventory.findBySku(p.getSku()).map(Product::getStock).orElse(p.getStock());
        String prodU = unidadLabel(p);
        return fmt2(stockBase) + " " + labelBase + " (" + fmt2(stockProd) + " " + prodU.toLowerCase() + "s)  "
                + (ok ? "✔" : "✖");
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

        double precioFinal;
        if (pmDirecto.isSelected()) {
            double precioDir = parseDouble(precioDirecto.getText(), -1.0);
            precioFinal = precioDir > 0 ? precioDir : costoUnitario; // fallback sensato
        } else {
            precioFinal = costoUnitario * (1.0 + (margen.getValue() / 100.0));
        }

        kCostoMat.setText(money(costoMatUnit));
        kCostoUnit.setText(money(costoUnitario));
        kPrecioFinal.setText(money(precioFinal));
    }

    private void fabricarAccion() {
        if (!session.isAdmin()) {
            flash(AlertBanner.warn("Solo ADMIN puede fabricar"));
            return;
        }
        String nombre = safe(nombreFinal.getText()).trim();
        if (rbRepro.isSelected() && recetaActual == null) {
            flash(AlertBanner.warn("Selecciona una receta"));
            return;
        }
        if (nombre.isEmpty()) {
            flash(AlertBanner.warn("Escribe el nombre del producto final"));
            return;
        }
        if (insumos.isEmpty()) {
            flash(AlertBanner.warn("Agrega al menos un material"));
            return;
        }

        // Si hay receta cargada, confirmar
        if (recetaActual != null) {
            int opt = confirm("¿Usar receta tal cual?",
                    "Puedes fabricar con esta receta o modificarla antes de continuar.",
                    "Usar receta", "Modificar");
            if (opt == 1)
                return; // Modificar => cancelar fabricación
        }

        // Construir requerimientos
        List<ProductionService.InsumoReq> reqs = new ArrayList<>();
        for (Insumo in : insumos)
            reqs.add(new ProductionService.InsumoReq(in.prod.getSku(), in.cantidadPorProducto));

        // Precio final mostrado (para guardar en PF después)
        double precioFinal = parseMoneyLabel(kPrecioFinal.getText());

        // Producir: si receta actual trae SKU => reutilizar; en NUEVO => SKU
        // autogenerado
        String sku = rbNuevo.isSelected() ? skuFinal.getText().trim()
                : safe(recetaActual == null ? "" : recetaActual.sku);
        if (sku.isBlank())
            sku = genSkuUniqueFromName(nombre);

        Double[] sug = new Double[1];
        boolean ok = production.produce(
                nombre,
                sku,
                cantidadPorLote.getValue(),
                numeroLotes.getValue(),
                reqs,
                manoObraUnit.getValue(),
                sug);

        if (!ok) {
            flash(AlertBanner.warn("Stock insuficiente o datos inválidos"));
            return;
        }

        // Fijar precio final redondeado y normalizar PF
        double precioRed = round2(precioFinal);
        Product pf = inventory.findBySku(sku).orElse(null);
        if (pf != null) {
            pf.setPrecio(precioRed);
            pf.setCategoria("Producción");
            pf.setUnidad("Unidad");
            pf.setContenido(1.0);
            inventory.upsert(pf);
        }

        // Ofrecer guardar/actualizar receta
        int opt = confirm("¿Guardar receta?",
                "Puedes guardar/actualizar la receta con este nombre para reproducirla después.",
                "Guardar", "No ahora");
        if (opt == 0)
            saveRecipe(nombre, sku);

        flash(AlertBanner.success("Producción registrada"));
        if (rbNuevo.isSelected())
            limpiarTodoNuevo();
        else
            renderInsumos();
        recalc();
        refrescarListaRecetas();
    }

    private void limpiarTodoNuevo() {
        recetaActual = null;
        nombreFinal.clear();
        skuFinal.setText(genSkuUniqueFromName(""));
        cantidadPorLote.getValueFactory().setValue(1);
        numeroLotes.getValueFactory().setValue(1);
        manoObraUnit.getValueFactory().setValue(0.0);
        pmMargen.setSelected(true);
        margen.getValueFactory().setValue(50.0);
        precioDirecto.clear();
        insumos.clear();
        renderInsumos();
        recalc();
    }

    // ====== Conversiones / human ======
    private double toProductUnit(Product p, double cantidadSegunPresentacion) {
        String u = safe(p.getUnidad()).toLowerCase();
        String pres = presentacion.getValue() == null ? "" : presentacion.getValue();
        double c = Math.max(0.0, p.getContenido());

        switch (pres) {
            case "Paquete", "Caja", "Rollo", "Unidad":
                return cantidadSegunPresentacion;
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

    private String baseLabel(Product p) {
        String u = safe(p.getUnidad()).toLowerCase();
        if (u.equals("rollo") || u.equals("m") || u.equals("metro") || u.equals("metros"))
            return "m";
        if (u.equals("paquete") || u.equals("caja")) {
            String name = safe(p.getNombre()).toLowerCase();
            return name.contains("hoja") ? "hojas" : "pzas";
        }
        return "pzas";
    }

    private String humanQtyFor(Product p, double qtyInProductUnit) {
        String prodU = unidadLabel(p);
        String baseU = baseLabel(p);
        double base = inventory.toBase(p, qtyInProductUnit);
        return fmt2(qtyInProductUnit) + " " + prodU.toLowerCase() + (qtyInProductUnit == 1 ? "" : "s")
                + " (" + fmt2(base) + " " + baseU + ")";
    }

    // ====== Recetas ======
    private Path recetasDir() {
        return Path.of("").toAbsolutePath().resolve("data").resolve("recetas");
    }

    private Path pathReceta(String nombre) {
        return recetasDir().resolve(slug(nombre) + ".json");
    }

    private void saveRecipeDialog() {
        String nombre = safe(nombreFinal.getText()).trim();
        if (nombre.isEmpty()) {
            flash(AlertBanner.warn("Escribe el nombre del producto para guardar receta"));
            return;
        }
        int opt = confirm("Guardar receta", "Se guardará con el nombre actual.", "Guardar", "Cancelar");
        if (opt == 0)
            saveRecipe(nombre, safe(skuFinal.getText()));
    }

    private void saveRecipe(String nombreProducto, String sku) {
        try {
            Files.createDirectories(recetasDir());
            RecipeFile rf = new RecipeFile();
            rf.nombre = nombreProducto;
            rf.sku = sku;
            rf.manoObraUnit = manoObraUnit.getValue();
            if (pmDirecto.isSelected()) {
                rf.precioDirecto = parseDouble(precioDirecto.getText(), 0.0);
                rf.margen = 0.0;
            } else {
                rf.margen = margen.getValue();
                rf.precioDirecto = 0.0;
            }
            rf.items = new ArrayList<>();
            for (Insumo i : insumos) {
                rf.items.add(new RecipeItem(i.prod.getSku(), i.prod.getNombre(),
                        inventory.toBase(i.prod, i.cantidadPorProducto)));
            }
            Files.writeString(pathReceta(nombreProducto), gson.toJson(rf), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            flash(AlertBanner.success("Receta guardada"));
            recetaActual = rf;
            refrescarListaRecetas();
        } catch (Exception ex) {
            flash(AlertBanner.warn("No se pudo guardar receta"));
        }
    }

    private RecipeFile loadRecipeFile(String nombreProducto) {
        try {
            Path p = pathReceta(nombreProducto);
            if (!Files.exists(p))
                return null;
            String s = Files.readString(p, StandardCharsets.UTF_8).trim();
            if (s.startsWith("[")) {
                // Compatibilidad con formato viejo (solo lista)
                Type T = new TypeToken<List<RecipeItem>>() {
                }.getType();
                List<RecipeItem> items = gson.fromJson(s, T);
                RecipeFile rf = new RecipeFile();
                rf.nombre = nombreProducto;
                rf.sku = "";
                rf.margen = 50.0;
                rf.manoObraUnit = 0.0;
                rf.precioDirecto = 0.0;
                rf.items = items == null ? new ArrayList<>() : items;
                return rf;
            } else {
                return gson.fromJson(s, RecipeFile.class);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ====== Utils generales ======
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

    private int confirm(String title, String msg, String ok, String cancel) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, msg, new ButtonType(ok, ButtonBar.ButtonData.OK_DONE),
                new ButtonType(cancel, ButtonBar.ButtonData.CANCEL_CLOSE));
        a.setTitle(title);
        Optional<ButtonType> r = a.showAndWait();
        return (r.isPresent() && r.get().getButtonData() == ButtonBar.ButtonData.OK_DONE) ? 0 : 1;
    }

    private String money(double v) {
        return String.format("$%,.2f", v);
    }

    private String fmt2(double v) {
        return String.format(Locale.US, "%.2f", v);
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

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double parseMoneyLabel(String s) {
        try {
            return Double.parseDouble(s.replace("$", "").replace(",", "").trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ComboBox<T> cast(ComboBox<?> cb) {
        return (ComboBox<T>) cb;
    }

    private String slug(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.replaceAll("[^\\p{L}0-9]+", "-").replaceAll("^-+|-+$", "").toLowerCase(Locale.ROOT);
        if (n.isBlank())
            n = "pf";
        return n;
    }

    private String genSkuUniqueFromName(String name) {
        // Normaliza y toma el primer término alfabético
        String base = java.text.Normalizer.normalize(name == null ? "" : name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Za-z0-9\\s-]", " ")
                .trim();

        String firstWord = "";
        for (String tok : base.split("\\s+")) {
            if (tok.matches(".*[A-Za-z].*")) {
                firstWord = tok;
                break;
            }
        }
        if (firstWord.isEmpty())
            firstWord = "PF";

        // Prefijo: 2 primeras letras del primer término
        String prefix = firstWord.substring(0, Math.min(2, firstWord.length()))
                .toUpperCase(java.util.Locale.ROOT);

        // Buscar máximo correlativo existente con ese prefijo: ^XX-\d{3}$
        int max = 0;
        java.util.regex.Pattern pat = java.util.regex.Pattern
                .compile("^" + java.util.regex.Pattern.quote(prefix) + "-(\\d{3})$");
        for (var p : inventory.list()) {
            String sku = p.getSku() == null ? "" : p.getSku().trim().toUpperCase(java.util.Locale.ROOT);
            var m = pat.matcher(sku);
            if (m.matches()) {
                try {
                    int n = Integer.parseInt(m.group(1));
                    if (n > max)
                        max = n;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Siguiente número, con cero-padding a 3 dígitos, garantizando unicidad
        String candidate;
        int seq = max + 1;
        do {
            candidate = String.format(java.util.Locale.ROOT, "%s-%03d", prefix, seq++);
        } while (inventory.findBySku(candidate).isPresent());

        return candidate;
    }

    // ====== Modelo interno y archivo de receta ======
    private static class Insumo {
        final Product prod;
        double cantidadPorProducto; // unidad del producto
        String humanResumen; // texto UX: "2 m (0.20 rollos)"

        Insumo(Product p, double q, String human) {
            this.prod = p;
            this.cantidadPorProducto = q;
            this.humanResumen = human;
        }
    }

    private static class RecipeFile {
        String nombre;
        String sku;
        double margen; // si >0 usamos margen
        double precioDirecto; // si >0 usamos directo
        double manoObraUnit;
        List<RecipeItem> items = new ArrayList<>();
    }
}
