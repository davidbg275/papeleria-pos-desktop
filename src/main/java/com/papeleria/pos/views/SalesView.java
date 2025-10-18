package com.papeleria.pos.views;

import com.papeleria.pos.components.AlertBanner;
import com.papeleria.pos.models.Product;
import com.papeleria.pos.models.Sale;
import com.papeleria.pos.models.SaleItem;
import com.papeleria.pos.services.EventBus;
import com.papeleria.pos.services.InventoryService;
import com.papeleria.pos.services.SalesService;
import com.papeleria.pos.services.SessionService;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.List;
import java.util.UUID;

public class SalesView extends BorderPane {

    private final SessionService session;
    private final SalesService sales;
    private final InventoryService inventory;
    private final EventBus bus;

    private final TextField filtro = new TextField();
    private final FlowPane grid = new FlowPane(12, 12);

    private final TableView<SaleItem> cartTable = new TableView<>();
    private final ObservableList<SaleItem> cart = FXCollections.observableArrayList();
    private final Label totalLbl = new Label("$0.00");
    private final TextField efectivo = new TextField();

    public SalesView(SessionService session, SalesService sales, InventoryService inventory, EventBus bus) {
        this.session = session;
        this.sales = sales;
        this.inventory = inventory;
        this.bus = bus;

        // ---- Izquierda (título + filtro + grilla de cards) ----
        VBox left = new VBox(12);
        Label title = new Label("Punto de Venta");
        title.getStyleClass().add("h1");
        Label subtitle = new Label("Busca productos y gestiona tu carrito de compras");
        subtitle.getStyleClass().add("subtle");
        filtro.setPromptText("Buscar por código o nombre del producto...");
        filtro.textProperty().addListener((o, old, v) -> renderGrid());
        grid.setPrefWrapLength(760);
        ScrollPane sc = new ScrollPane(grid);
        sc.setFitToWidth(true);
        sc.setStyle("-fx-background-color: transparent;");
        left.getChildren().addAll(new VBox(4, title, subtitle), new HBox(filtro), sc);
        left.setPadding(new Insets(6));

        // ---- Derecha (carrito) ----
        VBox right = new VBox(12);
        right.getStyleClass().add("cart");
        Label cartTitle = new Label("Carrito de Compras");
        cartTitle.getStyleClass().add("h1");

        cartTable.setItems(cart);
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<SaleItem, String> cProd = new TableColumn<>("Producto");
        cProd.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombre()));
        TableColumn<SaleItem, Number> cCant = new TableColumn<>("Cant.");
        cCant.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getCantidadBase()));
        TableColumn<SaleItem, Number> cPrecio = new TableColumn<>("Precio");
        cPrecio.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getPrecioUnitario()));
        TableColumn<SaleItem, Number> cSub = new TableColumn<>("Subtotal");
        cSub.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getSubtotal()));
        cartTable.getColumns().setAll(cProd, cCant, cPrecio, cSub);
        cartTable.setPrefHeight(380);

        HBox totalBox = new HBox(8, new Label("Total:"), totalLbl);
        totalLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: 900;");
        totalBox.setAlignment(Pos.CENTER_RIGHT);

        efectivo.setPromptText("Efectivo recibido");

        Button cobrar = new Button("Pagar en Efectivo");
        cobrar.getStyleClass().add("success");
        Button limpiar = new Button("Limpiar Carrito");
        limpiar.getStyleClass().add("ghost");
        VBox.setVgrow(cartTable, Priority.ALWAYS);

        right.getChildren().addAll(
                cartTitle, cartTable, new Separator(), totalBox,
                new HBox(8, new Label("Efectivo:"), efectivo),
                new HBox(8, cobrar, limpiar));

        setLeft(left);
        setRight(right);
        setPadding(new Insets(6));
        BorderPane.setMargin(right, new Insets(6, 0, 6, 12));

        // Handlers principales
        cobrar.setOnAction(e -> cobrarAccion());
        limpiar.setOnAction(e -> {
            cart.clear();
            recalcTotal();
            flash((VBox) getLeft(), AlertBanner.success("Carrito limpio"));
        });

        // Render inicial y refresco por eventos
        renderGrid();
        bus.subscribe(EventBus.Topic.INVENTORY_CHANGED, e -> javafx.application.Platform.runLater(this::renderGrid));
    }

    private void renderGrid() {
        grid.getChildren().clear();
        String q = filtro.getText() == null ? "" : filtro.getText().trim().toLowerCase();
        List<Product> productos = inventory.list();
        for (Product p : productos) {
            if (!q.isEmpty()) {
                String hay = (p.getSku() + " " + p.getNombre()).toLowerCase();
                if (!hay.contains(q))
                    continue;
            }
            VBox card = new VBox(6);
            card.getStyleClass().add("product-card");

            Label name = new Label(p.getNombre());
            name.getStyleClass().add("name");
            Label code = new Label("Código: " + p.getSku());
            code.getStyleClass().add("code");

            // Stock visible y coloreado
            Label stock = new Label(stockText(p));
            stock.getStyleClass().add("stock");
            stock.getStyleClass().add(stockClass(p.getStock()));

            Label price = new Label(String.format("$%.2f", p.getPrecio()));
            price.getStyleClass().add("price");

            Button add = new Button("+");
            add.getStyleClass().addAll("product-add", "primary");
            add.setOnAction(ev -> {
                Double baseQty = promptCantidad(p);
                if (baseQty == null)
                    return;
                if (!sales.validarStock(p.getSku(), baseQty)) {
                    flash((VBox) getLeft(), AlertBanner.warn("Stock insuficiente"));
                    return;
                }
                addToCart(p, baseQty);
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox bottom = new HBox(8, price, spacer, add);

            card.getChildren().addAll(name, code, stock, bottom);
            grid.getChildren().add(card);
        }
    }

    private String stockText(Product p) {
        String u = p.getUnidad() == null ? "" : p.getUnidad();
        return "Stock: " + String.format("%.2f", p.getStock()) + " " + u;
    }

    private String stockClass(double stock) {
        if (stock <= 0.0)
            return "stock-zero"; // rojo
        if (stock <= 5.0)
            return "stock-low"; // naranja
        return "stock-ok"; // verde
    }

    private void addToCart(Product prod, double cantBase) {
        if (!sales.validarStock(prod.getSku(), cantBase)) {
            flash((VBox) getLeft(), AlertBanner.danger("Stock insuficiente"));
            return;
        }
        SaleItem it = new SaleItem(prod.getSku(), prod.getNombre(), cantBase, prod.getPrecio());
        cart.add(it);
        recalcTotal();
    }

    private void cobrarAccion() {
        if (cart.isEmpty()) {
            flash((VBox) getLeft(), AlertBanner.warn("Carrito vacío"));
            return;
        }
        double total = cart.stream().mapToDouble(SaleItem::getSubtotal).sum();
        double ef = parseDouble(
                ((TextField) ((HBox) ((VBox) getRight()).getChildren().get(4)).getChildren().get(1)).getText(), 0.0);
        if (ef < total) {
            flash((VBox) getLeft(), AlertBanner.warn("Efectivo insuficiente"));
            return;
        }
        Sale s = new Sale(UUID.randomUUID().toString().substring(0, 8));
        for (SaleItem it : cart)
            s.agregarItem(it);
        s.setEfectivo(ef);
        sales.cobrarYGuardar(s); // descuenta stock, guarda venta y publica eventos

        cart.clear();
        recalcTotal();
        ((TextField) ((HBox) ((VBox) getRight()).getChildren().get(4)).getChildren().get(1)).clear();
        flash((VBox) getLeft(), AlertBanner.success("Venta registrada. Ticket generado."));
    }

    private void recalcTotal() {
        double total = cart.stream().mapToDouble(SaleItem::getSubtotal).sum();
        totalLbl.setText(String.format("$%.2f", total));
    }

    private double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.replace(",", ".").trim());
        } catch (Exception e) {
            return def;
        }
    }

    // Diálogo para vender por unidad/presentación/múltiplos → SIEMPRE convierte a
    // UNIDAD BASE
    private Double promptCantidad(Product p) {
        Dialog<Double> d = new Dialog<>();
        d.setTitle("Cantidad – " + p.getNombre());
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ChoiceBox<String> medida = new ChoiceBox<>();
        medida.getItems().add("Unidad base (" + p.getUnidad() + ")");
        if (p.getContenido() > 0)
            medida.getItems().add("Paquete/Rollo (" + p.getContenido() + " " + p.getUnidad() + ")");
        String u = p.getUnidad() == null ? "" : p.getUnidad().toLowerCase();
        if (u.equals("m") || u.equals("metro") || u.equals("metros")) {
            medida.getItems().add("Metro");
            medida.getItems().add("Centímetro");
        }
        medida.getSelectionModel().selectFirst();

        Spinner<Double> cantidad = new Spinner<>(0.01, 1_000_000.0, 1.0, 1.0);
        cantidad.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Medida"), medida);
        grid.addRow(1, new Label("Cantidad"), cantidad);
        d.getDialogPane().setContent(grid);

        d.setResultConverter(bt -> {
            if (bt != ButtonType.OK)
                return null;
            double q = cantidad.getValue();
            String m = medida.getValue();
            if (q <= 0)
                return null;

            if (m.startsWith("Paquete/Rollo")) {
                if (p.getContenido() <= 0)
                    return null;
                return q * p.getContenido(); // paquete de N unidades base
            } else if (m.equals("Metro")) {
                return q; // base en metros
            } else if (m.equals("Centímetro")) {
                return q / 100.0; // 100 cm = 1 m
            } else {
                return q; // Unidad base
            }
        });

        return d.showAndWait().orElse(null);
    }

    // Alertas que no se quedan pegadas (se auto-remueven)
    private void flash(VBox container, AlertBanner banner) {
        container.getChildren().removeIf(n -> n instanceof AlertBanner);
        container.getChildren().add(0, banner);
        PauseTransition t = new PauseTransition(Duration.seconds(2.5));
        t.setOnFinished(ev -> container.getChildren().remove(banner));
        t.play();
    }
}
