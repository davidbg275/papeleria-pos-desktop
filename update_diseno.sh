#!/usr/bin/env bash
set -euo pipefail
cd "${1:-$HOME/Documentos/papeleria-pos-desktop}"

echo ">> Actualizando estilos y vistas (Sales/Inventory/Main)..."

# ========== CSS nuevo (dark sobrio con cards, badges y sidebar) ==========
cat <<'EOF' > src/main/resources/css/app.css
/* ======= Tema oscuro modernizado ======= */
:root {
  --bg: #0B1221;
  --panel: #0E162B;
  --card: #0F172A;
  --muted: #9FB2CC;
  --txt: #E6EAF2;
  --primary: #2563EB;
  --success: #10B981;
  --danger: #DC2626;
  --warn: #F59E0B;
  --info: #0EA5E9;
  --chip: #111B33;
  --divider: rgba(255,255,255,0.06);
}

.root {
  -fx-background-color: var(--bg);
  -fx-font-family: "Inter", "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  -fx-text-fill: var(--txt);
}

/* Sidebar */
.sidebar {
  -fx-background-color: #081126;
  -fx-padding: 16;
  -fx-spacing: 12;
  -fx-pref-width: 76;
}
.sidebar .nav-btn {
  -fx-background-color: transparent;
  -fx-text-fill: var(--muted);
  -fx-padding: 10 12;
  -fx-background-radius: 12;
  -fx-font-weight: 700;
  -fx-alignment: CENTER;
}
.sidebar .nav-btn.active,
.sidebar .nav-btn:hover {
  -fx-background-color: rgba(37, 99, 235, 0.16);
  -fx-text-fill: var(--txt);
}

/* Cards */
.card {
  -fx-background-color: var(--card);
  -fx-background-radius: 14;
  -fx-padding: 16;
  -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 24, 0.2, 0, 8);
}
.panel {
  -fx-background-color: var(--panel);
  -fx-background-radius: 14;
  -fx-padding: 16;
}

/* Títulos y subtítulos */
.h1 { -fx-font-size: 24px; -fx-font-weight: 900; }
.subtle { -fx-text-fill: var(--muted); -fx-font-size: 12.5px; }

/* Botones */
.button {
  -fx-background-radius: 10;
  -fx-background-color: #1f2937;
  -fx-text-fill: var(--txt);
  -fx-padding: 8 14;
  -fx-font-weight: 700;
}
.button.primary { -fx-background-color: var(--primary); }
.button.success { -fx-background-color: var(--success); }
.button.danger  { -fx-background-color: var(--danger); }
.button.warn    { -fx-background-color: var(--warn); }
.button.info    { -fx-background-color: var(--info); }

/* Inputs */
.text-field, .combo-box-base, .spinner {
  -fx-background-color: #0c1630, #0c1630;
  -fx-background-insets: 0, 0;
  -fx-text-fill: var(--txt);
  -fx-prompt-text-fill: derive(var(--muted), 10%);
  -fx-background-radius: 10;
  -fx-padding: 9 12;
}

