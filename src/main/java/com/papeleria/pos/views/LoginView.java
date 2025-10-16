package com.papeleria.pos.views;

import com.papeleria.pos.components.AlertBanner;
import com.papeleria.pos.services.SessionService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class LoginView {

    private final SessionService session;

    public LoginView(SessionService session){
        this.session = session;
    }

    public void show(Stage stage, Runnable onSuccess){
        VBox root = new VBox(14);
        root.getStyleClass().add("card");
        root.setPadding(new Insets(20));
        root.setMaxWidth(380);

        Label title = new Label("Iniciar sesión");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: 800;");

        TextField user = new TextField();
        user.setPromptText("Usuario");
        PasswordField pass = new PasswordField();
        pass.setPromptText("Contraseña");

        Button login = new Button("Entrar");
        login.getStyleClass().add("primary");
        login.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(10, title, user, pass, login);
        box.setFillWidth(true);

        VBox container = new VBox(16);
        container.setAlignment(Pos.CENTER);
        container.getChildren().addAll(box);
        container.setPadding(new Insets(30));

        BorderPane wrapper = new BorderPane(container);
        wrapper.setStyle("-fx-background-color: #0B1221;");
        Scene scene = new Scene(wrapper, 420, 300);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());

        login.setOnAction(ev -> {
            if (session.login(user.getText().trim(), pass.getText())){
                onSuccess.run();
            } else {
                container.getChildren().add(0, new AlertBanner("alert-danger","⛔","Usuario o contraseña incorrectos"));
            }
        });

        stage.setTitle("POS Papelería - Login");
        stage.setScene(scene);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.show();
    }
}
