package com.papeleria.pos.views;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.papeleria.pos.services.RecipesStore;
import javafx.collections.FXCollections;
import com.papeleria.pos.components.AutoCompleteCombo;
import com.papeleria.pos.models.Product;

public class ProductionView extends BorderPane {

    // Servicios
    private final SessionService session;
    private final ProductionService production;
    private final InventoryService inventory;
    private final EventBus bus;
    private final RecipesStore recipes = new RecipesStore();

    // Estado receta
    private RecipeFile recetaActual = null;
    private boolean recetaBloqueada = false;

    // UI raíz
    private VBox header;

    // Modo
    private final ToggleGroup modo = new ToggleGroup();
    private final RadioButton rbNuevo = new RadioButton("Crear NUEVO producto");
    private final RadioButton rbRepro = new RadioButton("Reproducir receta");
    private final ComboBox<String> recetaSelect = new ComboBox<>();
    private final Button btnModificarReceta = new Button("Modificar receta");

    // Producto final
    private final TextField nombreFinal = new TextField();
    private final TextField skuFinal = new TextField();
    private final Button btnRegenerarSku = new Button("↻");

    // Lotes
    private final Spinner<Integer> cantidadPorLote = new Spinner<>(1, 1_000_000, 1, 1);
    private final Spinner<Integer> numeroLotes = new Spinner<>(1, 1_000_000, 1, 1);

    // Materiales
    private AutoCompleteCombo<Product> acMat;
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
    private final TextField precioDirecto = new TextField();
    private final Spinner<Double> margen = new Spinner<>(0.0, 1000.0, 50.0, 1.0);

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

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

        // Recalculo y saneo de precio directo
        cantidadPorLote.valueProperty().addListener((o, a, v) -> recalc());
        numeroLotes.valueProperty().addListener((o, a, v) -> recalc());
        manoObraUnit.valueProperty().addListener((o, a, v) -> recalc());
        margen.valueProperty().addListener((o, a, v) -> recalc());
        pmMargen.setOnAction(e -> recalc());
        pmDirecto.setOnAction(e -> recalc());
        precioDirecto.textProperty().addListener((o, a, v) -> {
            String s1 = (v == null ? "" : v).replaceAll("[^0-9.,]", "");
            if (!s1.equals(v)) {
                precioDirecto.setText(s1);
                return;
            }
            var m = s1.replace(',', '.');
            if (m.indexOf('.') >= 0) {
                String[] parts = m.split("\\.", -1);
                String d = parts.length > 1 ? parts[1] : "";
                if (d.length() > 2)
                    precioDirecto.setText(parts[0] + "." + d.substring(0, 2));
            }
            recalc();
        });

        // Inventario cambiado -> refrescar sin reiniciar
        bus.subscribe(EventBus.Topic.INVENTORY_CHANGED, ev -> javafx.application.Platform.runLater(() -> {
            cargarCatalogo(); // recarga items y dataset del autocompletado
            renderInsumos(); // re-pinta chips con stock actualizado
            updateDisponible(); // refresca etiqueta de disponible del editor
        }));

        cargarCatalogo();
        refrescarListaRecetas();
        startFileWatch(); // observa data/products.json y data/recetas/

