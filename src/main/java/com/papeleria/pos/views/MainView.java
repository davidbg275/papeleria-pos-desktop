package com.papeleria.pos.views;

import com.papeleria.pos.services.EventBus;
import com.papeleria.pos.services.InventoryService;
import com.papeleria.pos.services.ProductionService;
import com.papeleria.pos.services.SalesService;
import com.papeleria.pos.services.SessionService;
import com.papeleria.pos.services.StorageService;
import com.papeleria.pos.services.UserService;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.lang.reflect.Constructor;
import java.util.Locale;
import java.util.Objects;

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
    private final Node usersView; // sin referencia de tipo a UsersView

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

        // Botones
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

        // Vistas
        inventoryView = new InventoryView(session, inventoryService, bus);
        salesView = new SalesView(session, salesService, inventoryService, bus);
        productionView = new ProductionView(session, productionService, inventoryService, bus);
        reportsView = new ReportsView(session, salesService, inventoryService, storage, bus);

        // UsersView por reflexión (si falta, será null)
        usersView = tryBuildUsersView();

        // Contenido inicial
        setContent(salesView);

        // Permisos
        boolean isAdmin = session.isAdmin();
        bInv.setDisable(!isAdmin);
        bProd.setDisable(!isAdmin);
        // Si no existe UsersView o no es admin, se oculta
        if (!isAdmin || usersView == null) {
            bUsers.setDisable(true);
            bUsers.setVisible(false);
        }

        // Handlers
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
            if (!session.isAdmin() || usersView == null)
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

    /**
     * Intenta instanciar com.papeleria.pos.views.UsersView por reflexión.
     * Soporta constructores: (), (SessionService), (UserService), (SessionService,
     * UserService).
     * Devuelve null si la clase no existe o no es un Node.
     */
    private Node tryBuildUsersView() {
        try {
            Class<?> cls = Class.forName("com.papeleria.pos.views.UsersView");

            for (Constructor<?> ctor : cls.getConstructors()) {
                Class<?>[] pt = ctor.getParameterTypes();
                try {
                    Object view = null;

                    if (pt.length == 0) {
                        view = ctor.newInstance();
                    } else if (pt.length == 1) {
                        String p0 = pt[0].getName();
                        if (p0.equals("com.papeleria.pos.services.SessionService")) {
                            view = ctor.newInstance(this.session);
                        } else if (p0.equals("com.papeleria.pos.services.UserService")) {
                            view = ctor.newInstance(new UserService(this.storage));
                        }
                    } else if (pt.length == 2) {
                        String p0 = pt[0].getName();
                        String p1 = pt[1].getName();
                        if (p0.equals("com.papeleria.pos.services.SessionService")
                                && p1.equals("com.papeleria.pos.services.UserService")) {
                            view = ctor.newInstance(this.session, new UserService(this.storage));
                        }
                    }

                    if (view instanceof Node node)
                        return node;
                } catch (Throwable ignore) {
                    // prueba siguiente constructor
                }
            }
        } catch (ClassNotFoundException ignore) {
            // UsersView no existe
        }
        return null;
    }
}
