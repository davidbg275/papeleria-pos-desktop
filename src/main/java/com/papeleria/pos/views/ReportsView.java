package com.papeleria.pos.views;

import com.papeleria.pos.models.Product;
import com.papeleria.pos.models.Sale;
import com.papeleria.pos.services.EventBus;
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
import javafx.scene.control.Button;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import com.papeleria.pos.services.SalesService;
import com.papeleria.pos.services.SessionService;

import javafx.scene.layout.*;

import java.util.List;

public class ReportsView extends VBox {

    public ReportsView(SessionService session, SalesService sales, InventoryService inventory, StorageService storage,
            EventBus bus) {

        setSpacing(12);
        setPadding(new Insets(12));

        Label title = new Label("Reportes y Estadísticas");
        title.getStyleClass().add("h1");
        Label sub = new Label("Análisis de ventas y rendimiento de tu papelería");
        sub.getStyleClass().add("subtle");

        // Barra de rango + export (maquillaje)
        HBox bar = new HBox(8);
        ChoiceBox<String> rango = new ChoiceBox<>();
        rango.getItems().addAll("Últimos 7 días", "Últimos 30 días", "Este mes", "Año en curso");
        rango.getSelectionModel().selectFirst();
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label export = new Label("⬇ Exportar");
        export.getStyleClass().add("button");
        export.getStyleClass().add("ghost");
        Button cancelar = new Button("Cancelar venta");
        cancelar.getStyleClass().add("button");
        cancelar.getStyleClass().add("danger");
        cancelar.setDisable(!session.isAdmin());
        cancelar.setOnAction(e -> {
            TextInputDialog dlg = new TextInputDialog();
            dlg.setTitle("Cancelar venta");
            dlg.setHeaderText("Ingrese el ID de la venta a cancelar");
            dlg.setContentText("ID de ticket/venta:");
            dlg.showAndWait().ifPresent(id -> {
                String v = id == null ? "" : id.trim();
                if (v.isEmpty())
                    return;
                // Confirmación
                Alert c = new Alert(Alert.AlertType.CONFIRMATION);
                c.setTitle("Confirmar cancelación");
                c.setHeaderText("¿Cancelar la venta " + v + "?");
                c.setContentText("Esta acción repone inventario y elimina el ticket.");
                java.util.Optional<ButtonType> rr = c.showAndWait();
                if (rr.isEmpty() || rr.get() != ButtonType.OK)
                    return;

                boolean ok = sales.cancelarVenta(v, session.isAdmin());
                Alert a = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
                a.setTitle("Cancelar venta");
                a.setHeaderText(ok ? "Venta cancelada" : "No autorizado o venta inexistente");
                a.setContentText(ok ? "La venta fue cancelada y el inventario repuesto."
                        : "Verifique el ID y el rol del usuario.");
                a.showAndWait();
            });
        });

        bar.getChildren().addAll(rango, sp, export, cancelar);

        // KPIs
        HBox kpis = new HBox(12,
                kpi("Ventas Hoy", "$" + String.format("%.2f", totalVentas(storage))),
                kpi("Transacciones", String.valueOf(totalTransacciones(storage))),
                kpi("Ticket Promedio", "$" + String.format("%.2f", ticketPromedio(storage))),
                kpi("Ganancia Neta", "$" + String.format("%.2f", gananciaSimple(storage))));

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
        for (int i = 1; i <= 7; i++) {
            s.getData().add(new XYChart.Data<>("D" + i, (base / 7.0) * (0.6 + (i % 3) * 0.2)));
        }
        ventas.getData().add(s);
        StackPane ventasCard = new StackPane(ventas);
        ventasCard.getStyleClass().add("card");
        ventasCard.setPadding(new Insets(12));

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
            s2.getData()
                    .add(new XYChart.Data<>(
                            p.getNombre().length() > 10 ? p.getNombre().substring(0, 10) + "…" : p.getNombre(),
                            Math.max(0, Math.min(100, p.getStock()))));
            if (++count == 5)
                break;
        }
        if (count == 0) {
            s2.getData().add(new XYChart.Data<>("Sin datos", 0));
        }
        top.getData().add(s2);
        StackPane topCard = new StackPane(top);
        topCard.getStyleClass().add("card");
        topCard.setPadding(new Insets(12));

