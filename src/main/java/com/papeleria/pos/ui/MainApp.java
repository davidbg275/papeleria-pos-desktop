package com.papeleria.pos.ui;

import com.papeleria.pos.services.*;
import com.papeleria.pos.views.LoginView;
import com.papeleria.pos.views.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        java.nio.file.Path base = java.nio.file.Path.of("").toAbsolutePath();

        StorageService storage = new StorageService(base);
        EventBus bus = new EventBus();

        // usuarios por defecto si no hay
        new UserService(storage);

        SessionService session = new SessionService(storage);
        InventoryService inventory = new InventoryService(storage, bus);
        SalesService sales = new SalesService(storage, inventory, bus);
        // pasar storage al servicio de producción
        ProductionService production = new ProductionService(inventory, bus, storage);

        Runnable openMain = () -> {
            MainView main = new MainView(session, inventory, sales, production, bus, storage);
            Scene scene = new Scene(main, 1200, 750);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            primaryStage.setTitle("POS Papelería");
            primaryStage.setScene(scene);
            primaryStage.show();
        };

        boolean hasUser = session.getUsername() != null && !session.getUsername().isBlank();
        boolean userExists = new UserService(storage).find(session.getUsername()).isPresent();

        if (hasUser && userExists) {
            openMain.run();
        } else {
            Stage loginStage = new Stage();
            new LoginView(session).show(loginStage, () -> {
                loginStage.close();
                openMain.run();
            });
        }
    }
}
