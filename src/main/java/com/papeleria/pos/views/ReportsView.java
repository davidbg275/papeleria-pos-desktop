package com.papeleria.pos.views;

import com.papeleria.pos.models.Product;
import com.papeleria.pos.models.Sale;
import com.papeleria.pos.services.EventBus;
import com.papeleria.pos.services.InventoryService;
import com.papeleria.pos.services.SalesService;
import com.papeleria.pos.services.SessionService;
import com.papeleria.pos.services.StorageService;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class ReportsView extends VBox {

    public ReportsView(SessionService session,
            SalesService sales,
            InventoryService inventory,
            StorageService storage,
            EventBus bus) {

        setSpacing(12);
        setPadding(new Insets(12));

        Label title = new Label("Reportes y Estadísticas");
        title.getStyleClass().add("h1");
        Label sub = new Label("Análisis de ventas y rendimiento de tu papelería");
        sub.getStyleClass().add("subtle");

        // --- Barra superior
        HBox bar = new HBox(8);
        ChoiceBox<String> rango = new ChoiceBox<>();
        rango.getItems().addAll("Últimos 7 días", "Últimos 30 días", "Este mes", "Año en curso");
        rango.getSelectionModel().selectFirst();

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label export = new Label("⬇ Exportar");
        export.getStyleClass().addAll("button", "ghost");

        Button ver = new Button("Ver ticket");
        ver.getStyleClass().addAll("button", "ghost");

        Button cancelar = new Button("Cancelar venta");
        cancelar.getStyleClass().addAll("button", "danger");
        // No se deshabilita para vendedores: pediremos credenciales de ADMIN
        bar.getChildren().addAll(rango, sp, export, ver, cancelar);

        // ===== Historial de ventas =====
        TableView<Sale> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<Sale, String> cFecha = new TableColumn<>("Fecha");
        cFecha.setMinWidth(160);
        cFecha.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getFecha() == null ? ""
                        : d.getValue().getFecha()
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));

        TableColumn<Sale, String> cId = new TableColumn<>("ID");
        cId.setMinWidth(100);
        cId.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getId()));

        TableColumn<Sale, Number> cTotal = new TableColumn<>("Total");
        cTotal.setMinWidth(100);
        cTotal.setCellValueFactory(d -> new SimpleDoubleProperty(d.getValue().getTotal()));

        tabla.getColumns().setAll(cFecha, cId, cTotal);

        // Cargar y ordenar por fecha desc
        List<Sale> ventas = new ArrayList<>(sales.listSales());
        ventas.sort(Comparator.comparing(Sale::getFecha,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        tabla.getItems().setAll(ventas);

        // Panel de ticket
        TextArea ticketView = new TextArea();
        ticketView.setEditable(false);
        ticketView.setWrapText(true);
        ticketView.setPrefRowCount(12);
        ticketView.setStyle("-fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 12px;");

        // Al seleccionar una venta, mostrar ticket
        tabla.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> {
            if (s == null) {
                ticketView.clear();
                return;
            }
            ticketView.setText(sales.readTicket(s.getId()));
        });

        // Botón Ver ticket
        ver.setOnAction(e -> {
            Sale s = tabla.getSelectionModel().getSelectedItem();
            if (s == null)
                return;
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("Ticket " + s.getId());
            a.setHeaderText("Vista previa de ticket");
            TextArea ta = new TextArea(ticketView.getText().isEmpty()
                    ? sales.readTicket(s.getId())
                    : ticketView.getText());
            ta.setEditable(false);
            ta.setWrapText(true);
            ta.setStyle("-fx-font-family: 'JetBrains Mono', monospace; -fx-font-size: 12px;");
            ta.setPrefSize(420, 380);
            a.getDialogPane().setContent(ta);
            a.showAndWait();
        });

        // Botón Cancelar venta (pide credenciales de ADMIN)
        cancelar.setOnAction(e -> {
            Sale s = tabla.getSelectionModel().getSelectedItem();
            if (s == null)
                return;

            // Pide usuario/contraseña de ADMIN
            Dialog<Pair<String, String>> d = new Dialog<>();
            d.setTitle("Autorización de administrador");
            d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            GridPane g = new GridPane();
            g.setHgap(8);
            g.setVgap(8);
            g.setPadding(new Insets(10));
            TextField user = new TextField();
            user.setPromptText("Usuario ADMIN");
            PasswordField pass = new PasswordField();
            pass.setPromptText("Contraseña ADMIN");
            g.addRow(0, new Label("Usuario"), user);
            g.addRow(1, new Label("Contraseña"), pass);
            d.getDialogPane().setContent(g);

            d.setResultConverter(bt -> bt == ButtonType.OK ? new Pair<>(user.getText().trim(), pass.getText()) : null);
            Optional<Pair<String, String>> cred = d.showAndWait();
            if (cred.isEmpty())
                return;

            // Confirmar la cancelación
            Alert c = new Alert(Alert.AlertType.CONFIRMATION);
            c.setTitle("Confirmar cancelación");
            c.setHeaderText("¿Cancelar la venta " + s.getId() + "?");
            c.setContentText("Se repondrá inventario y se eliminará el ticket.");
            Optional<ButtonType> rr = c.showAndWait();
            if (rr.isEmpty() || rr.get() != ButtonType.OK)
                return;

            boolean ok = sales.cancelarVenta(s.getId(), cred.get().getKey(), cred.get().getValue());
            Alert res = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
            res.setTitle("Cancelar venta");
            res.setHeaderText(ok ? "Venta cancelada" : "Autorización inválida o venta no encontrada");
            res.setContentText(ok ? "Inventario repuesto. Ticket eliminado."
                    : "Verifique credenciales o seleccione otra venta.");
            res.showAndWait();

            if (ok) {
                // refrescar tabla
                List<Sale> vs = new ArrayList<>(sales.listSales());
                vs.sort(Comparator.comparing(Sale::getFecha,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed());
                tabla.getItems().setAll(vs);
                ticketView.clear();
            }
        });

        // Contenedor del historial
        VBox historyBox = new VBox(8, new Label("Historial de Ventas"), tabla,
                new Label("Vista de ticket"), ticketView);
        historyBox.getStyleClass().add("panel");
        historyBox.setPadding(new Insets(12));

        // ===== KPIs =====
        HBox kpis = new HBox(12,
                kpi("Ventas Hoy", "$" + String.format("%.2f", totalVentas(storage))),
                kpi("Transacciones", String.valueOf(totalTransacciones(storage))),
                kpi("Ticket Promedio", "$" + String.format("%.2f", ticketPromedio(storage))),
                kpi("Ganancia Neta", "$" + String.format("%.2f", gananciaSimple(storage))));

        // ===== Gráficas =====
        // Ventas por período (simple)
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        BarChart<String, Number> ventasChart = new BarChart<>(x, y);
        ventasChart.setLegendVisible(false);
        ventasChart.setCategoryGap(12);
        ventasChart.setTitle(null);
        ventasChart.setPrefHeight(240);
        XYChart.Series<String, Number> s1 = new XYChart.Series<>();
        double base = Math.max(1.0, totalVentas(storage));
        for (int i = 1; i <= 7; i++) {
            s1.getData().add(new XYChart.Data<>("D" + i, (base / 7.0) * (0.6 + (i % 3) * 0.2)));
        }
        ventasChart.getData().add(s1);
        StackPane ventasCard = new StackPane(ventasChart);
        ventasCard.getStyleClass().add("card");
        ventasCard.setPadding(new Insets(12));

        // Top productos por stock (simple)
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
            s2.getData().add(new XYChart.Data<>(
                    p.getNombre().length() > 10 ? p.getNombre().substring(0, 10) + "…" : p.getNombre(),
                    Math.max(0, Math.min(100, p.getStock()))));
            if (++count == 5)
                break;
        }
        if (count == 0)
            s2.getData().add(new XYChart.Data<>("Sin datos", 0));
        top.getData().add(s2);
        StackPane topCard = new StackPane(top);
        topCard.getStyleClass().add("card");
        topCard.setPadding(new Insets(12));

        // Layout de gráficas
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

        // Ensamblar página
        getChildren().addAll(title, sub, bar, kpis, historyBox, grid);

        // Suscripciones para refrescar KPIs/gráficas al cambiar ventas/inventario
        bus.subscribe(EventBus.Topic.SALES_CHANGED,
                e -> javafx.application.Platform.runLater(() -> {
                    refresh(inventory, storage, kpis, ventasChart, top);
                    refreshHistory(tabla, sales, ticketView); // ← también recarga la tabla y limpia vista
                }));

        bus.subscribe(EventBus.Topic.INVENTORY_CHANGED,
                e -> javafx.application.Platform.runLater(() -> {
                    refresh(inventory, storage, kpis, ventasChart, top);
                    // opcional: no es estrictamente necesario refrescar historial en cambios de
                    // inventario
                }));

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
        // Estimación rápida 40% margen
        return totalVentas(storage) * 0.40;
    }

    // Recalcular KPIs y series
    private void refresh(InventoryService inventory,
            StorageService storage,
            HBox kpis,
            BarChart<String, Number> ventas,
            BarChart<String, Number> top) {

        ((Label) ((VBox) ((HBox) kpis.getChildren().get(0)).getChildren().get(0)).getChildren().get(1))
                .setText("$" + String.format("%.2f", totalVentas(storage)));
        ((Label) ((VBox) ((HBox) kpis.getChildren().get(1)).getChildren().get(0)).getChildren().get(1))
                .setText(String.valueOf(totalTransacciones(storage)));
        ((Label) ((VBox) ((HBox) kpis.getChildren().get(2)).getChildren().get(0)).getChildren().get(1))
                .setText("$" + String.format("%.2f", ticketPromedio(storage)));
        ((Label) ((VBox) ((HBox) kpis.getChildren().get(3)).getChildren().get(0)).getChildren().get(1))
                .setText("$" + String.format("%.2f", gananciaSimple(storage)));

        ventas.getData().clear();
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        double base = Math.max(1.0, totalVentas(storage));
        for (int i = 1; i <= 7; i++) {
            s.getData().add(new XYChart.Data<>("D" + i, (base / 7.0) * (0.6 + (i % 3) * 0.2)));
        }
        ventas.getData().add(s);

        top.getData().clear();
        XYChart.Series<String, Number> s2 = new XYChart.Series<>();
        List<Product> prods = inventory.list();
        int count = 0;
        for (Product p : prods) {
            s2.getData().add(new XYChart.Data<>(
                    p.getNombre().length() > 10 ? p.getNombre().substring(0, 10) + "…" : p.getNombre(),
                    Math.max(0, Math.min(100, p.getStock()))));
            if (++count == 5)
                break;
        }
        if (count == 0)
            s2.getData().add(new XYChart.Data<>("Sin datos", 0));
        top.getData().add(s2);
    }

    // Recarga la tabla de ventas (historial) y limpia la vista de ticket
    private void refreshHistory(javafx.scene.control.TableView<Sale> tabla,
            SalesService sales,
            javafx.scene.control.TextArea ticketView) {
        java.util.List<Sale> vs = new java.util.ArrayList<>(sales.listSales());
        vs.sort(java.util.Comparator.comparing(Sale::getFecha,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).reversed());
        tabla.getItems().setAll(vs);
        ticketView.clear();
    }

}
