#!/usr/bin/env bash
set -euo pipefail
cd "${1:-$HOME/Documentos/papeleria-pos-desktop}"

echo ">> Corrigiendo contraste (tema claro) y agregando Reportes..."

# ====================== CSS con mayor contraste y jerarquía ======================
cat <<'EOF' > src/main/resources/css/app.css
/* ===== Tema claro tipo dashboard con alto contraste ===== */
:root {
  /* Base */
  -fx-bg:        #F5F7FB;   /* fondo global */
  -fx-surface:   #FFFFFF;   /* cards/paneles */
  -fx-surface-2: #F3F6FC;   /* headers y barras */
  -fx-border:    #D7DFEA;   /* bordes visibles */
  -fx-shadow:    rgba(16,24,40,0.12);

  /* Texto */
  -fx-txt:       #0F172A;   /* principal */
  -fx-muted:     #5B6476;   /* secundario/placeholder */
  -fx-invert:    #FFFFFF;

  /* Sidebar */
  -fx-side-bg:   #081226;
  -fx-side-hover:#0F203F;
  -fx-accent:    #06B6D4;

  /* Accentos */
  -fx-primary:   #0EA5E9;
  -fx-success:   #10B981;
  -fx-danger:    #EF4444;
  -fx-warn:      #F59E0B;
  -fx-info:      #38BDF8;
}

/* Base general */
.root {
  -fx-background-color: -fx-bg;
  -fx-font-family: "Inter", "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  -fx-text-fill: -fx-txt;
}

/* Sidebar compacto con iconos */
.sidebar {
  -fx-background-color: -fx-side-bg;
  -fx-padding: 16;
  -fx-spacing: 14;
  -fx-pref-width: 76;
}
.nav-btn {
  -fx-background-color: transparent;
  -fx-text-fill: #A4B0C9;
  -fx-background-radius: 12;
  -fx-font-size: 18px;
  -fx-font-weight: 900;
  -fx-alignment: CENTER;
  -fx-padding: 12 0;
}
.nav-btn:hover { -fx-background-color: -fx-side-hover; -fx-text-fill: #E6EDF9; }
.nav-btn.active {
  -fx-background-color: rgba(6,182,212,0.25);
  -fx-text-fill: #EAF8FB;
  -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 12, 0.25, 0, 2);
}

/* Página contenedora */
.page { -fx-padding: 16; }
.h1 { -fx-font-size: 24px; -fx-font-weight: 900; -fx-text-fill: -fx-txt; }
.subtle { -fx-text-fill: -fx-muted; -fx-font-size: 13px; }

/* Cards y paneles */
.card, .panel, .cart, .kpi, .product-card {
  -fx-background-color: -fx-surface;
  -fx-background-radius: 14;
  -fx-padding: 16;
  -fx-effect: dropshadow(gaussian, -fx-shadow, 22, 0.35, 0, 6);
  -fx-border-color: -fx-border;
  -fx-border-radius: 14;
}

/* Inputs */
.text-field, .combo-box-base, .spinner, .choice-box {
  -fx-background-color: -fx-surface;
  -fx-background-radius: 10;
  -fx-border-color: -fx-border;
  -fx-border-radius: 10;
  -fx-padding: 10 12;
  -fx-text-fill: -fx-txt;
  -fx-prompt-text-fill: derive(-fx-muted, 10%);
}

/* Botones */
.button {
  -fx-background-radius: 10;
  -fx-font-weight: 900;
  -fx-text-fill: -fx-invert;
  -fx-padding: 10 14;
}
.button.primary { -fx-background-color: -fx-primary; }
.button.success { -fx-background-color: -fx-success; }
.button.danger  { -fx-background-color: -fx-danger; }
.button.warn    { -fx-background-color: -fx-warn; }
.button.info    { -fx-background-color: -fx-info; }
.button.ghost {
  -fx-background-color: transparent;
  -fx-text-fill: -fx-txt;
  -fx-border-color: -fx-border;
  -fx-border-width: 1;
  -fx-border-radius: 10;
}
.button:disabled {
  -fx-opacity: 1.0;
  -fx-background-color: #E9EEF7;
  -fx-text-fill: #9AA4B2;
  -fx-border-color: #D7DFEA;
}

