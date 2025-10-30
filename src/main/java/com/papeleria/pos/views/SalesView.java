package com.papeleria.pos.views;

import com.microsoft.schemas.compatibility.AlternateContentDocument.AlternateContent.Choice;
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
        cCant.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    return;
                }
                SaleItem it = getTableView().getItems().get(getIndex());
                com.papeleria.pos.models.Product p = inventory.findBySku(it.getSku()).orElse(null);
                if (p == null) {
                    setText(String.format("%.3f", v.doubleValue()));
                    return;
                }

                String u = (p.getUnidad() == null ? "" : p.getUnidad().trim().toLowerCase());
                double contenido = Math.max(0.0, p.getContenido());
                double qProd = it.getCantidadBase();

                final double EPS = 1e-9;
                String txt;
                if ((u.equals("paquete") || u.equals("caja")) && contenido > 0) {
                    double piezas = qProd * contenido; // piezas/hojas
                    long entero = Math.round(piezas);
                    if (Math.abs(piezas - entero) < EPS) {
                        // heurística: si el nombre contiene "hoja" usar "hoja(s)"
                        String name = p.getNombre() == null ? "" : p.getNombre().toLowerCase();
                        String menor = name.contains("hoja") ? "hoja" : "pieza";
                        txt = entero + " " + (entero == 1 ? menor : menor + "s");
                    } else {
                        txt = String.format("%.3f %s", piezas, "pzas");
                    }
                } else if (u.equals("rollo") && contenido > 0) {
                    double metros = qProd * contenido;
                    if (metros >= 1)
                        txt = String.format("%.2f m", metros);
                    else
                        txt = String.format("%.0f cm", metros * 100.0);
                } else if (u.equals("m") || u.equals("metro") || u.equals("metros")) {
                    txt = String.format("%.2f m", qProd);
                } else {
                    // unidad suelta del producto (paquete/caja/rollo/pieza)
                    long entero = Math.round(qProd);
                    String label = switch (u) {
                        case "paquete" -> "paquete";
                        case "caja" -> "caja";
                        case "rollo" -> "rollo";
                        default -> "pieza";
                    };
                    txt = entero + " " + (entero == 1 ? label : label + "s");
                }
                setText(txt);
            }
        });

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
                Choice c = promptVenta(p); // devuelve qtyInProductUnit (fracción) y unitPricePerProductUnit
                if (c == null)
                    return;
                if (!sales.validarStock(p.getSku(), c.qtyInProductUnit)) {
                    flash((VBox) getLeft(), AlertBanner.warn("Stock insuficiente"));
                    return;
                }
                // usa SIEMPRE el precio del producto (por SU unidad)
                SaleItem it = new SaleItem(p.getSku(), p.getNombre(), c.qtyInProductUnit, p.getPrecio());
                cart.add(it);
                recalcTotal();
            });
            ;

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox bottom = new HBox(8, price, spacer, add);

            card.getChildren().addAll(name, code, stock, bottom);
            grid.getChildren().add(card);
        }
    }

    private void addToCartWithPrice(Product prod, double qtyInProductUnit, double unitPrice) {
        SaleItem it = new SaleItem(prod.getSku(), prod.getNombre(), qtyInProductUnit, unitPrice);
        cart.add(it);
        recalcTotal();
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

        double raw = cart.stream().mapToDouble(SaleItem::getSubtotal).sum();
        double totalCobrar = roundMex(raw); // 👈 redondeo mexicano

        double ef = parseDouble(
                ((TextField) ((HBox) ((VBox) getRight()).getChildren().get(4)).getChildren().get(1)).getText(), 0.0);

        if (ef < totalCobrar) {
            flash((VBox) getLeft(), AlertBanner.warn("Efectivo insuficiente (Total: " + money(totalCobrar) + ")"));
            return;
        }

        double cambio = ef - totalCobrar;
        double cambioRed = roundMex(cambio); // 👈 cambio redondeado a $0.50

        // Guardar venta (total redondeado). Sale recalcula cambio internamente, pero la
        // UI muestra el redondeado.
        Sale s = new Sale(java.util.UUID.randomUUID().toString().substring(0, 8));
        for (SaleItem it : cart)
            s.agregarItem(it);
        s.setEfectivo(ef);
        s.setTotal(totalCobrar); // 👈 total a cobrar en ticket/registro

        sales.cobrarYGuardar(s);

        cart.clear();
        recalcTotal();
        ((TextField) ((HBox) ((VBox) getRight()).getChildren().get(4)).getChildren().get(1)).clear();

        flash((VBox) getLeft(), AlertBanner.success(
                "Venta registrada. Total " + money(totalCobrar) + " | Cambio " + money(cambioRed) + " (redondeado)"));
    }

    private void recalcTotal() {
        double raw = cart.stream().mapToDouble(SaleItem::getSubtotal).sum();
        double charge = roundMex(raw);
        totalLbl.setText(String.format("$%,.2f", charge));
    }

    // redondeo a múltiplos de 0.50; si hay monto >0 y queda <0.50 => 0.50
    private double roundMex(double value) {
        if (value <= 0)
            return 0.0;
        double half = Math.round(value * 2.0) / 2.0; // múltiplo de 0.50
        if (half < 0.50)
            half = 0.50;
        return half;
    }

    private String money(double v) {
        return String.format("$%,.2f", v);
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
    // Elección de presentación y cantidad. Devuelve cantidad en UNIDAD DEL PRODUCTO
    // y precio unitario efectivo.
    private static class Choice {
        final double qtyInProductUnit;
        final double unitPrice;

        Choice(double q, double p) {
            this.qtyInProductUnit = q;
            this.unitPrice = p;
        }
    }

    private Choice promptVenta(Product p) {
        Dialog<Choice> d = new Dialog<>();
        d.setTitle("Vender — " + p.getNombre());
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Unidad declarada del producto (la del inventario)
        String uProd = (p.getUnidad() == null ? "" : p.getUnidad().trim().toLowerCase());

        // Presentaciones visibles "limpias"
        ChoiceBox<String> present = new ChoiceBox<>();
        // Opción 1: la unidad del producto tal cual (siempre)
        String labelUnidadProd = switch (uProd) {
            case "paquete" -> "Paquete";
            case "caja" -> "Caja";
            case "rollo" -> "Rollo";
            case "m", "metro", "metros" -> "Metro";
            default -> "Unidad";
        };
        present.getItems().add(labelUnidadProd);

        // Opción 2+: si hay contenido,-ofrecer presentaciones menores
        double contenido = Math.max(0.0, p.getContenido());
        if (contenido > 0) {
            // Si el producto es empaquetado/encajado (paquete/caja) → ofrecer "Pieza/Hoja"
            if (uProd.equals("paquete") || uProd.equals("caja")) {
                // heurística: si el nombre contiene "hoja" usamos "Hoja"; si no, "Pieza"
                String lowerName = (p.getNombre() == null ? "" : p.getNombre().toLowerCase());
                String labelUnidadMenor = lowerName.contains("hoja") ? "Hoja" : "Pieza";
                present.getItems().add(labelUnidadMenor);
            }
            // Si el producto es un "rollo" con N metros → ofrecer Metro y Centímetro
            if (uProd.equals("rollo")) {
                if (!present.getItems().contains("Metro"))
                    present.getItems().add("Metro");
                present.getItems().add("Centímetro");
            }
        }
        present.getSelectionModel().selectFirst();

        Spinner<Double> cantidad = new Spinner<>(0.01, 1_000_000.0, 1.0, 1.0);
        cantidad.setEditable(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Presentación"), present);
        grid.addRow(1, new Label("Cantidad"), cantidad);
        d.getDialogPane().setContent(grid);

        d.setResultConverter(bt -> {
            if (bt != ButtonType.OK)
                return null;
            double q = cantidad.getValue();
            if (q <= 0)
                return null;

            String sel = present.getValue();
            double qtyInProductUnit; // SIEMPRE en la unidad del producto (stock y validarStock usan esta)
            double unitPrice; // precio por la unidad EFECTIVA que se cobra en esta línea

            // Precio base declarado en el producto es por la unidad del producto
            // dentro de d.setResultConverter(...)
            double pricePerProductUnit = p.getPrecio();
            if (sel.equals("Hoja") || sel.equals("Pieza")) {
                if (contenido <= 0)
                    return null;
                qtyInProductUnit = q / contenido; // fracción del paquete/caja
                unitPrice = pricePerProductUnit; // 👈 precio por paquete/caja (NO dividir)
            } else if (sel.equals("Metro") && uProd.equals("rollo")) {
                if (contenido <= 0)
                    return null;
                qtyInProductUnit = q / contenido; // fracción del rollo
                unitPrice = pricePerProductUnit; // 👈 precio por rollo completo
            } else if (sel.equals("Centímetro") && uProd.equals("rollo")) {
                if (contenido <= 0)
                    return null;
                qtyInProductUnit = (q / 100.0) / contenido;
                unitPrice = pricePerProductUnit; // 👈 igual
            } else {
                // misma unidad del producto (Paquete/Caja/Rollo/Metro/Unidad)
                qtyInProductUnit = q;
                unitPrice = pricePerProductUnit;
            }
            return new Choice(qtyInProductUnit, unitPrice);

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
