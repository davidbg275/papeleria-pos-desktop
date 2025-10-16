package com.papeleria.pos.views;

import com.papeleria.pos.models.Role;
import com.papeleria.pos.models.User;
import com.papeleria.pos.services.UserService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Optional;

public class UsersView extends VBox {

    private final UserService users;
    private final TableView<User> table = new TableView<>();
    private final ObservableList<User> backing = FXCollections.observableArrayList();

    public UsersView(UserService users) {
        this.users = users;
        setSpacing(12); setPadding(new Insets(12));

        Label title = new Label("Usuarios y Roles"); title.getStyleClass().add("h1");
        Label sub = new Label("Gestiona usuarios, contraseñas y permisos"); sub.getStyleClass().add("subtle");

        Button add = new Button("Nuevo Usuario");
        Button edit = new Button("Editar");
        Button del = new Button("Eliminar");
        HBox actions = new HBox(8, add, edit, del);

        table.setItems(backing);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<User, String> cUser = new TableColumn<>("Usuario");
        cUser.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getUsername()));
        cUser.setMinWidth(200);

        TableColumn<User, Role> cRole = new TableColumn<>("Rol");
        cRole.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getRole()));
        cRole.setMinWidth(140);

        table.getColumns().setAll(cUser, cRole);
        table.setPrefHeight(520);

        getChildren().addAll(title, sub, actions, table);

        add.setOnAction(e -> showDialog(null));
        edit.setOnAction(e -> showDialog(table.getSelectionModel().getSelectedItem()));
        del.setOnAction(e -> {
            User u = table.getSelectionModel().getSelectedItem();
            if (u == null) return;
            if (!users.remove(u.getUsername())) {
                new Alert(Alert.AlertType.WARNING, "Debe quedar al menos un ADMIN.").showAndWait();
            } else {
                refresh();
            }
        });

        refresh();
    }

    private void refresh() {
        backing.setAll(users.list());
    }

    private void showDialog(User editable) {
        Dialog<User> d = new Dialog<>();
        d.setTitle(editable == null ? "Nuevo Usuario" : "Editar Usuario");
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(12));
        TextField username = new TextField(); PasswordField pass = new PasswordField();
        ChoiceBox<Role> role = new ChoiceBox<>(FXCollections.observableArrayList(Role.ADMIN, Role.SELLER));

        username.setPromptText("usuario"); pass.setPromptText("contraseña"); role.getSelectionModel().select(Role.SELLER);

        if (editable != null) {
            username.setText(editable.getUsername()); username.setDisable(true);
            pass.setText(editable.getPassword());
            role.getSelectionModel().select(editable.getRole());
        }

        grid.addRow(0, new Label("Usuario"), username);
        grid.addRow(1, new Label("Contraseña"), pass);
        grid.addRow(2, new Label("Rol"), role);
        d.getDialogPane().setContent(grid);

        d.setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            String u = username.getText().trim(); String p = pass.getText().trim();
            Role r = role.getValue() == null ? Role.SELLER : role.getValue();
            if (u.isEmpty() || p.isEmpty()) return null;
            return new User(u, p, r);
        });

        Optional<User> res = d.showAndWait();
        res.ifPresent(u -> { users.upsert(u); refresh(); });
    }
}
