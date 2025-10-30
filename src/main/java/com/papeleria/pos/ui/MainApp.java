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
        // Recomendado: carpeta fija en HOME (persistente entre ejecuciones)
        // Si prefieres seguir con el directorio actual, cambia por:
        // Path.of("").toAbsolutePath()
        java.nio.file.Path base = java.nio.file.Path.of("").toAbsolutePath();

        StorageService storage = new StorageService(base);
        EventBus bus = new EventBus();

        // 1) Sembrar usuarios por defecto ANTES del login (si users.json está vacío)
        new UserService(storage); // su constructor hace seedDefaults() si no hay usuarios

        SessionService session = new SessionService(storage);
        InventoryService inventory = new InventoryService(storage, bus);
        SalesService sales = new SalesService(storage, inventory, bus);
        ProductionService production = new ProductionService(inventory, bus);

        Runnable openMain = () -> {
            MainView main = new MainView(session, inventory, sales, production, bus, storage);
            Scene scene = new Scene(main, 1200, 750);
            scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            primaryStage.setTitle("POS Papelería");
            primaryStage.setScene(scene);
            primaryStage.show();
        };

        // 2) Si hay sesión guardada y el usuario existe, entra directo al MainView
        boolean hasUser = session.getUsername() != null && !session.getUsername().isBlank();
        boolean userExists = new UserService(storage).find(session.getUsername()).isPresent();

        if (hasUser && userExists) {
            openMain.run();
        } else {
            // Si no, mostrar Login y, al autenticar, abrir Main
            Stage loginStage = new Stage();
            new LoginView(session).show(loginStage, () -> {
                loginStage.close();
                openMain.run();
            });
        }
    }

}
