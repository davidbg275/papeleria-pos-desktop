package com.papeleria.pos.views;

import com.papeleria.pos.components.AutoCompleteCombo;
import com.papeleria.pos.components.AlertBanner;
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
import javafx.util.StringConverter;
import java.util.*;
import java.util.stream.Collectors;

public class ProductionView extends BorderPane {

    private final SessionService session;
    private final ProductionService production; // reservado si lo usas internamente
    private final InventoryService inventory;
    private final EventBus bus;

    // UI principal
    private final ComboBox<String> productoFinal = new ComboBox<>();
    private final Spinner<Integer> piezasPorLote = new Spinner<>(1, 100000, 10, 1);
    private final Spinner<Double> veces = new Spinner<>(0.1, 1000.0, 1.0, 0.5);
    private final Spinner<Double> ganancia = new Spinner<>(0.0, 3.0, 0.30, 0.05);
    private final Spinner<Double> extra = new Spinner<>(0.0, 100000.0, 0.0, 1.0);

    private final ComboBox<Product> material = new ComboBox<>();
    private final ComboBox<String> medida = new ComboBox<>(
            FXCollections.observableArrayList("Unidad base", "Paquete/Rollo"));
    private final Spinner<Double> cantidad = new Spinner<>(0.0, 100000.0, 1.0, 1.0);

    private final FlowPane chips = new FlowPane(8, 8);
    private final ObservableList<Insumo> insumos = FXCollections.observableArrayList();

    // KPIs
    private final Label kCostoMat = new Label("$0.00");
    private final Label kCostoUnit = new Label("$0.00");
    private final Label kPrecioSug = new Label("$0.00");

    public ProductionView(SessionService session, ProductionService production, InventoryService inventory,
            EventBus bus) {
        this.session = session;
        this.production = production;
        this.inventory = inventory;
        this.bus = bus;

        // ----- Lado izquierdo: chips + KPIs -----
        VBox left = new VBox(12);
        Label title = new Label("Producción / Armar productos");
        title.getStyleClass().add("h1");
        Label sub = new Label("Define la receta (BOM), fabrica y actualiza inventario.");
        sub.getStyleClass().add("subtle");

        HBox kpis = new HBox(12, kpi("Costo materiales", kCostoMat),
                kpi("Costo unitario", kCostoUnit),
                kpi("Precio sugerido", kPrecioSug));
        VBox chipCard = new VBox(8, new Label("Insumos"), chips);
        chipCard.getStyleClass().add("card");
        chips.setPrefWrapLength(260);

        left.getChildren().addAll(title, sub, kpis, chipCard);
        left.setPadding(new Insets(12));

        // ----- Centro/Derecha: formulario -----
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(12));
        form.getStyleClass().add("card");

        // Producto final (ComboBox editable con autocompletar)
        productoFinal.setEditable(true);
        List<String> nombres = inventory.list().stream().map(Product::getNombre).sorted().collect(Collectors.toList());
        productoFinal.setItems(FXCollections.observableArrayList(nombres));
        new AutoCompleteCombo<>(cast(productoFinal), nombres, s -> s); // truco para reutilizar clase genérica

        // Materiales: ComboBox<Product> con autocompletar
        ObservableList<Product> productos = FXCollections.observableArrayList(inventory.list());
        material.setItems(productos);
        material.setConverter(new StringConverter<Product>() {
            @Override
            public String toString(Product p) {
                return p == null ? "" : p.getNombre() + "  (" + p.getSku() + ")";
            }

            @Override
            public Product fromString(String s) {
                return productos.stream().filter(p -> (p.getNombre() + " (" + p.getSku() + ")").equalsIgnoreCase(s))
                        .findFirst().orElse(null);
            }
        });
        new AutoCompleteCombo<>(material, productos, p -> p.getNombre() + " " + p.getSku());
        medida.getSelectionModel().selectFirst();