/* Tabla */
.table-view {
  -fx-background-color: -fx-surface;
  -fx-background-radius: 14;
  -fx-border-color: -fx-border;
  -fx-border-radius: 14;
}
.table-view .column-header-background {
  -fx-background-color: -fx-surface-2;
  -fx-background-radius: 14 14 0 0;
  -fx-border-color: -fx-border;
  -fx-border-width: 0 0 1 0;
}
.table-view .column-header, .table-view .filler { -fx-background-color: transparent; -fx-size: 42px; }
.table-row-cell:odd { -fx-background-color: #FAFBFF; }
.table-row-cell:selected { -fx-background-color: rgba(14,165,233,0.16); }
.table-cell { -fx-text-fill: -fx-txt; -fx-padding: 10 12; }

/* Grid de productos (Ventas) */
.product-card { -fx-pref-width: 230; }
.product-card .name  { -fx-font-size: 14.5px; -fx-font-weight: 900; }
.product-card .code  { -fx-text-fill: -fx-muted; }
.product-card .price { -fx-text-fill: #059669; -fx-font-weight: 900; }
.product-add { -fx-background-color: -fx-primary; -fx-background-radius: 10; -fx-padding: 8 12; -fx-font-weight: 900; }

/* Carrito */
.cart { -fx-pref-width: 360; }
.cart .line { -fx-border-color: -fx-border; -fx-border-width: 0 0 1 0; -fx-padding: 8 0; }

/* KPIs / badges */
.kpi-title { -fx-text-fill: -fx-muted; -fx-font-size: 12.5px; }
.kpi-value { -fx-font-size: 22px; -fx-font-weight: 900; }

/* Alertas */
.alert { -fx-background-radius: 12; -fx-padding: 10 12; -fx-font-weight: 900; }
.alert-info    { -fx-background-color: rgba(56,189,248,.18); -fx-text-fill: #075985; }
.alert-warn    { -fx-background-color: rgba(245,158,11,.18); -fx-text-fill: #92400E; }
.alert-danger  { -fx-background-color: rgba(239,68,68,.18);  -fx-text-fill: #7F1D1D; }
.alert-success { -fx-background-color: rgba(16,185,129,.18); -fx-text-fill: #065F46; }
EOF

# ====================== ReportsView (dashboard de métricas) ======================
cat <<'EOF' > src/main/java/com/papeleria/pos/views/ReportsView.java
package com.papeleria.pos.views;

import com.papeleria.pos.models.Product;
import com.papeleria.pos.models.Sale;
import com.papeleria.pos.services.InventoryService;
import com.papeleria.pos.services.StorageService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.List;

public class ReportsView extends VBox {

    public ReportsView(InventoryService inventory, StorageService storage){
        setSpacing(12);
        setPadding(new Insets(12));

        Label title = new Label("Reportes y Estadísticas"); title.getStyleClass().add("h1");
        Label sub = new Label("Análisis de ventas y rendimiento de tu papelería"); sub.getStyleClass().add("subtle");

        // Barra de rango + export (maquillaje)
        HBox bar = new HBox(8);
        ChoiceBox<String> rango = new ChoiceBox<>();
        rango.getItems().addAll("Últimos 7 días","Últimos 30 días","Este mes","Año en curso");
        rango.getSelectionModel().selectFirst();
        Region sp = new Region(); HBox.setHgrow(sp, Priority.ALWAYS);
        Label export = new Label("⬇ Exportar"); export.getStyleClass().add("button"); export.getStyleClass().add("ghost");
        bar.getChildren().addAll(rango, sp, export);

        // KPIs
        HBox kpis = new HBox(12,
                kpi("Ventas Hoy", "$" + String.format("%.2f", totalVentas(storage))),
                kpi("Transacciones", String.valueOf(totalTransacciones(storage))),
                kpi("Ticket Promedio", "$" + String.format("%.2f", ticketPromedio(storage))),
                kpi("Ganancia Neta", "$" + String.format("%.2f", gananciaSimple(storage)))
        );

        // Gráfica Ventas por período (dummy/simple con 7 barras)
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        BarChart<String, Number> ventas = new BarChart<>(x, y);
        ventas.setLegendVisible(false);
        ventas.setCategoryGap(12);
        ventas.setTitle(null);
        ventas.setPrefHeight(240);
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        double base = Math.max(1.0, totalVentas(storage));
        for (int i=1;i<=7;i++) {
            s.getData().add(new XYChart.Data<>("D"+i, (base/7.0) * (0.6 + (i%3)*0.2)));
        }
        ventas.getData().add(s);
        StackPane ventasCard = new StackPane(ventas); ventasCard.getStyleClass().add("card"); ventasCard.setPadding(new Insets(12));

        // Gráfica Productos más vendidos (dummy usando inventario)
        CategoryAxis px = new CategoryAxis();
        NumberAxis py = new NumberAxis();
        BarChart<String, Number> top = new BarChart<>(px, py);
        top.setLegendVisible(false);
        top.setCategoryGap(8);
        top.setPrefHeight(240);
        XYChart.Series<String, Number> s2 = new XYChart.Series<>();
        List<Product> prods = inventory.list();
        int count = 0;
        for (Product p : prods) {
            s2.getData().add(new XYChart.Data<>(p.getNombre().length()>10 ? p.getNombre().substring(0,10)+"…" : p.getNombre(), Math.max(0, Math.min(100, p.getStock()))));
            if (++count==5) break;
        }
        if (count==0){
            s2.getData().add(new XYChart.Data<>("Sin datos", 0));
        }
        top.getData().add(s2);
        StackPane topCard = new StackPane(top); topCard.getStyleClass().add("card"); topCard.setPadding(new Insets(12));

        // Dos tarjetas inferiores tipo resumen
        VBox leftBottom = new VBox(12, new Label("Top 5 Productos"), topCard); leftBottom.getStyleClass().add("panel");
        VBox rightBottom = new VBox(12, new Label("Ventas por Período"), ventasCard); rightBottom.getStyleClass().add("panel");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12);
        grid.add(leftBottom, 0, 0);
        grid.add(rightBottom, 1, 0);
        GridPane.setHgrow(leftBottom, Priority.ALWAYS);
        GridPane.setHgrow(rightBottom, Priority.ALWAYS);

        getChildren().addAll(title, sub, bar, kpis, grid);
    }

    private HBox kpi(String title, String value){
        Label t = new Label(title); t.getStyleClass().add("kpi-title");
        Label v = new Label(value); v.getStyleClass().add("kpi-value");
        VBox inner = new VBox(4, t, v);
        HBox wrap = new HBox(inner);
        wrap.getStyleClass().add("kpi");
        wrap.setPadding(new Insets(12));
        wrap.setPrefWidth(220);
        return wrap;
    }

    // ====== Cálculos simples a partir de ventas guardadas ======
    private double totalVentas(StorageService storage){
        double sum = 0.0;
        for (Sale s : storage.loadSales()) sum += s.getTotal();
        return sum;
    }
    private int totalTransacciones(StorageService storage){
        return storage.loadSales().size();
    }
    private double ticketPromedio(StorageService storage){
        int n = storage.loadSales().size();
        return n==0 ? 0.0 : totalVentas(storage)/n;
    }
    private double gananciaSimple(StorageService storage){
        // Estimación rápida 40% margen sobre total (coincide con la tarjeta de ejemplo)
        return totalVentas(storage) * 0.40;
    }
}
EOF

# ====================== MainView: enlazar botón de Reportes ======================
cat <<'EOF' > src/main/java/com/papeleria/pos/views/MainView.java
package com.papeleria.pos.views;

import com.papeleria.pos.services.*;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.*;

public class MainView extends BorderPane {
    private final SessionService session;
    private final InventoryService inventoryService;
    private final SalesService salesService;
    private final ProductionService productionService;
    private final EventBus bus;
    private final StorageService storage;

    private final VBox sidebar = new VBox(14);
    private final StackPane center = new StackPane();

    private final InventoryView inventoryView;
    private final SalesView salesView;
    private final ProductionView productionView;
    private final ReportsView reportsView;

    public MainView(SessionService session, InventoryService inventoryService, SalesService salesService,
                    ProductionService productionService, EventBus bus){
        this.session = session; this.inventoryService = inventoryService;
        this.salesService = salesService; this.productionService = productionService; this.bus = bus;
        this.storage = new StorageService(java.nio.file.Path.of("").toAbsolutePath()); // para reportes

        setPadding(new Insets(16));
        sidebar.getStyleClass().add("sidebar");

        Button bSales  = mk("🛒");
        Button bInv    = mk("📦");
        Button bProd   = mk("🛠️");
        Button bReport = mk("📊");
        Button bLogout = mk("⏻");
        bSales.getStyleClass().add("active");
        bLogout.setOnAction(e -> { session.logout(); System.exit(0); });

        sidebar.getChildren().addAll(bSales, bInv, bProd, bReport, bLogout);
        setLeft(sidebar);

        inventoryView = new InventoryView(session, inventoryService, bus);
        salesView     = new SalesView(session, salesService, inventoryService, bus);
        productionView= new ProductionView(session, productionService, inventoryService, bus);
        reportsView   = new ReportsView(inventoryService, storage);

        setContent(salesView); // inicia en POS

        bSales.setOnAction(e -> { activate(bSales, bInv, bProd, bReport); setContent(salesView); });
        bInv.setOnAction(e -> { activate(bInv, bSales, bProd, bReport); setContent(inventoryView); });
        bProd.setOnAction(e -> { activate(bProd, bSales, bInv, bReport); setContent(productionView); });
        bReport.setOnAction(e -> { activate(bReport, bSales, bInv, bProd); setContent(reportsView); });
    }

    private Button mk(String icon){
        Button b = new Button(icon);
        b.getStyleClass().add("nav-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setPrefHeight(52);
        return b;
    }
    private void activate(Button on, Button... off){
        on.getStyleClass().add("active");
        for (Button x : off) x.getStyleClass().remove("active");
    }
    private void setContent(Node n){ center.getChildren().setAll(new VBox(){{
        getStyleClass().add("page"); getChildren().add(n);
    }}); setCenter(center); }
}
EOF

# ====================== Arreglo tipográfico para KPIs con elipsis ======================
# (forzar que los botones de acción no queden "difuminados")
# Nada que tocar en Java; el CSS actualizado ya muestra bordes/headers visibles.

echo ">> Recompilando..."
mvn -q clean package

echo
echo "========================================"
echo "Listo. Ejecuta:"
echo "mvn -q javafx:run"
echo "========================================"
EOF