        rbNuevo.setSelected(true);
        aplicarModo(true);
        recalc();
    }

    /* ======================= UI ======================= */

    private VBox cardModo() {
        rbNuevo.setToggleGroup(modo);
        rbRepro.setToggleGroup(modo);

        recetaSelect.setPromptText("Selecciona una receta");
        recetaSelect.setDisable(true);
        recetaSelect.valueProperty().addListener((o, a, v) -> {
            if (rbRepro.isSelected() && v != null)
                cargarRecetaPorNombre(v, true);
        });

        btnModificarReceta.setVisible(false);
        btnModificarReceta.setOnAction(e -> solicitarModificarReceta());

        modo.selectedToggleProperty().addListener((o, a, v) -> aplicarModo(true));

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(10);
        g.setPadding(new Insets(8));
        g.addRow(0, rbNuevo, rbRepro, btnModificarReceta);
        g.addRow(1, new Label("Mis recetas"), recetaSelect);
        return card("¿Qué quieres hacer?", g);
    }

    private VBox cardProducto() {
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
                        .filter(p -> (p.getNombre() + " (" + p.getSku() + ")").equalsIgnoreCase(s))
                        .findFirst().orElse(null);
            }
        });

        // catálogo inicial
        var listaInicial = javafx.collections.FXCollections.observableArrayList(inventory.list());
        // guarda referencia al autocompletado para refrescarlo después
        acMat = new com.papeleria.pos.components.AutoCompleteCombo<>(
                material,
                listaInicial,
                p -> p.getNombre() + " (" + p.getSku() + ")");

        presentacion.getItems().setAll("Unidad");
        presentacion.getSelectionModel().selectFirst();
        cantPorProducto.setEditable(true);
        disponibleLbl.getStyleClass().add("ok-pill");

        Button agregar = new Button("+ Agregar Material");
        agregar.getStyleClass().add("primary");
        agregar.setOnAction(e -> agregarInsumo());
        agregar.setId("btnAgregarMaterial");

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
        precioDirecto.setPromptText("Ej: 80.00");
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

    /* =================== Modo y bloqueo =================== */

    private void aplicarModo(boolean limpiar) {
        boolean nuevo = rbNuevo.isSelected();

        if (limpiar) {
            recetaActual = null;
            nombreFinal.clear();
            skuFinal.clear();
            recetaSelect.getSelectionModel().clearSelection();
            insumos.clear();
            renderInsumos();
            presentacion.getItems().setAll("Unidad");
            presentacion.getSelectionModel().selectFirst();
            cantPorProducto.getValueFactory().setValue(1.0);
            cantidadPorLote.getValueFactory().setValue(1);
            numeroLotes.getValueFactory().setValue(1);
            manoObraUnit.getValueFactory().setValue(0.0);
            pmMargen.setSelected(true);
            margen.getValueFactory().setValue(50.0);
            precioDirecto.clear();
        }

        if (nuevo) {
            recetaBloqueada = false;
            btnModificarReceta.setVisible(false);
        } else {
            recetaBloqueada = true;
            btnModificarReceta.setVisible(true);
            btnModificarReceta.setDisable(recetaActual == null);
        }

        recetaSelect.setDisable(nuevo);
        aplicarBloqueos();
        if (nuevo) {
            if (skuFinal.getText() == null || skuFinal.getText().isBlank())
                skuFinal.setText(genSkuFromName(nombreFinal.getText()));
        }
        recalc();
    }

    private void aplicarBloqueos() {
        boolean nuevo = rbNuevo.isSelected();

        if (nuevo) {
            nombreFinal.setDisable(false);
            btnRegenerarSku.setDisable(false);
            skuFinal.setDisable(false);
            setEditorMaterialEnabled(true);
            bloquearChips(false);
            pmMargen.setDisable(false);
            pmDirecto.setDisable(false);
            margen.setDisable(false);
            precioDirecto.setDisable(!pmDirecto.isSelected());
            return;
        }

        boolean camposEditables = !recetaBloqueada;

        nombreFinal.setDisable(recetaBloqueada);
        btnRegenerarSku.setDisable(true);
        skuFinal.setDisable(true);

        setEditorMaterialEnabled(camposEditables);
        bloquearChips(recetaBloqueada);

        pmMargen.setDisable(false);
        pmDirecto.setDisable(false);
        margen.setDisable(false);
        precioDirecto.setDisable(!pmDirecto.isSelected());

        btnModificarReceta.setDisable(recetaActual == null);
        btnModificarReceta.setText(recetaBloqueada ? "Modificar receta" : "Bloquear receta");
    }

    private void setEditorMaterialEnabled(boolean enabled) {
        material.setDisable(!enabled);
        material.getEditor().setDisable(!enabled);
        presentacion.setDisable(!enabled);
        cantPorProducto.setDisable(!enabled);
        Node btnAgregar = getScene() == null ? null : getScene().lookup("#btnAgregarMaterial");
        if (btnAgregar != null)
            btnAgregar.setDisable(!enabled);
    }

    private void bloquearChips(boolean bloquear) {
        listaInsumos.getChildren().forEach(n -> {
            if (n instanceof HBox hb) {
                hb.getChildren().stream().filter(c -> c instanceof Button).forEach(c -> c.setDisable(bloquear));
            }
        });
    }

    private void solicitarModificarReceta() {
        if (recetaActual == null)
            return;
        if (recetaBloqueada) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("Modificar receta");
            a.setHeaderText("¿Deseas desbloquear y modificar esta receta?");
            a.getButtonTypes().setAll(new ButtonType("Modificar", ButtonBar.ButtonData.OK_DONE),
                    new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE));
            a.showAndWait().ifPresent(bt -> {
                if (bt.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                    recetaBloqueada = false;
                    aplicarBloqueos();
                }
            });
        } else {
            recetaBloqueada = true;
            aplicarBloqueos();
        }
    }

    /* =================== Lógica principal =================== */

    private void onNombreChanged() {
        if (!rbNuevo.isSelected())
            return;
        String nombre = safe(nombreFinal.getText()).trim();
        skuFinal.setText(genSkuFromName(nombre));
        if (nombre.isEmpty())
            return;

        if (Files.exists(pathReceta(nombre))) {
            rbRepro.setSelected(true);
            aplicarModo(false);
            recetaSelect.getSelectionModel().select(nombre);
            cargarRecetaPorNombre(nombre, true);
            flash(AlertBanner.info("Cambié a 'Reproducir receta' porque ya existe la receta: " + nombre));
            return;
        }

        boolean existeNoProd = inventory.search(nombre).stream()
                .anyMatch(p -> p.getNombre() != null && p.getNombre().equalsIgnoreCase(nombre)
                        && !"Producción".equalsIgnoreCase(safe(p.getCategoria())));
        if (existeNoProd) {
            flash(AlertBanner.warn("Ya hay un producto en inventario con ese nombre."));
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

        if (rbNuevo.isSelected() && Files.exists(pathReceta(nombre))) {
            flash(AlertBanner.warn("Ya existe una receta con ese nombre. Usa 'Reproducir receta'."));
            return;
        }
        boolean existeNoProd = inventory.search(nombre).stream()
                .anyMatch(p -> p.getNombre() != null && p.getNombre().equalsIgnoreCase(nombre)
                        && !"Producción".equalsIgnoreCase(safe(p.getCategoria())));
        if (existeNoProd) {
            flash(AlertBanner.warn("Ese nombre pertenece a inventario. Usa otro."));
            return;
        }

        confirmarFabricacion(nombre);
    }

    private void confirmarFabricacion(String nombre) {
        int lotes = numeroLotes.getValue();
        int cantLote = cantidadPorLote.getValue();
        double total = lotes * cantLote;
        String precio = kPrecioFinal.getText();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirmar Fabricación");
        confirm.setHeaderText("¿Deseas fabricar este producto?");
        confirm.setContentText("Producto: " + nombre + "\n" +
                "Lotes: " + lotes + "\n" +
                "Cantidad por lote: " + cantLote + "\n" +
                "Total a fabricar: " + total + "\n" +
                "Precio unitario final: " + precio);

        ButtonType aceptar = new ButtonType("Aceptar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(aceptar, cancelar);

        DialogPane dp = confirm.getDialogPane();
        dp.lookupButton(aceptar).setStyle("-fx-background-color:#2ecc71;-fx-text-fill:white;");
        dp.lookupButton(cancelar).setStyle("-fx-background-color:#e74c3c;-fx-text-fill:white;");

        confirm.showAndWait().ifPresent(res -> {
            if (res == aceptar)
                ejecutarFabricacion(nombre);
        });
    }

    private void ejecutarFabricacion(String nombre) {
        List<ProductionService.InsumoReq> reqs = new ArrayList<>();
        for (Insumo in : insumos)
            reqs.add(new ProductionService.InsumoReq(in.prod.getSku(), in.cantidadPorProducto));

        double precioFinal = parseMoney(kPrecioFinal.getText());
        String sku = rbNuevo.isSelected() ? skuFinal.getText().trim()
                : safe(recetaActual == null ? "" : recetaActual.sku);
        if (sku.isBlank())
            sku = genSkuFromName(nombre);

        Double[] sug = new Double[1];
        boolean ok = production.produce(nombre, sku, cantidadPorLote.getValue(), numeroLotes.getValue(),
                reqs, manoObraUnit.getValue(), sug);
        if (!ok) {
            flash(AlertBanner.warn("Stock insuficiente o datos inválidos"));
            return;
        }

        // Actualiza producto final
        Product pf = inventory.findBySku(sku).orElse(null);
        if (pf != null) {
            pf.setPrecio(round2(precioFinal));
            pf.setCategoria("Producción");
            pf.setUnidad("Unidad");
            pf.setContenido(1.0);
            inventory.upsert(pf);
        }

        // GUARDAR RECETA cuando el flujo es "nuevo"
        if (rbNuevo.isSelected()) {
            RecipesStore.Recipe r = new RecipesStore.Recipe();
            r.nombre = nombre;
            r.sku = sku;
            r.margen = pmMargen.isSelected() ? margen.getValue() : 0.0;
            r.precioDirecto = pmDirecto.isSelected() ? parseD(precioDirecto.getText(), 0.0) : 0.0;
            r.manoObraUnit = manoObraUnit.getValue();
            r.items = new ArrayList<>();
            for (Insumo in : insumos) {
                RecipesStore.Item it = new RecipesStore.Item();
                it.sku = in.prod.getSku();
                it.cantidadBase = round2(inventory.toBase(in.prod, in.cantidadPorProducto));
                r.items.add(it);
            }
            recipes.upsert(r);
            refrescarListaRecetas();
        }

        flash(AlertBanner.success("Fabricación completada"));
        if (rbNuevo.isSelected())
            aplicarModo(true);
        recalc();
    }

    /* =================== Materiales =================== */

    private void cargarCatalogo() {
        var list = new java.util.ArrayList<>(inventory.list());
        list.sort(java.util.Comparator.comparing(
                com.papeleria.pos.models.Product::getNombre,
                java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        Product sel = material.getValue();

        // actualiza items visibles del ComboBox
        material.getItems().setAll(list);

        // refresca dataset del autocompletado
        if (acMat != null) {
            acMat.refreshData(javafx.collections.FXCollections.observableArrayList(list));
        }

        // re-selecciona por SKU si había algo elegido
        if (sel != null) {
            for (var it : material.getItems()) {
                if (it.getSku() != null && it.getSku().equalsIgnoreCase(sel.getSku())) {
                    material.setValue(it);
                    break;
                }
            }
        }
    }

    private void agregarInsumo() {
        Product pSel = material.getValue();
        double qUser = cantPorProducto.getValue();

        if (pSel == null) {
            flash(AlertBanner.warn("Selecciona un material"));
            return;
        }
        if (qUser <= 0) {
            flash(AlertBanner.warn("Cantidad inválida"));
            return;
        }

        // Reobtén el producto “fresco” desde inventario por si cambió stock/precio
        Product p = inventory.findBySku(pSel.getSku()).orElse(pSel);

        // Cantidad en unidad del producto según presentación elegida
        double q = toProductUnit(p, qUser);
        String human = humanQtyFor(p, q);

        // Si ya estaba en la lista, acumula
        for (Insumo x : new ArrayList<>(insumos)) {
            if (x.prod.getSku().equalsIgnoreCase(p.getSku())) {
                x.cantidadPorProducto = x.cantidadPorProducto + q;
                x.humanResumen = humanQtyFor(p, x.cantidadPorProducto);
                renderInsumos();
                recalc();
                limpiarEditor();
                return;
            }
        }

        // Nuevo insumo
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
            del.setDisable(recetaBloqueada && rbRepro.isSelected());
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
        if (p != null) {
            // reobtén del inventario por SKU para evitar caché viejo del ComboBox
            p = inventory.findBySku(p.getSku()).orElse(p);
        }
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

    /* =================== Recetas =================== */

    private Path recetasDir() {
        return Path.of("").toAbsolutePath().resolve("data").resolve("recetas");
    }

    private Path pathReceta(String nombre) {
        return recetasDir().resolve(slug(nombre) + ".json");
    }

    private List<String> listaFabricables() {
        Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
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
        var opt = recipes.getByName(nombre);
        if (opt.isEmpty()) {
            flash(AlertBanner.warn("No se encontró la receta en recipes.json"));
            return;
        }
        var rf = opt.get();

        // Snapshot interno
        recetaActual = new RecipeFile();
        recetaActual.nombre = rf.nombre;
        recetaActual.sku = rf.sku == null ? "" : rf.sku;
        recetaActual.margen = rf.margen;
        recetaActual.precioDirecto = rf.precioDirecto;
        recetaActual.manoObraUnit = rf.manoObraUnit;

        if (autofill) {
            // Rellena encabezado
            nombreFinal.setText(rf.nombre);
            skuFinal.setText(recetaActual.sku);
            manoObraUnit.getValueFactory().setValue(rf.manoObraUnit <= 0 ? 0.0 : rf.manoObraUnit);

            // Precio: usa uno u otro y limpia el contrario
            if (rf.precioDirecto > 0) {
                pmDirecto.setSelected(true);
                precioDirecto.setText(String.format(java.util.Locale.US, "%.2f", rf.precioDirecto));
                margen.getValueFactory().setValue(0.0);
            } else {
                pmMargen.setSelected(true);
                margen.getValueFactory().setValue(rf.margen <= 0 ? 50.0 : rf.margen);
                precioDirecto.clear();
            }

            // Marca visualmente la receta elegida
            if (!Objects.equals(recetaSelect.getValue(), rf.nombre)) {
                recetaSelect.getSelectionModel().select(rf.nombre);
            }
        }

        // Materiales desde inventario “fresco”
        insumos.clear();
        for (RecipesStore.Item it : rf.items) {
            inventory.findBySku(it.sku).ifPresent(p -> {
                // cantidadBase viene en unidad base; conviértela a unidad del producto para la
                // UI
                double qPU = fromBaseToProductUnit(p, it.cantidadBase);
                insumos.add(new Insumo(p, qPU, humanQtyFor(p, qPU)));
            });
        }
        renderInsumos();

        // Bloquea edición de receta hasta que el usuario decida “Modificar receta”
        recetaBloqueada = true;
        aplicarBloqueos();

        recalc();
    }

    private void refrescarListaRecetas() {
        var nombres = new ArrayList<String>();
        for (RecipesStore.Recipe r : recipes.list())
            nombres.add(r.nombre);
        Collections.sort(nombres, String.CASE_INSENSITIVE_ORDER);
        recetaSelect.setItems(FXCollections.observableArrayList(nombres));
    }

    private RecipeFile snapshotReceta(String nombre, String sku) {
        RecipeFile rf = new RecipeFile();
        rf.nombre = nombre;
        rf.sku = sku;
        rf.margen = pmMargen.isSelected() ? margen.getValue() : 0.0;
        rf.precioDirecto = pmDirecto.isSelected() ? parseD(precioDirecto.getText(), 0.0) : 0.0;
        rf.manoObraUnit = manoObraUnit.getValue();
        rf.items = new ArrayList<>();
        for (Insumo in : insumos) {
            double base = inventory.toBase(in.prod, in.cantidadPorProducto);
            rf.items.add(new RecipeItem(
                    in.prod.getSku(),
                    "BASE", // o "" si prefieres
                    round2(inventory.toBase(in.prod, in.cantidadPorProducto))));

        }
        return rf;
    }

    private void writeRecipeFile(RecipeFile rf) {
        try {
            Files.createDirectories(recetasDir());
            Path p = pathReceta(rf.nombre);
            String json = gson.toJson(rf);
            Files.writeString(p, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            flash(AlertBanner.warn("No se pudo guardar la receta"));
        }
    }

    /* =================== Utilidades =================== */

    private void recalc() {
        double costoMatUnit = 0.0;
        for (Insumo i : insumos)
            costoMatUnit += round2(i.prod.getPrecio() * i.cantidadPorProducto);
        double costoUnit = round2(costoMatUnit + manoObraUnit.getValue());
        double precio = pmDirecto.isSelected()
                ? parseD(precioDirecto.getText(), costoUnit)
                : round2(costoUnit * (1.0 + (margen.getValue() / 100.0)));
        precio = round2(precio);

        kCostoMat.setText(money(costoMatUnit));
        kCostoUnit.setText(money(costoUnit));
        kPrecioFinal.setText(money(precio));
    }

    private void flash(AlertBanner b) {
        header.getChildren().removeIf(n -> n instanceof AlertBanner);
        header.getChildren().add(0, b);
        PauseTransition t = new PauseTransition(Duration.seconds(2.2));
        t.setOnFinished(e -> header.getChildren().remove(b));
        t.play();
    }

    private String money(double v) {
        return String.format(java.util.Locale.US, "$%,.2f", v);
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

    private String slug(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.replaceAll("[^\\p{L}0-9]+", "-").replaceAll("^-+|-+$", "").toLowerCase(java.util.Locale.ROOT);
        if (n.isBlank())
            n = "pf";
        return n;
    }

    // Conversión unidades
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

    // SKU
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

    /* ====== Watcher de archivos para refrescar catálogos/recetas ====== */
    private void startFileWatch() {
        try {
            Path dataDir = Path.of("").toAbsolutePath().resolve("data");
            Files.createDirectories(dataDir);
            Files.createDirectories(recetasDir());

            // Usa un thread daemon para no bloquear cierre.
            var ex = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "pos-filewatch");
                t.setDaemon(true);
                return t;
            });

            ex.scheduleWithFixedDelay(() -> {
                try {
                    // Si cambió products.json -> refrescar materiales
                    Path products = dataDir.resolve("products.json");
                    // Si cambió lista de recetas (nuevo/actualizado) -> refrescar select
                    // Implementación simple: escanea timestamps y refresca si cambia tamaño o
                    // mtime.
                    long key = (Files.exists(products) ? Files.getLastModifiedTime(products).toMillis() : 0)
                            ^ directoryStamp(recetasDir());
                    // cache estática en campo local del runnable
                    stampCache = (stampCache == 0L) ? key : stampCache;
                    if (key != stampCache) {
                        stampCache = key;
                        Platform.runLater(() -> {
                            cargarCatalogo();
                            refrescarListaRecetas();
                            renderInsumos();
                        });
                    }
                } catch (Exception ignored) {
                }
            }, 500, 700, TimeUnit.MILLISECONDS);

        } catch (Exception ignored) {
        }
    }

    private long stampCache = 0L;

    private long directoryStamp(Path dir) {
        try (var s = Files.list(dir)) {
            return s.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() ^ p.toString().hashCode();
                        } catch (Exception e) {
                            return p.toString().hashCode();
                        }
                    }).reduce(0L, (a, b) -> a ^ b);
        } catch (Exception e) {
            return 0L;
        }
    }

    /* =================== Modelos =================== */
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