        int r = 0;
        form.addRow(r++, new Label("Producto final"), productoFinal);
        form.addRow(r++, new Label("Piezas por lote"), piezasPorLote);
        form.addRow(r++, new Label("Veces a fabricar"), veces);
        form.addRow(r++, new Label("Ganancia deseada"), ganancia);
        form.addRow(r++, new Label("Costo extra por lote"), extra);
        form.add(new Separator(), 0, r++, 2, 1);
        form.addRow(r++, new Label("Material"), material);
        form.addRow(r++, new Label("Medida"), medida);
        form.addRow(r++, new Label("Cantidad"), cantidad);

        Button addInsumo = new Button("Agregar insumo");
        addInsumo.getStyleClass().add("primary");
        form.add(addInsumo, 1, r++);
        Button fabricar = new Button("Fabricar");
        fabricar.getStyleClass().add("success");
        form.add(fabricar, 1, r++);

        setLeft(left);
        setCenter(form);
        BorderPane.setMargin(form, new Insets(0, 0, 0, 12));

        // Eventos
        addInsumo.setOnAction(e -> agregarInsumo());
        fabricar.setOnAction(e -> fabricar());

        // Recalcular KPIs cada cambio
        piezasPorLote.valueProperty().addListener((o, old, v) -> recalc());
        veces.valueProperty().addListener((o, old, v) -> recalc());
        ganancia.valueProperty().addListener((o, old, v) -> recalc());
        extra.valueProperty().addListener((o, old, v) -> recalc());
        cantidad.valueProperty().addListener((o, old, v) -> recalc());

