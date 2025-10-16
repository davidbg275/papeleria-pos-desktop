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
