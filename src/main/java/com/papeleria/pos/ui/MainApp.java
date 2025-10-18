package com.papeleria.pos.ui;

import com.papeleria.pos.services.*;
import com.papeleria.pos.views.LoginView;
import com.papeleria.pos.views.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        Path base = Path.of("").toAbsolutePath();
        StorageService storage = new StorageService(base);
        EventBus bus = new EventBus();
        SessionService session = new SessionService(storage);
        InventoryService inventory = new InventoryService(storage, bus);
        SalesService sales = new SalesService(storage, inventory, bus);

        // ⬇️ CORREGIDO: ProductionService solo recibe (InventoryService, EventBus)
        ProductionService production = new ProductionService(inventory, bus);

        Stage loginStage = new Stage();
        new LoginView(session).show(loginStage, () -> {
            loginStage.close();
            MainView main = new MainView(session, inventory, sales, production, bus, storage);
            Scene scene = new Scene(main, 1200, 750);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            primaryStage.setTitle("POS Papelería");
            primaryStage.setScene(scene);
            primaryStage.show();
        });
    }
}
