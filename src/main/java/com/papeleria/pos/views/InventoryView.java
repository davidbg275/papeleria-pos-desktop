package com.papeleria.pos.views;

import com.papeleria.pos.components.AlertBanner;
import com.papeleria.pos.models.Product;
import com.papeleria.pos.services.EventBus;
import com.papeleria.pos.services.InventoryService;
import com.papeleria.pos.services.SessionService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class InventoryView extends VBox {
    private final SessionService session;
    private final InventoryService service;
    private final EventBus bus;

    private final ObservableList<Product> backing = FXCollections.observableArrayList();
    private final TableView<Product> table = new TableView<>(backing);
    private final TextField search = new TextField();

    public InventoryView(SessionService session, InventoryService service, EventBus bus) {
        this.session = session;
        this.service = service;
        this.bus = bus;
        setSpacing(12);
        setPadding(new Insets(10));

        Label title = new Label("Inventario");
        title.getStyleClass().add("h1");
        Label sub = new Label("Gestiona el stock y productos de tu papelería");
        sub.getStyleClass().add("subtle");

        // KPIs
        HBox kpis = new HBox(12, kpi("Total Productos", String.valueOf(service.list().size()), "📦"),
                kpi("Stock Bajo", String.valueOf(contarBajo()), "⚠️"),
                kpi("Sin Stock", String.valueOf(contarCero()), "⛔"),
                kpi("Valor Total", "$", "💲"));
        // Buscador + acciones
        search.setPromptText("Buscar por nombre o código...");
        Button btnAddEdit = new Button("Agregar/Editar");
        btnAddEdit.getStyleClass().add("primary");
        Button btnDelete = new Button("Eliminar seleccionado");
        btnDelete.getStyleClass().add("ghost");
        Button btnClearAll = new Button("Eliminar TODO");
        btnClearAll.getStyleClass().add("danger");
        Button btnImport = new Button("Cargar Productos (.xlsx)");
        btnImport.getStyleClass().add("info");

        HBox actions = new HBox(8, search, new Region(), btnAddEdit, btnDelete, btnClearAll, btnImport);
        HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);
        HBox.setHgrow(search, Priority.ALWAYS);

        setupTable();
        getChildren().addAll(title, sub, kpis, actions, table);

        // Eventos
        search.textProperty().addListener((o, old, v) -> refresh());
        bus.subscribe(EventBus.Topic.INVENTORY_CHANGED, ev -> {
            refresh();
            actualizarKpis(kpis);
        });

        // Acciones
        btnAddEdit.setOnAction(ev -> {
            if (!session.isAdmin()) {
                getChildren().add(0, AlertBanner.warn("Solo ADMIN puede agregar/editar"));
                return;
            }
            showEditDialog(table.getSelectionModel().getSelectedItem());
        });

        btnDelete.setOnAction(ev -> {
            if (!session.isAdmin()) {
                getChildren().add(0, AlertBanner.warn("Solo ADMIN puede eliminar"));
                return;
            }
            Product p = table.getSelectionModel().getSelectedItem();
            if (p != null)
                service.removeBySku(p.getSku());
        });
        btnClearAll.setOnAction(ev -> {
            if (!session.isAdmin()) {
                if (!session.isAdmin()) {
                    flash(AlertBanner.danger("Solo ADMIN puede eliminar TODO"));
                    return;
                }

                return;
            }
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Confirmación");
            d.setHeaderText("Eliminar TODO el inventario");
            d.setContentText("Escribe: ELIMINAR");
            Optional<String> ans = d.showAndWait();
            if (ans.isPresent() && "ELIMINAR".equalsIgnoreCase(ans.get().trim())) {
                service.clearAll();
                refresh();
                if (!session.isAdmin()) {
                    flash(AlertBanner.danger("Solo ADMIN puede eliminar TODO"));
                    return;
                }

            } else {
                if (!session.isAdmin()) {
                    flash(AlertBanner.danger("Solo ADMIN puede eliminar TODO"));
                    return;
                }

            }
        });

        btnImport.setOnAction(ev -> {
            if (!session.isAdmin()) {
                getChildren().add(0, AlertBanner.warn("Solo ADMIN puede importar"));
                return;
            }
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel .xlsx", "*.xlsx"));
            File f = fc.showOpenDialog(getScene().getWindow());
            if (f != null) {
                try {
                    int n = service.importFromExcel(Path.of(f.getAbsolutePath()));
                    getChildren().add(0, AlertBanner.success("Importados: " + n));
                } catch (Exception ex) {
                    getChildren().add(0, AlertBanner.danger("Error importando: " + ex.getMessage()));
                }
            }
        });

        refresh();
    }

    private HBox kpi(String title, String value, String icon) {
        Label t = new Label(title);
        t.getStyleClass().add("subtle");
        Label v = new Label(value);
        v.setStyle("-fx-font-size: 22px; -fx-font-weight: 900;");
        HBox h = new HBox(10, new Label(icon), new VBox(2, t, v));
        HBox card = new HBox(h);
        card.getStyleClass().add("kpi");
        card.setPadding(new Insets(14));
        card.setPrefWidth(220);
        return card;
    }

    private void actualizarKpis(HBox kpis) {
        ((Label) ((VBox) ((HBox) ((HBox) kpis.getChildren().get(0)).getChildren().get(0)).getChildren().get(1))
                .getChildren().get(1))
                .setText(String.valueOf(service.list().size()));
        ((Label) ((VBox) ((HBox) ((HBox) kpis.getChildren().get(1)).getChildren().get(0)).getChildren().get(1))
                .getChildren().get(1))
                .setText(String.valueOf(contarBajo()));
        ((Label) ((VBox) ((HBox) ((HBox) kpis.getChildren().get(2)).getChildren().get(0)).getChildren().get(1))
                .getChildren().get(1))
                .setText(String.valueOf(contarCero()));
    }

    private long contarBajo() {
        return service.list().stream().filter(p -> p.getStock() > 0 && p.getStock() <= 5).count();
    }

    private long contarCero() {
        return service.list().stream().filter(p -> p.getStock() <= 0).count();
    }

    private void setupTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Product, String> cCodigo = new TableColumn<>("Código");
        cCodigo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSku()));
        cCodigo.setMinWidth(100);

        TableColumn<Product, String> cNombre = new TableColumn<>("Producto");
        cNombre.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombre()));
        cNombre.setMinWidth(260);

        TableColumn<Product, String> cCat = new TableColumn<>("Categoría");
        cCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategoria()));
        cCat.setMinWidth(140);

        // NUEVOS
        TableColumn<Product, String> cUnidad = new TableColumn<>("Unidad");
        cUnidad.setCellValueFactory(d -> new SimpleStringProperty(unidadPretty(d.getValue())));
        cUnidad.setMinWidth(100);

        TableColumn<Product, String> cContenido = new TableColumn<>("Contenido");
        cContenido.setCellValueFactory(d -> new SimpleStringProperty(contenidoPretty(d.getValue())));
        cContenido.setMinWidth(140);

        TableColumn<Product, String> cPresent = new TableColumn<>("Presentaciones");
        cPresent.setCellValueFactory(d -> new SimpleStringProperty(presentaciones(d.getValue())));
        cPresent.setMinWidth(200);

        // Stock numérico con color
        TableColumn<Product, Number> cStock = new TableColumn<>("Stock");
        cStock.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getStock()));
        cStock.setMinWidth(90);
        cStock.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                double s = v.doubleValue();
                setText(String.format("%.2f", s));
                String color = (s <= 0.0) ? "#dc2626" : (s <= 5.0 ? "#ea580c" : "#16a34a");
                setStyle("-fx-text-fill: " + color + "; -fx-font-weight: 700;");
            }
        });

        // Equivalente en unidad base/pieza/hoja/metro (solo si aplica)
        TableColumn<Product, String> cEquiv = new TableColumn<>("Equiv. base");
        cEquiv.setCellValueFactory(d -> new SimpleStringProperty(equivBase(d.getValue())));
        cEquiv.setMinWidth(140);

        TableColumn<Product, Number> cPrecioUnidad = new TableColumn<>("Precio (unidad)");
        cPrecioUnidad.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getPrecio()));
        cPrecioUnidad.setMinWidth(120);

        TableColumn<Product, Number> cPrecioMenor = new TableColumn<>("Precio (menor)");
        cPrecioMenor.setCellValueFactory(d -> new SimpleDoubleProperty(precioMenor(d.getValue())));
        cPrecioMenor.setMinWidth(120);

        table.getColumns().setAll(
                cCodigo, cNombre, cCat,
                cUnidad, cContenido, cPresent,
                cStock, cEquiv,
                cPrecioUnidad, cPrecioMenor);
        table.setPrefHeight(520);
    }

    private void showEditDialog(Product editable) {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle(editable == null ? "Agregar producto" : "Editar producto");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        TextField sku = new TextField();
        TextField nombre = new TextField();
        TextField categoria = new TextField();
        TextField unidad = new TextField();
        TextField contenido = new TextField();
        TextField precio = new TextField();
        TextField stock = new TextField();
        sku.setPromptText("Código (sku)");
        nombre.setPromptText("Nombre");
        categoria.setPromptText("Categoría");
        unidad.setPromptText("Unidad (pza, hoja, m...)");
        contenido.setPromptText("Contenido (0 si no aplica)");
        precio.setPromptText("Precio unidad base");
        stock.setPromptText("Stock unidad base");

        if (editable != null) {
            sku.setText(editable.getSku());
            sku.setDisable(true);
            nombre.setText(editable.getNombre());
            categoria.setText(editable.getCategoria());
            unidad.setText(editable.getUnidad());
            contenido.setText(String.valueOf(editable.getContenido()));
            precio.setText(String.valueOf(editable.getPrecio()));
            stock.setText(String.valueOf(editable.getStock()));
        }

        grid.addRow(0, new Label("Código"), sku);
        grid.addRow(1, new Label("Nombre"), nombre);
        grid.addRow(2, new Label("Categoría"), categoria);
        grid.addRow(3, new Label("Unidad"), unidad);
        grid.addRow(4, new Label("Contenido"), contenido);
        grid.addRow(5, new Label("Precio"), precio);
        grid.addRow(6, new Label("Stock"), stock);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    String _sku = sku.getText().trim();
                    String _nombre = nombre.getText().trim();
                    String _cat = categoria.getText().trim();
                    String _uni = unidad.getText().trim();
                    double _cont = Double.parseDouble(contenido.getText().trim());
                    double _pre = Double.parseDouble(precio.getText().trim());
                    double _stk = Double.parseDouble(stock.getText().trim());
                    if (_sku.isEmpty() || _nombre.isEmpty())
                        return null;
                    return new Product(_sku, _nombre, _cat, _uni, _cont, _pre, _stk);
                } catch (Exception ex) {
                    return null;
                }
            }
            return null;
        });
        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(service::upsert);
    }

    private void refresh() {
        String q = search.getText();
        List<Product> src = service.search(q);
        backing.setAll(src);
    }

    // Muestra una alerta en el tope y la retira sola
    private void flash(AlertBanner banner) {
        getChildren().removeIf(n -> n instanceof AlertBanner);
        getChildren().add(0, banner);
        PauseTransition t = new PauseTransition(Duration.seconds(2.5));
        t.setOnFinished(e -> getChildren().remove(banner));
        t.play();
    }

    private String unidadPretty(Product p) {
        String u = safe(p.getUnidad());
        return switch (u) {
            case "paquete" -> "Paquete";
            case "caja" -> "Caja";
            case "rollo" -> "Rollo";
            case "m", "metro", "metros" -> "Metro";
            default -> "Unidad";
        };
    }

    private String contenidoPretty(Product p) {
        double c = p.getContenido();
        if (c <= 0)
            return "—";
        String u = safe(p.getUnidad());
        if (u.equals("rollo"))
            return String.format("%.0f m", c); // 10 m por rollo
        // heurística de nombre para "hoja"/"pieza"
        String name = safe(p.getNombre());
        String menor = name.contains("hoja") ? "hojas" : "pzas";
        return String.format("%.0f %s", c, menor);
    }

    /**
     * Lo que verán en ventas: Paquete • Hoja, Caja • Pieza, Rollo • Metro •
     * Centímetro…
     */
    private String presentaciones(Product p) {
        String u = safe(p.getUnidad());
        double c = p.getContenido();
        if (u.equals("paquete")) {
            return c > 0 ? "Paquete • Hoja" : "Paquete";
        } else if (u.equals("caja")) {
            return c > 0 ? "Caja • Pieza" : "Caja";
        } else if (u.equals("rollo")) {
            return c > 0 ? "Rollo • Metro • Centímetro" : "Rollo";
        } else if (u.equals("m") || u.equals("metro") || u.equals("metros")) {
            return "Metro • Centímetro";
        } else {
            return "Unidad";
        }
    }

    /**
     * Equivalente en unidad menor: 28.99 paquetes ≈ 14495 hojas; 24.99 rollos ≈
     * 249.9 m
     */
    private String equivBase(Product p) {
        double c = p.getContenido();
        if (c <= 0)
            return "—";
        String u = safe(p.getUnidad());
        double base = p.getStock() * c;
        if (u.equals("rollo"))
            return String.format("≈ %.2f m", base);
        String name = safe(p.getNombre());
        String menor = name.contains("hoja") ? "hojas" : "pzas";
        return String.format("≈ %.0f %s", base, menor);
    }

    /**
     * Precio por la unidad menor (pieza/hoja/metro/cm) si procede; si no aplica, 0
     * o igual al base.
     */
    private double precioMenor(Product p) {
        double c = p.getContenido();
        String u = safe(p.getUnidad());
        if (c > 0 && (u.equals("paquete") || u.equals("caja") || u.equals("rollo"))) {
            return p.getPrecio() / c; // por hoja/pieza/metro
        }
        if (u.equals("m") || u.equals("metro") || u.equals("metros")) {
            return p.getPrecio(); // ya es por metro
        }
        return 0.0; // no aplica (unidad suelta)
    }

    private String safe(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

}
