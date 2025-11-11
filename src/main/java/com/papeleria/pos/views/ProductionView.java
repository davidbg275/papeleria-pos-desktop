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
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Vista de armado de productos.
 * - Campo Nombre sugiere solo “fabricables”: recetas guardadas + productos
 * categoría Producción.
 * - Si el nombre ya es una receta, cambia a “Reproducir receta”
 * automáticamente.
 * - Evita crear nuevo si existe en inventario con otra categoría.
 * - Autocomplete no acepta con ENTER ni SPACE (solo TAB/clic).
 * - SKU automático LL-NNN.
 * - Conversión correcta de presentaciones (paquete/caja/rollo/metro/cm).
 */
public class ProductionView extends BorderPane {

    // Servicios
    private final SessionService session;
    private final ProductionService production;
    private final InventoryService inventory;
    private final EventBus bus;

    // Receta cargada si se está reproduciendo
    private RecipeFile recetaActual = null;

    // Contenedores
    private VBox header;

    // Modo de trabajo
    private final ToggleGroup modo = new ToggleGroup();
    private final RadioButton rbNuevo = new RadioButton("Crear NUEVO producto");
    private final RadioButton rbRepro = new RadioButton("Reproducir receta");
    private final ComboBox<String> recetaSelect = new ComboBox<>();

    // Datos del producto final
    private final TextField nombreFinal = new TextField();
    private final TextField skuFinal = new TextField();
    private final Button btnRegenerarSku = new Button("↻");

    // Lotes
    private final Spinner<Integer> cantidadPorLote = new Spinner<>(1, 1_000_000, 1, 1);
    private final Spinner<Integer> numeroLotes = new Spinner<>(1, 1_000_000, 1, 1);
    private final Label totalAFabricar = new Label("1");

    // Materiales
    private final ComboBox<Product> material = new ComboBox<>();
    private final ChoiceBox<String> presentacion = new ChoiceBox<>();
    private final Spinner<Double> cantPorProducto = new Spinner<>(0.01, 1_000_000.0, 1.0, 1.0);
    private final Label disponibleLbl = new Label("—");
    private final FlowPane listaInsumos = new FlowPane(10, 10);
    private final ObservableList<Insumo> insumos = FXCollections.observableArrayList();

    // Precios
    private final Label kCostoMat = new Label("$0.00");
    private final Label kCostoUnit = new Label("$0.00");
    private final Label kPrecioFinal = new Label("$0.00");
    private final Spinner<Double> manoObraUnit = new Spinner<>(0.0, 1_000_000.0, 0.0, 1.0);
    private final ToggleGroup precioModo = new ToggleGroup();
    private final RadioButton pmMargen = new RadioButton("Margen %");
    private final RadioButton pmDirecto = new RadioButton("Precio directo");
    private final Spinner<Double> margen = new Spinner<>(0.0, 1000.0, 50.0, 1.0);
    private final TextField precioDirecto = new TextField();

    private final Gson gson = new Gson();

    public ProductionView(SessionService session, ProductionService production, InventoryService inventory,
            EventBus bus) {
        this.session = session;
        this.production = production;
        this.inventory = inventory;
        this.bus = bus;

        setPadding(new Insets(12));

        Label t = new Label("Armar Productos");
        t.getStyleClass().add("h1");
        Label s = new Label("Crea productos nuevos o reproduce tus recetas");
        s.getStyleClass().add("subtle");
        header = new VBox(6, t, s);
        setTop(header);

        VBox page = new VBox(12,
                cardModo(),
                cardProducto(),
                cardLotes(),
                cardMateriales(),
                cardPrecio());
        ScrollPane sc = new ScrollPane(page);
        sc.setFitToWidth(true);
        setCenter(sc);

        nombreFinal.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        skuFinal.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        material.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        material.getEditor().setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

        cantidadPorLote.valueProperty().addListener((o, a, v) -> recalc());
        numeroLotes.valueProperty().addListener((o, a, v) -> recalc());
        manoObraUnit.valueProperty().addListener((o, a, v) -> recalc());
        margen.valueProperty().addListener((o, a, v) -> recalc());
        precioDirecto.textProperty().addListener((o, a, v) -> recalc());
        pmMargen.setOnAction(e -> recalc());
        pmDirecto.setOnAction(e -> recalc());

        bus.subscribe(EventBus.Topic.INVENTORY_CHANGED, ev -> javafx.application.Platform.runLater(this::recalc));

        cargarCatalogo();
        refrescarListaRecetas();
        rbNuevo.setSelected(true);
        aplicarModo();
        recalc();
    }