/* Badges */
.badge {
  -fx-background-color: rgba(255,255,255,0.08);
  -fx-background-radius: 10;
  -fx-padding: 4 8;
  -fx-font-weight: 800;
  -fx-font-size: 12px;
}
.badge.ok    { -fx-background-color: rgba(16,185,129,.18); -fx-text-fill: #a7f3d0; }
.badge.low   { -fx-background-color: rgba(245,158,11,.18); -fx-text-fill: #fde68a; }
.badge.none  { -fx-background-color: rgba(220,38,38,.20); -fx-text-fill: #fecaca; }

/* Tabla */
.table-view {
  -fx-background-color: transparent;
  -fx-control-inner-background: transparent;
}
.table-view .column-header-background { -fx-background-color: #0c1a33; -fx-background-radius: 12 12 0 0; }
.table-view .column-header, .table-view .filler { -fx-background-color: transparent; -fx-size: 42px; }
.table-row-cell:odd { -fx-background-color: rgba(255,255,255,0.02); }
.table-row-cell:selected { -fx-background-color: derive(var(--primary), 10%); }
.table-cell { -fx-text-fill: var(--txt); -fx-padding: 10 12; }

/* Grid de Productos (Sales) */
.product-card {
  -fx-background-color: var(--card);
  -fx-background-radius: 14;
  -fx-padding: 12;
  -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 18, 0.15, 0, 6);
}
.product-card .name { -fx-font-weight: 800; -fx-font-size: 14.5px; }
.product-card .price { -fx-text-fill: #9ef0b5; -fx-font-weight: 900; }
.product-add { -fx-background-color: var(--primary); -fx-background-radius: 10; -fx-padding: 6 10; -fx-font-weight: 800; }

/* Carrito */
.cart {
  -fx-background-color: var(--panel);
  -fx-background-radius: 14;
  -fx-padding: 16;
}
.cart .line { -fx-border-color: var(--divider); -fx-border-width: 0 0 1 0; -fx-padding: 8 0; }

/* Alertas banners */
.alert { -fx-background-radius: 12; -fx-padding: 10 14; -fx-font-weight: 700; }
.alert-info    { -fx-background-color: derive(var(--info), 10%); }
.alert-warn    { -fx-background-color: derive(var(--warn), 10%); }
.alert-danger  { -fx-background-color: derive(var(--danger), 10%); }
.alert-success { -fx-background-color: derive(var(--success), 10%); }
EOF

# ========== MainView con sidebar de iconos ==========
cat <<'EOF' > src/main/java/com/papeleria/pos/views/MainView.java
package com.papeleria.pos.views;

import com.papeleria.pos.services.*;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public class MainView extends BorderPane {
    private final SessionService session;
    private final InventoryService inventoryService;
    private final SalesService salesService;
    private final ProductionService productionService;
    private final EventBus bus;

    private final VBox sidebar = new VBox(12);
    private final StackPane center = new StackPane();

    private final InventoryView inventoryView;
    private final SalesView salesView;
    private final ProductionView productionView;

    public MainView(SessionService session, InventoryService inventoryService, SalesService salesService,
                    ProductionService productionService, EventBus bus){
        this.session = session;
        this.inventoryService = inventoryService;
        this.salesService = salesService;
        this.productionService = productionService;
        this.bus = bus;

        setPadding(new Insets(14));

        sidebar.getStyleClass().add("sidebar");
        Button bSales  = mkNav("🛒");
        Button bInv    = mkNav("📦");
        Button bProd   = mkNav("🛠️");
        Button bReport = mkNav("📊");
        Button bLogout = mkNav("⏻");
        bSales.getStyleClass().add("active");
        bLogout.setOnAction(e -> { session.logout(); System.exit(0); });

        sidebar.getChildren().addAll(bSales, bInv, bProd, bReport, bLogout);
        setLeft(sidebar);

        inventoryView = new InventoryView(session, inventoryService, bus);
        salesView = new SalesView(session, salesService, inventoryService, bus);
        productionView = new ProductionView(session, productionService, inventoryService, bus);

        setContent(salesView);

        bSales.setOnAction(e -> { activate(bSales, bInv, bProd, bReport); setContent(salesView); });
        bInv.setOnAction(e -> { activate(bInv, bSales, bProd, bReport); setContent(inventoryView); });
        bProd.setOnAction(e -> { activate(bProd, bSales, bInv, bReport); setContent(productionView); });
        bReport.setOnAction(e -> { activate(bReport, bSales, bInv, bProd); setContent(inventoryView); }); // placeholder
    }

    private Button mkNav(String icon){
        Button b = new Button(icon);
        b.getStyleClass().add("nav-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setPrefHeight(48);
        return b;
    }

    private void activate(Button on, Button... off){
        on.getStyleClass().add("active");
        for (Button x : off) x.getStyleClass().remove("active");
    }

    private void setContent(Node n){ center.getChildren().setAll(n); setCenter(center); }
}
EOF

# ========== SalesView: grilla de productos + carrito a la derecha ==========
cat <<'EOF' > src/main/java/com/papeleria/pos/views/SalesView.java
package com.papeleria.pos.views;

import com.papeleria.pos.components.AlertBanner;
import com.papeleria.pos.models.Product;
import com.papeleria.pos.models.Sale;
import com.papeleria.pos.models.SaleItem;
import com.papeleria.pos.services.EventBus;
import com.papeleria.pos.services.InventoryService;
import com.papeleria.pos.services.SalesService;
import com.papeleria.pos.services.SessionService;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SalesView extends BorderPane {

    private final SessionService session;
    private final SalesService sales;
    private final InventoryService inventory;
    private final EventBus bus;

    private final TextField filtro = new TextField();
    private final FlowPane grid = new FlowPane(12,12);

    private final TableView<SaleItem> cartTable = new TableView<>();
    private final ObservableList<SaleItem> cart = FXCollections.observableArrayList();
    private final Label totalLbl = new Label("$0.00");
    private final TextField efectivo = new TextField();

    public SalesView(SessionService session, SalesService sales, InventoryService inventory, EventBus bus){
        this.session = session; this.sales = sales; this.inventory = inventory; this.bus = bus;

        // ----- Izquierda: productos -----
        VBox left = new VBox(12);
        Label title = new Label("Punto de Venta"); title.getStyleClass().add("h1");
        Label subtitle = new Label("Busca productos y gestiona tu carrito"); subtitle.getStyleClass().add("subtle");
        filtro.setPromptText("Buscar por código o nombre del producto...");
        filtro.textProperty().addListener((o,old,v) -> renderGrid());
        grid.setPrefWrapLength(760);

        left.getChildren().addAll(title, subtitle, filtro, new ScrollPane(grid){{
            setFitToWidth(true); setPrefViewportHeight(520); setStyle("-fx-background-color: transparent;");
        }});
        left.setPadding(new Insets(12));

        // ----- Derecha: carrito -----
        VBox right = new VBox(10);
        right.getStyleClass().add("cart");
        Label cartTitle = new Label("Carrito de Compras"); cartTitle.getStyleClass().add("h1");

        cartTable.setItems(cart);
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<SaleItem,String> cProd = new TableColumn<>("Producto");
        cProd.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombre()));
        TableColumn<SaleItem,Number> cCant = new TableColumn<>("Cant.");
        cCant.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getCantidadBase()));
        TableColumn<SaleItem,Number> cPrecio = new TableColumn<>("Precio");
        cPrecio.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getPrecioUnitario()));
        TableColumn<SaleItem,Number> cSub = new TableColumn<>("Subtotal");
        cSub.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getSubtotal()));
        cartTable.getColumns().setAll(cProd, cCant, cPrecio, cSub);
        cartTable.setPrefHeight(380);

        HBox totalBox = new HBox(8, new Label("Total:"), totalLbl);
        totalLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: 900;");
        totalBox.setAlignment(Pos.CENTER_RIGHT);

        efectivo.setPromptText("Efectivo recibido");

        HBox actions = new HBox(8);
        Button cobrar = new Button("💵 Pagar en Efectivo"); cobrar.getStyleClass().add("success");
        Button limpiar = new Button("🧹 Limpiar Carrito");
        actions.getChildren().addAll(cobrar, limpiar);

        right.getChildren().addAll(cartTitle, cartTable, new Separator(), totalBox, new HBox(8, new Label("Efectivo:"), efectivo), actions);

        setLeft(left); setRight(right); setPadding(new Insets(10));
        BorderPane.setMargin(right, new Insets(12,0,12,12));

        // Eventos
        renderGrid();
        cobrar.setOnAction(e -> cobrarAccion());
        limpiar.setOnAction(e -> { cart.clear(); recalcTotal(); });
    }

    private void renderGrid(){
        grid.getChildren().clear();
        String q = filtro.getText() == null ? "" : filtro.getText().trim().toLowerCase();
        List<Product> productos = inventory.list();
        for (Product p : productos){
            if (!q.isEmpty()){
                String hay = (p.getSku()+" "+p.getNombre()).toLowerCase();
                if (!hay.contains(q)) continue;
            }
            VBox card = new VBox(6); card.getStyleClass().add("product-card");
            Label name = new Label(p.getNombre()); name.getStyleClass().add("name");
            Label code = new Label("Código: " + p.getSku()); code.getStyleClass().add("subtle");
            Label price = new Label(String.format("$%.2f", p.getPrecio())); price.getStyleClass().add("price");
            Button add = new Button("+"); add.getStyleClass().addAll("product-add","primary");
            add.setOnAction(ev -> addToCart(p, 1.0)); // botón rápido +1 unidad base
            HBox bottom = new HBox(8, price, new Pane(), add); HBox.setHgrow(bottom.getChildren().get(1), Priority.ALWAYS);
            card.getChildren().addAll(name, code, bottom);
            card.setPrefWidth(220);
            grid.getChildren().add(card);
        }
    }

    private void addToCart(Product prod, double cantBase){
        if (!sales.validarStock(prod.getSku(), cantBase)){
            ((VBox)getLeft()).getChildren().add(0, AlertBanner.danger("Stock insuficiente"));
            return;
        }
        SaleItem it = new SaleItem(prod.getSku(), prod.getNombre(), cantBase, prod.getPrecio());
        cart.add(it);
        recalcTotal();
    }

    private void cobrarAccion(){
        if (cart.isEmpty()){
            ((VBox)getLeft()).getChildren().add(0, AlertBanner.warn("Carrito vacío"));
            return;
        }
        double total = cart.stream().mapToDouble(SaleItem::getSubtotal).sum();
        double ef = parseDouble(efectivo.getText(), 0.0);
        if (ef < total){
            ((VBox)getLeft()).getChildren().add(0, AlertBanner.warn("Efectivo insuficiente"));
            return;
        }
        Sale s = new Sale(UUID.randomUUID().toString().substring(0,8));
        for (SaleItem it : cart) s.agregarItem(it);
        s.setEfectivo(ef);
        sales.cobrarYGuardar(s);
        cart.clear(); recalcTotal(); efectivo.clear();
        ((VBox)getLeft()).getChildren().add(0, AlertBanner.success("Venta registrada. Ticket generado."));
    }

    private void recalcTotal(){
        double total = cart.stream().mapToDouble(SaleItem::getSubtotal).sum();
        totalLbl.setText(String.format("$%.2f", total));
    }

    private double parseDouble(String s, double def){ try { return Double.parseDouble(s.replace(",", ".").trim()); } catch(Exception e){ return def; } }
}
EOF

