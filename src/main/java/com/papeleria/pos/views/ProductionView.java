package com.papeleria.pos.views;

import com.papeleria.pos.components.AlertBanner;
import com.papeleria.pos.components.AutoCompleteCombo;
import com.papeleria.pos.models.Product;
import com.papeleria.pos.services.EventBus;
import com.papeleria.pos.services.InventoryService;
import com.papeleria.pos.services.ProductionService;
import com.papeleria.pos.services.SessionService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** UI “Armar Productos” como en el mock. Compatible con tu modelo actual. */
public class ProductionView extends BorderPane {

    // Servicios
    private final SessionService session;
    private final ProductionService production;
    private final InventoryService inventory;
    private final EventBus bus;

    // Host para banners (evita error de "effectively final")
    private VBox bannerHost;

    // Paso 1
    private final TextField nombreFinal = new TextField();
    private final TextField skuFinal = new TextField();

    // Paso 2
    private final Spinner<Integer> cantidadPorLote = new Spinner<>(1, 1_000_000, 10, 1);
    private final Spinner<Integer> numeroLotes = new Spinner<>(1, 1_000_000, 1, 1);
    private final Label totalAFabricar = new Label("10");

    // Paso 3
    private final ComboBox<Product> material = new ComboBox<>();
    private final Spinner<Double> cantPorProducto = new Spinner<>(0.01, 1_000_000.0, 20.0, 1.0);
    private final Label disponibleLbl = new Label("—");
    private final FlowPane listaInsumos = new FlowPane(10, 10);
    private final ObservableList<Insumo> insumos = FXCollections.observableArrayList();

    // Resumen
    private final Label kCostoMat = new Label("$0.00");
    private final Label kCostoUnit = new Label("$0.00");
    private final Label kPrecioSug = new Label("$0.00");
    private final Spinner<Double> manoObraUnit = new Spinner<>(0.0, 1_000_000.0, 0.0, 1.0);

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
        // fija host para banners
        this.bannerHost = header;

        // Página
        VBox page = new VBox(12);
        page.getStyleClass().add("page");
        page.getChildren().addAll(
                cardPaso1Producto(),
                cardPaso2Cantidades(),
                cardPaso3Materiales(),
                cardResumenCostos());
        setCenter(page);

        // Eventos
        cantidadPorLote.valueProperty().addListener((o, a, v) -> recalc());
        numeroLotes.valueProperty().addListener((o, a, v) -> recalc());
        manoObraUnit.valueProperty().addListener((o, a, v) -> recalc());
        bus.subscribe(EventBus.Topic.INVENTORY_CHANGED, e -> javafx.application.Platform.runLater(this::recalc));