    /* ===================== UI ===================== */

    private VBox cardModo() {
        rbNuevo.setToggleGroup(modo);
        rbRepro.setToggleGroup(modo);

        recetaSelect.setPromptText("Selecciona una receta");
        recetaSelect.setDisable(true);
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

    private VBox cardProducto() {
        // Sugerencias SOLO con “fabricables”
        ComboBox<String> nombreCombo = new ComboBox<>(FXCollections.observableArrayList(listaFabricables()));
        nombreCombo.setEditable(true);
        new AutoCompleteCombo<>(cast(nombreCombo), nombreCombo.getItems(), s -> s);
        nombreCombo.getEditor().textProperty().bindBidirectional(nombreFinal.textProperty());

        skuFinal.setEditable(false);
        btnRegenerarSku.setOnAction(e -> skuFinal.setText(genSkuFromName(nombreFinal.getText())));

        nombreFinal.textProperty().addListener((o, a, v) -> onNombreChanged());

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(8));
        g.addRow(0, new Label("Nombre del producto"), nombreCombo);
        g.addRow(1, new Label("Código (SKU)"), new HBox(6, skuFinal, btnRegenerarSku));
        HBox.setHgrow(skuFinal, Priority.ALWAYS);
        return card("Producto final", g);
    }

    private VBox cardLotes() {
        totalAFabricar.getStyleClass().add("total-fabricar");
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(8));
        cantidadPorLote.setEditable(true);
        numeroLotes.setEditable(true);
        g.addRow(0, new Label("Cantidad por lote"), cantidadPorLote);
        g.addRow(1, new Label("Número de lotes"), numeroLotes);
        return card("Lotes y cantidades", g);
    }

    private VBox cardMateriales() {
        material.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Product p) {
                return p == null ? "" : p.getNombre() + " (" + p.getSku() + ")";
            }

            @Override
            public Product fromString(String s) {
                return inventory.list().stream()
                        .filter(p -> (p.getNombre() + " (" + p.getSku() + ")").equalsIgnoreCase(s)).findFirst()
                        .orElse(null);
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
                String u = lc(v.getUnidad());
                double c = Math.max(0.0, v.getContenido());
                if (c > 0 && (u.equals("paquete") || u.equals("caja")))
                    presentacion.getItems().add(nombreMenor(v));
                if (c > 0 && u.equals("rollo")) {
                    if (!presentacion.getItems().contains("Metro"))
                        presentacion.getItems().add("Metro");
                    presentacion.getItems().add("Centímetro");
                }
                if (u.equals("m") || u.equals("metro") || u.equals("metros"))
                    presentacion.getItems().add("Centímetro");
            }
            if (presentacion.getItems().isEmpty())
                presentacion.getItems().add("Unidad");
            presentacion.getSelectionModel().selectFirst();
            updateDisponible();
        });
        cantPorProducto.valueProperty().addListener((o, a, v) -> updateDisponible());

        listaInsumos.setPrefWrapLength(520);
        return card("Materiales", new VBox(10, fila, new HBox(new Region(), agregar), listaInsumos));
    }

    private VBox cardPrecio() {
        VBox k1 = kpi("$0.00", "Costo materiales (por unidad)");
        VBox k2 = kpi("$0.00", "Costo unitario (incluye MO)");
        VBox k3 = kpiG("$0.00", "Precio unitario final");
        ((Label) ((VBox) k1.getChildren().get(0)).getChildren().get(0)).textProperty().bind(kCostoMat.textProperty());
        ((Label) ((VBox) k2.getChildren().get(0)).getChildren().get(0)).textProperty().bind(kCostoUnit.textProperty());
        ((Label) ((VBox) k3.getChildren().get(0)).getChildren().get(0)).textProperty()
                .bind(kPrecioFinal.textProperty());

        pmMargen.setToggleGroup(precioModo);
        pmDirecto.setToggleGroup(precioModo);
        pmMargen.setSelected(true);
        margen.setEditable(true);
        precioDirecto.setPromptText("Ej: 35.00");
        precioDirecto.setDisable(true);
        pmDirecto.selectedProperty().addListener((o, a, v) -> precioDirecto.setDisable(!v));

        Button fabricar = new Button();
        fabricar.getStyleClass().add("success");
        fabricar.textProperty().bind(Bindings.createStringBinding(
                () -> (recetaActual != null ? "Fabricar (receta)" : "Fabricar") + " "
                        + Math.max(1, cantidadPorLote.getValue() * numeroLotes.getValue()),
                cantidadPorLote.valueProperty(), numeroLotes.valueProperty(), kPrecioFinal.textProperty()));
        fabricar.setOnAction(e -> fabricarAccion());

        return card("Precio y fabricación", new VBox(12, new HBox(12, k1, k2, k3),
                new HBox(12, pmMargen, new Label("%"), margen, new Region(), pmDirecto, new Label("$"), precioDirecto),
                fabricar));
    }

    private VBox card(String t, Region content) {
        Label l = new Label(t);
        l.getStyleClass().add("card-title");
        VBox v = new VBox(10, l, content);
        v.getStyleClass().add("card");
        v.setPadding(new Insets(12));
        return v;
    }

    private VBox kpi(String v, String s) {
        Label a = new Label(v);
        a.getStyleClass().add("kpi-value");
        Label b = new Label(s);
        b.getStyleClass().add("subtle");
        VBox inner = new VBox(4, a, b);
        VBox card = new VBox(inner);
        card.getStyleClass().add("soft-card");
        card.setPadding(new Insets(12));
        card.setPrefWidth(240);
        return card;
    }

    private VBox kpiG(String v, String s) {
        Label a = new Label(v);
        a.getStyleClass().addAll("kpi-value", "big");
        Label b = new Label(s);
        b.getStyleClass().add("subtle");
        VBox inner = new VBox(4, a, b);
        VBox card = new VBox(inner);
        card.getStyleClass().add("soft-card");
        card.setPadding(new Insets(12));
        card.setPrefWidth(260);
        return card;
    }

    /* ===================== LÓGICA ===================== */

    private void aplicarModo() {
        boolean nuevo = rbNuevo.isSelected();
        recetaSelect.setDisable(nuevo);
        nombreFinal.setDisable(!nuevo);
        btnRegenerarSku.setDisable(!nuevo);
        if (nuevo) {
            recetaActual = null;
            if (skuFinal.getText() == null || skuFinal.getText().isBlank())
                skuFinal.setText(genSkuFromName(nombreFinal.getText()));
        }
    }

    private void onNombreChanged() {
        if (!rbNuevo.isSelected())
            return;
        String nombre = safe(nombreFinal.getText()).trim();
        skuFinal.setText(genSkuFromName(nombre));
        if (nombre.isEmpty())
            return;

        // Si el nombre ya tiene receta → cambiar a "Reproducir" automáticamente
        if (Files.exists(pathReceta(nombre))) {
            rbRepro.setSelected(true);
            aplicarModo();
            recetaSelect.getSelectionModel().select(nombre);
            cargarRecetaPorNombre(nombre, true);
            flash(AlertBanner.info("Cambié a 'Reproducir receta' porque ya existe la receta: " + nombre));
            return;
        }

        // Si existe en inventario con otra categoría → bloquear
        boolean existeNoProd = inventory.search(nombre).stream()
                .anyMatch(p -> p.getNombre() != null && p.getNombre().equalsIgnoreCase(nombre)
                        && !"Producción".equalsIgnoreCase(safe(p.getCategoria())));
        if (existeNoProd) {
            flash(AlertBanner.warn(
                    "Ya hay un producto en inventario con ese nombre. No puedes crear uno nuevo con el mismo nombre."));
        }
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

        // En modo NUEVO, si el nombre ya es receta, bloquear.
        if (rbNuevo.isSelected() && Files.exists(pathReceta(nombre))) {
            flash(AlertBanner.warn("Ya existe una receta con ese nombre. Usa 'Reproducir receta'."));
            return;
        }

        // Si existe en inventario con otra categoría -> bloquear.
        boolean existeNoProd = inventory.search(nombre).stream()
                .anyMatch(p -> p.getNombre() != null && p.getNombre().equalsIgnoreCase(nombre)
                        && !"Producción".equalsIgnoreCase(safe(p.getCategoria())));
        if (existeNoProd) {
            flash(AlertBanner.warn("Ese nombre pertenece a un producto de inventario. Usa otro nombre."));
            return;
        }

        List<ProductionService.InsumoReq> reqs = new ArrayList<>();
        for (Insumo in : insumos)
            reqs.add(new ProductionService.InsumoReq(in.prod.getSku(), in.cantidadPorProducto));

        double precioFinal = parseMoney(kPrecioFinal.getText());
        String sku = rbNuevo.isSelected() ? skuFinal.getText().trim()
                : safe(recetaActual == null ? "" : recetaActual.sku);
        if (sku.isBlank())
            sku = genSkuFromName(nombre);

        Double[] sug = new Double[1];
        boolean ok = production.produce(nombre, sku, cantidadPorLote.getValue(), numeroLotes.getValue(), reqs,
                manoObraUnit.getValue(), sug);
        if (!ok) {
            flash(AlertBanner.warn("Stock insuficiente o datos inválidos"));
            return;
        }

        Product pf = inventory.findBySku(sku).orElse(null);
        if (pf != null) {
            pf.setPrecio(round2(precioFinal));
            pf.setCategoria("Producción");
            pf.setUnidad("Unidad");
            pf.setContenido(1.0);
            inventory.upsert(pf);
        }

        flash(AlertBanner.success("Producción registrada"));
        if (rbNuevo.isSelected())
            limpiarNuevo();
        recalc();
        refrescarListaRecetas();
    }

    /* ===================== Materiales ===================== */

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
        double q = toProductUnit(p, qUser);
        String human = humanQtyFor(p, q);

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

    private void renderInsumos() {
        listaInsumos.getChildren().clear();
        for (Insumo i : insumos) {
            Label name = new Label(i.prod.getNombre());
            Label qty = new Label("Agregado: " + i.humanResumen);
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

    private void limpiarEditor() {
        material.getSelectionModel().clearSelection();
        material.getEditor().clear();
        presentacion.getItems().setAll("Unidad");
        presentacion.getSelectionModel().selectFirst();
        cantPorProducto.getValueFactory().setValue(1.0);
        updateDisponible();
    }

    private void updateDisponible() {
        Product p = material.getValue();
        double q = cantPorProducto.getValue();
        double qProdUnit = p == null ? 0.0 : toProductUnit(p, q);
        disponibleLbl.setText(p == null ? "—" : disponibleTexto(p, qProdUnit));
    }

    private String disponibleTexto(Product p, double qPU) {
        double reqBase = inventory.toBase(p, qPU * numeroLotes.getValue() * cantidadPorLote.getValue());
        double stockBase = inventory.toBase(p,
                inventory.findBySku(p.getSku()).map(Product::getStock).orElse(p.getStock()));
        boolean ok = stockBase + 1e-9 >= reqBase;
        double stockProd = inventory.findBySku(p.getSku()).map(Product::getStock).orElse(p.getStock());
        return fmt2(stockBase) + " " + baseLabel(p) + " (" + fmt2(stockProd) + " " + unidadLabel(p).toLowerCase()
                + "s)  " + (ok ? "✔" : "✖");
    }

    private String okClase(Insumo i) {
        double reqBase = inventory.toBase(i.prod,
                i.cantidadPorProducto * numeroLotes.getValue() * cantidadPorLote.getValue());
        double stockBase = inventory.toBase(i.prod,
                inventory.findBySku(i.prod.getSku()).map(Product::getStock).orElse(i.prod.getStock()));
        return (stockBase + 1e-9 >= reqBase) ? "ok-pill" : "warn-pill";
    }

    /* ===================== Cálculos y helpers ===================== */

    private void recalc() {
        double costoMatUnit = 0.0;
        for (Insumo i : insumos)
            costoMatUnit += i.prod.getPrecio() * i.cantidadPorProducto;
        double costoUnit = costoMatUnit + manoObraUnit.getValue();
        double precio = pmDirecto.isSelected()
                ? parseD(precioDirecto.getText(), costoUnit)
                : costoUnit * (1.0 + (margen.getValue() / 100.0));
        kCostoMat.setText(money(costoMatUnit));
        kCostoUnit.setText(money(costoUnit));
        kPrecioFinal.setText(money(precio));
    }

    private void limpiarNuevo() {
        recetaActual = null;
        nombreFinal.clear();
        skuFinal.setText(genSkuFromName(""));
        cantidadPorLote.getValueFactory().setValue(1);
        numeroLotes.getValueFactory().setValue(1);
        manoObraUnit.getValueFactory().setValue(0.0);
        pmMargen.setSelected(true);
        margen.getValueFactory().setValue(50.0);
        precioDirecto.clear();
        insumos.clear();
        renderInsumos();
    }

    private double toProductUnit(Product p, double cant) {
        String u = lc(p.getUnidad());
        String pres = presentacion.getValue() == null ? "" : presentacion.getValue();
        double c = Math.max(0.0, p.getContenido());
        return switch (pres) {
            case "Paquete", "Caja", "Rollo", "Unidad" -> cant;
            case "Pieza", "Hoja" -> c > 0 ? cant / c : cant;
            case "Metro" -> (u.equals("rollo") && c > 0) ? cant / c : cant;
            case "Centímetro" -> {
                double m = cant / 100.0;
                yield (u.equals("rollo") && c > 0) ? m / c : m;
            }
            default -> cant;
        };
    }

    private double fromBaseToProductUnit(Product p, double base) {
        String u = lc(p.getUnidad());
        double c = Math.max(0.0, p.getContenido());
        if ((u.equals("paquete") || u.equals("caja") || u.equals("rollo")) && c > 0)
            return base / c;
        return base;
    }

    private String unidadLabel(Product p) {
        String u = lc(p.getUnidad());
        return switch (u) {
            case "paquete" -> "Paquete";
            case "caja" -> "Caja";
            case "rollo" -> "Rollo";
            case "m", "metro", "metros" -> "Metro";
            default -> "Unidad";
        };
    }

    private String nombreMenor(Product p) {
        return lc(p.getNombre()).contains("hoja") ? "Hoja" : "Pieza";
    }

    private String baseLabel(Product p) {
        String u = lc(p.getUnidad());
        if (u.equals("rollo") || u.equals("m") || u.equals("metro") || u.equals("metros"))
            return "m";
        if (u.equals("paquete") || u.equals("caja"))
            return lc(p.getNombre()).contains("hoja") ? "hojas" : "pzas";
        return "pzas";
    }

    private String humanQtyFor(Product p, double qtyPU) {
        return fmt2(qtyPU) + " " + unidadLabel(p).toLowerCase() + (qtyPU == 1 ? "" : "s") + " ("
                + fmt2(inventory.toBase(p, qtyPU)) + " " + baseLabel(p) + ")";
    }

    private String money(double v) {
        return String.format("$%,.2f", v);
    }

    private String fmt2(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String lc(String s) {
        return safe(s).toLowerCase(java.util.Locale.ROOT);
    }

    private double parseD(String s, double def) {
        try {
            return Double.parseDouble(s.replace(",", ".").trim());
        } catch (Exception e) {
            return def;
        }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double parseMoney(String s) {
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

    /* ===================== Recetas ===================== */

    private Path recetasDir() {
        return Path.of("").toAbsolutePath().resolve("data").resolve("recetas");
    }

    private Path pathReceta(String nombre) {
        return recetasDir().resolve(slug(nombre) + ".json");
    }

    private List<String> listaFabricables() {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        // Recetas guardadas
        try {
            Path dir = recetasDir();
            if (Files.exists(dir)) {
                try (var s = Files.list(dir)) {
                    s.filter(p -> p.toString().endsWith(".json"))
                            .forEach(p -> out.add(p.getFileName().toString().replace(".json", "")));
                }
            }
        } catch (Exception ignored) {
        }

        // Productos categoría Producción
        for (Product p : inventory.list()) {
            String cat = safe(p.getCategoria());
            if ("Producción".equalsIgnoreCase(cat) && p.getNombre() != null)
                out.add(p.getNombre());
        }
        return new ArrayList<>(out);
    }

    private RecipeFile loadRecipeFile(String nombre) {
        try {
            Path p = pathReceta(nombre);
            if (!Files.exists(p))
                return null;
            String s = Files.readString(p, StandardCharsets.UTF_8).trim();
            if (s.startsWith("[")) {
                Type T = new TypeToken<List<RecipeItem>>() {
                }.getType();
                List<RecipeItem> items = gson.fromJson(s, T);
                RecipeFile rf = new RecipeFile();
                rf.nombre = nombre;
                rf.sku = "";
                rf.margen = 50.0;
                rf.manoObraUnit = 0.0;
                rf.precioDirecto = 0.0;
                rf.items = items == null ? new ArrayList<>() : items;
                return rf;
            } else
                return gson.fromJson(s, RecipeFile.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void cargarRecetaPorNombre(String nombre, boolean autofill) {
        RecipeFile rf = loadRecipeFile(nombre);
        if (rf == null) {
            flash(AlertBanner.warn("No se pudo cargar la receta"));
            return;
        }
        recetaActual = rf;
        insumos.clear();
        if (autofill) {
            nombreFinal.setText(rf.nombre);
            skuFinal.setText(safe(rf.sku));
        }
        for (RecipeItem it : rf.items) {
            Product p = inventory.findBySku(it.getSku()).orElse(null);
            if (p != null) {
                double q = fromBaseToProductUnit(p, it.getCantidadBase());
                insumos.add(new Insumo(p, q, humanQtyFor(p, q)));
            }
        }
        renderInsumos();
        recalc();
    }

    private void refrescarListaRecetas() {
        try {
            List<String> nombres = new ArrayList<>();
            if (Files.exists(recetasDir()))
                try (var s = Files.list(recetasDir())) {
                    s.filter(p -> p.toString().endsWith(".json"))
                            .forEach(p -> nombres.add(p.getFileName().toString().replace(".json", "")));
                }
            Collections.sort(nombres);
            recetaSelect.setItems(FXCollections.observableArrayList(nombres));
        } catch (Exception ignored) {
        }
    }

    /* ===================== Utilidades UI ===================== */

    private void flash(AlertBanner b) {
        header.getChildren().removeIf(n -> n instanceof AlertBanner);
        header.getChildren().add(0, b);
        PauseTransition t = new PauseTransition(Duration.seconds(2.2));
        t.setOnFinished(e -> header.getChildren().remove(b));
        t.play();
    }

    private String slug(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.replaceAll("[^\\p{L}0-9]+", "-").replaceAll("^-+|-+$", "").toLowerCase(java.util.Locale.ROOT);
        if (n.isBlank())
            n = "pf";
        return n;
    }

    // SKU LL-NNN
    private String genSkuFromName(String name) {
        String base = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Za-z0-9\\s-]", " ")
                .trim();
        String first = "PF";
        for (String tok : base.split("\\s+"))
            if (tok.matches(".*[A-Za-z].*")) {
                first = tok;
                break;
            }
        String prefix = first.substring(0, Math.min(2, first.length())).toUpperCase(java.util.Locale.ROOT);
        Pattern pat = Pattern.compile("^" + Pattern.quote(prefix) + "-(\\d{3})$");
        int max = 0;
        for (Product p : inventory.list()) {
            String sku = safe(p.getSku()).toUpperCase(java.util.Locale.ROOT);
            var m = pat.matcher(sku);
            if (m.matches())
                try {
                    int n = Integer.parseInt(m.group(1));
                    if (n > max)
                        max = n;
                } catch (Exception ignored) {
                }
        }
        String cand;
        int seq = max + 1;
        do {
            cand = String.format(java.util.Locale.ROOT, "%s-%03d", prefix, seq++);
        } while (inventory.findBySku(cand).isPresent());
        return cand;
    }

    /* ===================== Modelos internos ===================== */

    private static class Insumo {
        final Product prod;
        double cantidadPorProducto;
        String humanResumen;

        Insumo(Product p, double q, String h) {
            this.prod = p;
            this.cantidadPorProducto = q;
            this.humanResumen = h;
        }
    }

    private static class RecipeFile {
        String nombre;
        String sku;
        double margen;
        double precioDirecto;
        double manoObraUnit;
        List<RecipeItem> items = new ArrayList<>();
    }
}