# ========== InventoryView con KPIs/acciones estilo tablero ==========
cat <<'EOF' > src/main/java/com/papeleria/pos/views/InventoryView.java
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

public class InventoryView extends VBox {
    private final SessionService session;
    private final InventoryService service;
    private final EventBus bus;

    private final ObservableList<Product> backing = FXCollections.observableArrayList();
    private final TableView<Product> table = new TableView<>(backing);
    private final TextField search = new TextField();

    public InventoryView(SessionService session, InventoryService service, EventBus bus){
        this.session = session; this.service = service; this.bus = bus;
        setSpacing(12); setPadding(new Insets(12));

        Label title = new Label("Inventario"); title.getStyleClass().add("h1");
        Label sub = new Label("Gestiona el stock y productos de tu papelería"); sub.getStyleClass().add("subtle");

        // KPIs
        HBox kpis = new HBox(12);
        kpis.getChildren().addAll(kpi("Total Productos", String.valueOf(service.list().size()), "📦"),
                                   kpi("Stock Bajo", String.valueOf(contarBajo()), "⚠️"),
                                   kpi("Sin Stock", String.valueOf(contarCero()), "⛔"));
        // Buscador + acciones
        search.setPromptText("Buscar productos por código o nombre...");
        Button btnAddEdit = new Button("Agregar/Editar");
        Button btnDelete = new Button("Eliminar seleccionado");
        Button btnClearAll = new Button("Eliminar TODO"); btnClearAll.getStyleClass().add("danger");
        Button btnImport = new Button("Cargar Excel (.xlsx)"); btnImport.getStyleClass().add("info");
        HBox actions = new HBox(8, search, new Pane(), btnAddEdit, btnDelete, btnClearAll, btnImport);
        HBox.setHgrow(search, Priority.ALWAYS); HBox.setHgrow(actions.getChildren().get(1), Priority.ALWAYS);

        setupTable();
        getChildren().addAll(title, sub, kpis, actions, table);

        // Eventos
        search.textProperty().addListener((o,old,v) -> refresh());
        bus.subscribe(EventBus.Topic.INVENTORY_CHANGED, ev -> { refresh(); actualizarKpis(kpis); });

        // Acciones
        btnAddEdit.setOnAction(ev -> showEditDialog(table.getSelectionModel().getSelectedItem()));
        btnDelete.setOnAction(ev -> {
            Product p = table.getSelectionModel().getSelectedItem();
            if (p != null) service.removeBySku(p.getSku());
        });
        btnClearAll.setOnAction(ev -> {
            if (!session.isAdmin()) { getChildren().add(0, AlertBanner.danger("Solo ADMIN puede eliminar TODO")); return; }
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Confirmación");
            d.setHeaderText("Confirmar eliminación total");
            d.setContentText("Escribe la contraseña de admin para confirmar:");
            Optional<String> ans = d.showAndWait();
            if (ans.isPresent() && ans.get().equals("admin")) { service.clearAll(); getChildren().add(0, AlertBanner.success("Inventario eliminado")); }
            else { getChildren().add(0, AlertBanner.warn("Operación cancelada")); }
        });
        btnImport.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel .xlsx", "*.xlsx"));
            File f = fc.showOpenDialog(getScene().getWindow());
            if (f != null){
                try {
                    int n = service.importFromExcel(Path.of(f.getAbsolutePath()));
                    getChildren().add(0, AlertBanner.success("Importados: " + n));
                } catch (Exception ex){
                    getChildren().add(0, AlertBanner.danger("Error importando: " + ex.getMessage()));
                }
            }
        });

        refresh();
    }

    private HBox kpi(String title, String value, String icon){
        VBox v = new VBox(4);
        Label t = new Label(title); t.getStyleClass().add("subtle");
        Label val = new Label(value); val.setStyle("-fx-font-size: 22px; -fx-font-weight: 900;");
        HBox h = new HBox(8, new Label(icon), new VBox(2, t, val));
        HBox wrap = new HBox(h); wrap.getStyleClass().add("card"); wrap.setPadding(new Insets(12)); wrap.setPrefWidth(210);
        return wrap;
    }
    private void actualizarKpis(HBox kpis){
        ((Label)((VBox)((HBox)((HBox)kpis.getChildren().get(0)).getChildren().get(0)).getChildren().get(1)).getChildren().get(1))
                .setText(String.valueOf(service.list().size()));
        ((Label)((VBox)((HBox)((HBox)kpis.getChildren().get(1)).getChildren().get(0)).getChildren().get(1)).getChildren().get(1))
                .setText(String.valueOf(contarBajo()));
        ((Label)((VBox)((HBox)((HBox)kpis.getChildren().get(2)).getChildren().get(0)).getChildren().get(1)).getChildren().get(1))
                .setText(String.valueOf(contarCero()));
    }
    private long contarBajo(){ return service.list().stream().filter(p -> p.getStock() > 0 && p.getStock() <= 5).count(); }
    private long contarCero(){ return service.list().stream().filter(p -> p.getStock() <= 0).count(); }

    private void setupTable(){
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Product, String> cCodigo = new TableColumn<>("Código");
        cCodigo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSku()));
        cCodigo.setMinWidth(120);

        TableColumn<Product, String> cNombre = new TableColumn<>("Producto");
        cNombre.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombre()));
        cNombre.setMinWidth(240);

        TableColumn<Product, String> cCat = new TableColumn<>("Categoría");
        cCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategoria()));
        cCat.setMinWidth(140);

        TableColumn<Product, Number> cPrecio = new TableColumn<>("Precio");
        cPrecio.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getPrecio()));
        cPrecio.setMinWidth(120);

        TableColumn<Product, Number> cStock = new TableColumn<>("Stock");
        cStock.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getStock()));
        cStock.setMinWidth(120);

        table.getColumns().setAll(cCodigo, cNombre, cCat, cStock, cPrecio);
        table.setPrefHeight(520);
    }

    private void showEditDialog(Product editable){
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle(editable == null ? "Agregar producto" : "Editar producto");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8); grid.setPadding(new Insets(10));
        TextField sku = new TextField(); TextField nombre = new TextField();
        TextField categoria = new TextField(); TextField unidad = new TextField();
        TextField contenido = new TextField(); TextField precio = new TextField(); TextField stock = new TextField();
        sku.setPromptText("Código (sku)"); nombre.setPromptText("Nombre"); categoria.setPromptText("Categoría");
        unidad.setPromptText("Unidad (pza, hoja, m...)"); contenido.setPromptText("Contenido (0 si no aplica)");
        precio.setPromptText("Precio unidad base"); stock.setPromptText("Stock unidad base");

        if (editable != null){
            sku.setText(editable.getSku()); sku.setDisable(true);
            nombre.setText(editable.getNombre()); categoria.setText(editable.getCategoria());
            unidad.setText(editable.getUnidad()); contenido.setText(String.valueOf(editable.getContenido()));
            precio.setText(String.valueOf(editable.getPrecio())); stock.setText(String.valueOf(editable.getStock()));
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
            if (bt == ButtonType.OK){
                try {
                    String _sku = sku.getText().trim();
                    String _nombre = nombre.getText().trim();
                    String _cat = categoria.getText().trim();
                    String _uni = unidad.getText().trim();
                    double _cont = Double.parseDouble(contenido.getText().trim());
                    double _pre = Double.parseDouble(precio.getText().trim());
                    double _stk = Double.parseDouble(stock.getText().trim());
                    if (_sku.isEmpty() || _nombre.isEmpty()) return null;
                    return new Product(_sku, _nombre, _cat, _uni, _cont, _pre, _stk);
                } catch (Exception ex) { return null; }
            }
            return null;
        });
        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(service::upsert);
    }

    private void refresh(){
        String q = search.getText();
        List<Product> src = service.search(q);
        backing.setAll(src);
    }
}
EOF

echo ">> Listo. Recompilando…"
mvn -q clean package

echo
echo "========================================"
echo "Diseño actualizado. Ejecuta:"
echo "mvn -q javafx:run"
echo "========================================"
EOF

chmod +x update_diseno.sh
echo "Parche guardado como ./update_diseno.sh. Para aplicarlo:"
echo "  cd ~/Documentos/papeleria-pos-desktop && ./update_diseno.sh"