        cargarCatalogo();
        recalc();
    }

    // ====== Layout ======
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
        // autocompletar nombres existentes
        List<String> nombres = inventory.list().stream()
                .map(p -> p.getNombre() == null ? "" : p.getNombre()).sorted().toList();
        ComboBox<String> nombreCombo = new ComboBox<>(FXCollections.observableArrayList(nombres));
        nombreCombo.setEditable(true);
        new AutoCompleteCombo<>(cast(nombreCombo), nombres, s -> s);
        nombreCombo.getEditor().textProperty().bindBidirectional(nombreFinal.textProperty());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(8));
        grid.addRow(0, new Label("Nombre del producto"), nombreCombo);
        grid.addRow(1, new Label("Código del producto"), skuFinal);

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
        fila.addRow(1, new Label("Cantidad por producto"), cantPorProducto);
        fila.addRow(2, new Label("Disponible"), disponibleLbl);

        material.valueProperty().addListener((o, a, v) -> updateDisponible());
        cantPorProducto.valueProperty().addListener((o, a, v) -> updateDisponible());

        listaInsumos.setPrefWrapLength(520);

        return card("Materiales necesarios", new VBox(10, fila, rightAligned(new Region(), agregar), listaInsumos));
    }

    private VBox cardResumenCostos() {
        VBox k1 = kpi("$0.00", "Costo de materiales\nPor cada producto");
        VBox k2 = kpi("$0.00", "Costo unitario total\nIncluyendo mano de obra");
        VBox k3 = kpi("$0.00", "Precio sugerido\n50% de ganancia");

        ((Label) ((VBox) k1.getChildren().get(0)).getChildren().get(0)).textProperty().bind(kCostoMat.textProperty());
        ((Label) ((VBox) k2.getChildren().get(0)).getChildren().get(0)).textProperty().bind(kCostoUnit.textProperty());
        ((Label) ((VBox) k3.getChildren().get(0)).getChildren().get(0)).textProperty().bind(kPrecioSug.textProperty());

        manoObraUnit.setEditable(true);

        HBox kpis = new HBox(12, k1, k2, k3);
        Button draft = new Button("Guardar como borrador");
        draft.getStyleClass().add("ghost");
        draft.setOnAction(e -> flash(AlertBanner.success("Borrador guardado")));
        Button fabricar = new Button();
        fabricar.getStyleClass().add("success");
        fabricar.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> "¡Fabricar " + totalAFabricar.getText() + " productos!",
                totalAFabricar.textProperty()));
        fabricar.setOnAction(e -> fabricarAccion());
        HBox buttons = new HBox(10, draft, fabricar);

        return card("Resumen de costos", new VBox(12, kpis, buttons));
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

    // ====== Lógica ======
    private void cargarCatalogo() {
        material.setItems(FXCollections.observableArrayList(inventory.list()));
    }

    private void agregarInsumo() {
        Product p = material.getValue();
        double q = cantPorProducto.getValue();
        if (p == null) {
            flash(AlertBanner.warn("Selecciona un material"));
            return;
        }
        if (q <= 0) {
            flash(AlertBanner.warn("Cantidad inválida"));
            return;
        }

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
        cantPorProducto.getValueFactory().setValue(20.0);
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
        double q = cantPorProducto.getValue();
        disponibleLbl.setText(p == null ? "—" : disponibleTexto(p, q));
    }

    private String disponibleTexto(Product p, double qPorProducto) {
        // requiere InventoryService.toBase(...)
        double reqTotalBase = inventory.toBase(p, qPorProducto * numeroLotes.getValue() * cantidadPorLote.getValue());
        double stockBase = p.getStock();
        boolean ok = stockBase + 1e-9 >= reqTotalBase;
        return trim2(stockBase) + "  " + (ok ? "✔" : "✖");
    }

    private String okClase(Insumo i) {
        double reqTotalBase = inventory.toBase(i.prod,
                i.cantidadPorProducto * numeroLotes.getValue() * cantidadPorLote.getValue());
        return (i.prod.getStock() + 1e-9 >= reqTotalBase) ? "ok-pill" : "warn-pill";
    }

    private void recalc() {
        int total = Math.max(1, cantidadPorLote.getValue()) * Math.max(1, numeroLotes.getValue());
        totalAFabricar.setText(String.valueOf(total));

        double costoMatUnit = 0.0;
        for (Insumo i : insumos) {
            costoMatUnit += i.prod.getPrecio() * i.cantidadPorProducto; // precio por unidad del producto
        }
        double costoUnitario = costoMatUnit + manoObraUnit.getValue();
        double precioSugerido = costoUnitario * 1.5; // 50% ganancia

        kCostoMat.setText(money(costoMatUnit));
        kCostoUnit.setText(money(costoUnitario));
        kPrecioSug.setText(money(precioSugerido));
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

        // Servicio central
        List<ProductionService.InsumoReq> reqs = new ArrayList<>();
        for (Insumo in : insumos)
            reqs.add(new ProductionService.InsumoReq(in.prod.getSku(), in.cantidadPorProducto));

        Double[] sug = new Double[1];
        boolean ok = production.produce(
                nombre,
                skuFinal.getText() == null ? null : skuFinal.getText().trim(),
                Math.max(1, cantidadPorLote.getValue()),
                Math.max(1, numeroLotes.getValue()),
                reqs,
                0.0,
                sug);
        if (!ok) {
            flash(AlertBanner.danger("Stock insuficiente o datos incompletos"));
            return;
        }

        flash(AlertBanner.success("Producción registrada"));
        insumos.clear();
        renderInsumos();
        recalc();
    }

    // ====== Util ======
    private <T> ComboBox<T> cast(ComboBox<?> cb) {
        @SuppressWarnings("unchecked")
        ComboBox<T> c = (ComboBox<T>) cb;
        return c;
    }

    private static class Insumo {
        final Product prod;
        double cantidadPorProducto;

        Insumo(Product p, double q) {
            this.prod = Objects.requireNonNull(p);
            this.cantidadPorProducto = q;
        }
    }

    private String money(double v) {
        return String.format("$%.2f", v);
    }

    private String trim2(double v) {
        return String.format("%.2f", v);
    }

    private void flash(AlertBanner banner) {
        if (bannerHost == null)
            return;
        bannerHost.getChildren().removeIf(n -> n instanceof AlertBanner);
        bannerHost.getChildren().add(0, banner);
        var t = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2.5));
        t.setOnFinished(e -> bannerHost.getChildren().remove(banner));
        t.play();
    }
}