        recalc();
    }

    // ===== util =====
    private <T> ComboBox<T> cast(ComboBox<?> cb) {
        @SuppressWarnings("unchecked")
        ComboBox<T> c = (ComboBox<T>) cb;
        return c;
    }

    private VBox kpi(String title, Label value) {
        Label t = new Label(title);
        t.getStyleClass().add("subtle");
        value.setStyle("-fx-font-size: 22px; -fx-font-weight: 900;");
        VBox box = new VBox(4, t, value);
        VBox wrap = new VBox(box);
        wrap.getStyleClass().add("card");
        wrap.setPadding(new Insets(12));
        wrap.setPrefWidth(220);
        return wrap;
    }

    private void agregarInsumo() {
        Product p = material.getValue();
        if (p == null) {
            ((VBox) getLeft()).getChildren().add(0, AlertBanner.warn("Selecciona un material"));
            return;
        }
        double cant = cantidad.getValue();
        if (cant <= 0) {
            ((VBox) getLeft()).getChildren().add(0, AlertBanner.warn("Cantidad inválida"));
            return;
        }
        String med = medida.getValue();

        Insumo in = new Insumo(p, med, cant);
        insumos.add(in);
        renderChips();
        recalc();
        // limpiar edición rápida
        material.getSelectionModel().clearSelection();
        material.getEditor().clear();
        cantidad.getValueFactory().setValue(1.0);
        medida.getSelectionModel().selectFirst();
    }

    private void renderChips() {
        chips.getChildren().clear();
        for (Insumo i : insumos) {
            HBox chip = new HBox(8, new Label(i.texto()), new Button("❌"));
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("card");
            ((Button) chip.getChildren().get(1)).setOnAction(e -> {
                insumos.remove(i);
                renderChips();
                recalc();
            });
            chips.getChildren().add(chip);
        }
    }

    private void recalc() {
        double costoMat = insumos.stream().mapToDouble(Insumo::costo).sum();
        int piezas = Math.max(1, piezasPorLote.getValue());
        double costoUnit = (costoMat + extra.getValue()) / piezas;
        double precioSug = costoUnit * (1.0 + ganancia.getValue());
        kCostoMat.setText(String.format("$%.2f", costoMat));
        kCostoUnit.setText(String.format("$%.2f", costoUnit));
        kPrecioSug.setText(String.format("$%.2f", precioSug));
    }

    private void fabricar() {
        String nombreFinal = productoFinal.getEditor().getText().trim();
        if (nombreFinal.isEmpty()) {
            ((VBox) getLeft()).getChildren().add(0, AlertBanner.warn("Escribe el producto final"));
            return;
        }
        int piezas = Math.max(1, piezasPorLote.getValue());
        double vecesFab = Math.max(0.1, veces.getValue());

        // Validar stock de insumos
        for (Insumo i : insumos) {
            double req = i.factor() * i.cantidad * vecesFab;
            Product inv = inventory.findBySku(i.prod.getSku()).orElse(null); // ← desempaquetar Optional
            if (inv == null || inv.getStock() < req) {
                ((VBox) getLeft()).getChildren().add(0,
                        AlertBanner.danger("Stock insuficiente: " + i.prod.getNombre()));
                return;
            }
        }

        // Descontar insumos
        for (Insumo i : insumos) {
            double req = i.factor() * i.cantidad * vecesFab;
            inventory.adjustStock(i.prod.getSku(), -req);
        }

        // Crear/actualizar producto final
        Product exist = inventory.search(nombreFinal).stream()
                .filter(p -> p.getNombre().equalsIgnoreCase(nombreFinal)).findFirst().orElse(null);

        if (exist == null) {
            // sugerir creación
            Dialog<Product> d = new Dialog<>();
            d.setTitle("Crear producto final");
            d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            GridPane grid = new GridPane();
            grid.setHgap(8);
            grid.setVgap(8);
            grid.setPadding(new Insets(10));
            TextField sku = new TextField("PF-" + Math.abs(nombreFinal.hashCode() % 100000));
            TextField cat = new TextField("Producción");
            TextField uni = new TextField("pza");
            TextField cont = new TextField("0");
            TextField precio = new TextField(kPrecioSug.getText().replace("$", ""));
            grid.addRow(0, new Label("SKU"), sku);
            grid.addRow(1, new Label("Categoría"), cat);
            grid.addRow(2, new Label("Unidad"), uni);
            grid.addRow(3, new Label("Contenido"), cont);
            grid.addRow(4, new Label("Precio base"), precio);
            d.getDialogPane().setContent(grid);
            d.setResultConverter(bt -> {
                if (bt != ButtonType.OK)
                    return null;
                try {
                    return new Product(sku.getText().trim(), nombreFinal, cat.getText().trim(), uni.getText().trim(),
                            Double.parseDouble(cont.getText().trim()),
                            Double.parseDouble(precio.getText().trim()), 0.0);
                } catch (Exception ex) {
                    return null;
                }
            });
            Optional<Product> res = d.showAndWait();
            if (res.isEmpty()) {
                ((VBox) getLeft()).getChildren().add(0, AlertBanner.warn("Cancelado"));
                return;
            }
            exist = res.get();
            inventory.upsert(exist);
        }

        // Sumar stock final
        double incremento = piezas * vecesFab;
        inventory.adjustStock(exist.getSku(), incremento);

        bus.publish(EventBus.Topic.INVENTORY_CHANGED, "PRODUCTION");
        ((VBox) getLeft()).getChildren().add(0, AlertBanner.success("Producción registrada"));
        insumos.clear();
        renderChips();
        recalc();
    }

    // ===== Insumo =====
    private static class Insumo {
        final Product prod;
        final String medida;
        final double cantidad;

        Insumo(Product prod, String medida, double cantidad) {
            this.prod = prod;
            this.medida = medida;
            this.cantidad = cantidad;
        }

        double factor() {
            return ("Paquete/Rollo".equals(medida) && prod.getContenido() > 0) ? prod.getContenido() : 1.0;
        }

        double costo() {
            return prod.getPrecio() * cantidad * factor();
        }

        String texto() {
            return prod.getNombre() + " x " + cantidad + " (" + medida + ")";
        }
    }
}
