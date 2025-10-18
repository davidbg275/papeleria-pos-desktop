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
    private final UsersView usersView;

    // ⬅⬅ NOTA: ahora recibimos 'storage' para no crear otro nuevo
    public MainView(SessionService session,
            InventoryService inventoryService,
            SalesService salesService,
            ProductionService productionService,
            EventBus bus,
            StorageService storage) {

        this.session = session;
        this.inventoryService = inventoryService;
        this.salesService = salesService;
        this.productionService = productionService;
        this.bus = bus;
        this.storage = storage;

        setPadding(new Insets(16));
        sidebar.getStyleClass().add("sidebar");

        // === Botones (mantén estos nombres) ===
        Button bSales = mk("🛒");
        Button bInv = mk("📦");
        Button bProd = mk("🛠️");
        Button bReport = mk("📊");
        Button bUsers = mk("⚙️");
        Button bLogout = mk("⏻");
        bSales.getStyleClass().add("active");
        bLogout.setOnAction(e -> {
            session.logout();
            System.exit(0);
        });

        sidebar.getChildren().addAll(bSales, bInv, bProd, bReport, bUsers, bLogout);
        setLeft(sidebar);

        // === CREA LAS VISTAS ANTES DE USARLAS EN HANDLERS ===
        inventoryView = new InventoryView(session, inventoryService, bus);
        salesView = new SalesView(session, salesService, inventoryService, bus);
        productionView = new ProductionView(session, productionService, inventoryService, bus);
        reportsView = new ReportsView(inventoryService, storage, bus); // ← firma nueva con bus
        usersView = new UsersView(new UserService(storage));

        // === Contenido inicial (ventas) ===
        setContent(salesView);

        // === Permisos por rol ===
        boolean isAdmin = session.isAdmin();
        bInv.setDisable(!isAdmin);
        bProd.setDisable(!isAdmin);
        bUsers.setDisable(!isAdmin);

        // === Handlers (después de crear vistas) ===
        bSales.setOnAction(e -> {
            activate(bSales, bInv, bProd, bReport, bUsers);
            setContent(salesView);
        });

        bInv.setOnAction(e -> {
            if (!session.isAdmin())
                return;
            activate(bInv, bSales, bProd, bReport, bUsers);
            setContent(inventoryView);
        });

        bProd.setOnAction(e -> {
            if (!session.isAdmin())
                return;
            activate(bProd, bSales, bInv, bReport, bUsers);
            setContent(productionView);
        });

        bReport.setOnAction(e -> {
            activate(bReport, bSales, bInv, bProd, bUsers);
            setContent(reportsView);
        });

        bUsers.setOnAction(e -> {
            if (!session.isAdmin())
                return;
            activate(bUsers, bSales, bInv, bProd, bReport);
            setContent(usersView);
        });
    }

    private Button mk(String icon) {
        Button b = new Button(icon);
        b.getStyleClass().add("nav-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setPrefHeight(52);
        return b;
    }

    private void activate(Button on, Button... off) {
        on.getStyleClass().add("active");
        for (Button x : off)
            x.getStyleClass().remove("active");
    }

    private void setContent(Node n) {
        center.getChildren().setAll(new VBox() {
            {
                getStyleClass().add("page");
                getChildren().add(n);
            }
        });
        setCenter(center);
    }
}