        // Dos tarjetas inferiores tipo resumen
        VBox leftBottom = new VBox(12, new Label("Top 5 Productos"), topCard);
        leftBottom.getStyleClass().add("panel");
        VBox rightBottom = new VBox(12, new Label("Ventas por Período"), ventasCard);
        rightBottom.getStyleClass().add("panel");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.add(leftBottom, 0, 0);
        grid.add(rightBottom, 1, 0);
        GridPane.setHgrow(leftBottom, Priority.ALWAYS);
        GridPane.setHgrow(rightBottom, Priority.ALWAYS);

        getChildren().addAll(title, sub, bar, kpis, grid);
        bus.subscribe(EventBus.Topic.SALES_CHANGED,
                e -> javafx.application.Platform.runLater(() -> refresh(inventory, storage, kpis, ventas, top)));
        bus.subscribe(EventBus.Topic.INVENTORY_CHANGED,
                e -> javafx.application.Platform.runLater(() -> refresh(inventory, storage, kpis, ventas, top)));
    }

    private HBox kpi(String title, String value) {
        Label t = new Label(title);
        t.getStyleClass().add("kpi-title");
        Label v = new Label(value);
        v.getStyleClass().add("kpi-value");
        VBox inner = new VBox(4, t, v);
        HBox wrap = new HBox(inner);
        wrap.getStyleClass().add("kpi");
        wrap.setPadding(new Insets(12));
        wrap.setPrefWidth(220);
        return wrap;
    }

    // ====== Cálculos simples a partir de ventas guardadas ======
    private double totalVentas(StorageService storage) {
        double sum = 0.0;
        for (Sale s : storage.loadSales())
            sum += s.getTotal();
        return sum;
    }

    private int totalTransacciones(StorageService storage) {
        return storage.loadSales().size();
    }

    private double ticketPromedio(StorageService storage) {
        int n = storage.loadSales().size();
        return n == 0 ? 0.0 : totalVentas(storage) / n;
    }

    private double gananciaSimple(StorageService storage) {
        // Estimación rápida 40% margen sobre total (coincide con la tarjeta de ejemplo)
        return totalVentas(storage) * 0.40;
    }

    // --- NUEVO: método refresh para recalcular KPIs y series ---
    private void refresh(InventoryService inventory, StorageService storage, HBox kpis, BarChart<String, Number> ventas,
            BarChart<String, Number> top) {
        // KPIs
        ((Label) ((VBox) ((HBox) kpis.getChildren().get(0)).getChildren().get(0)).getChildren().get(1))
                .setText("$" + String.format("%.2f", totalVentas(storage)));
        ((Label) ((VBox) ((HBox) kpis.getChildren().get(1)).getChildren().get(0)).getChildren().get(1))
                .setText(String.valueOf(totalTransacciones(storage)));
        ((Label) ((VBox) ((HBox) kpis.getChildren().get(2)).getChildren().get(0)).getChildren().get(1))
                .setText("$" + String.format("%.2f", ticketPromedio(storage)));
        ((Label) ((VBox) ((HBox) kpis.getChildren().get(3)).getChildren().get(0)).getChildren().get(1))
                .setText("$" + String.format("%.2f", gananciaSimple(storage)));

        // Ventas últimos 7 (dummy proporcional)
        ventas.getData().clear();
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        double base = Math.max(1.0, totalVentas(storage));
        for (int i = 1; i <= 7; i++)
            s.getData().add(new XYChart.Data<>("D" + i, (base / 7.0) * (0.6 + (i % 3) * 0.2)));
        ventas.getData().add(s);

        // Top productos por stock
        top.getData().clear();
        XYChart.Series<String, Number> s2 = new XYChart.Series<>();
        List<Product> prods = inventory.list();
        int count = 0;
        for (Product p : prods) {
            s2.getData()
                    .add(new XYChart.Data<>(
                            p.getNombre().length() > 10 ? p.getNombre().substring(0, 10) + "…" : p.getNombre(),
                            Math.max(0, Math.min(100, p.getStock()))));
            if (++count == 5)
                break;
        }
        if (count == 0)
            s2.getData().add(new XYChart.Data<>("Sin datos", 0));
        top.getData().add(s2);
    }
}
