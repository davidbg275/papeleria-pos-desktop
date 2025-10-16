#!/usr/bin/env bash
# patch_usuarios_roles_y_produccion_autocomplete.sh
# Agrega: Gestión de Usuarios/Roles y autocompletar en Producción (materiales y producto final).
# Ejecuta este archivo dentro de ~/Documentos/papeleria-pos-desktop

set -euo pipefail
cd "${1:-$HOME/Documentos/papeleria-pos-desktop}"

echo ">> Añadiendo Gestión de Usuarios/Roles y autocompletado en Producción..."

# ========= Modelos =========
mkdir -p src/main/java/com/papeleria/pos/models

cat <<'EOF' > src/main/java/com/papeleria/pos/models/Role.java
package com.papeleria.pos.models;
public enum Role { ADMIN, SELLER }
EOF

cat <<'EOF' > src/main/java/com/papeleria/pos/models/User.java
package com.papeleria.pos.models;

public class User {
    private String username;
    private String password;
    private Role role;

    public User() {}
    public User(String username, String password, Role role) {
        this.username = username; this.password = password; this.role = role;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
EOF

# ========= Services =========
mkdir -p src/main/java/com/papeleria/pos/services

cat <<'EOF' > src/main/java/com/papeleria/pos/services/UserService.java
package com.papeleria.pos.services;

import com.papeleria.pos.models.Role;
import com.papeleria.pos.models.User;

import java.util.*;
import java.util.stream.Collectors;

public class UserService {
    private final StorageService storage;

    public UserService(StorageService storage) {
        this.storage = storage;
        seedDefaults();
    }

    public List<User> list() {
        return new ArrayList<>(storage.loadUsers());
    }

    public Optional<User> find(String username) {
        return list().stream().filter(u -> u.getUsername().equalsIgnoreCase(username)).findFirst();
    }

    public void upsert(User u) {
        List<User> all = list();
        Optional<User> ex = all.stream().filter(x -> x.getUsername().equalsIgnoreCase(u.getUsername())).findFirst();
        if (ex.isPresent()) {
            ex.get().setPassword(u.getPassword());
            ex.get().setRole(u.getRole());
        } else {
            all.add(u);
        }
        storage.saveUsers(all);
    }

    public boolean remove(String username) {
        List<User> all = list();
        Optional<User> ex = all.stream().filter(u -> u.getUsername().equalsIgnoreCase(username)).findFirst();
        if (ex.isEmpty()) return false;

        // No permitir dejar el sistema sin ADMIN
        long admins = all.stream().filter(u -> u.getRole() == Role.ADMIN).count();
        if (ex.get().getRole() == Role.ADMIN && admins <= 1) return false;

        all = all.stream().filter(u -> !u.getUsername().equalsIgnoreCase(username)).collect(Collectors.toList());
        storage.saveUsers(all);
        return true;
    }

    public boolean validate(String username, String password) {
        return list().stream().anyMatch(u -> u.getUsername().equals(username) && u.getPassword().equals(password));
    }

    public Role roleOf(String username) {
        return find(username).map(User::getRole).orElse(Role.SELLER);
    }

    private void seedDefaults() {
        List<User> users = storage.loadUsers();
        if (users == null || users.isEmpty()) {
            users = new ArrayList<>();
            users.add(new User("admin", "admin", Role.ADMIN));
            users.add(new User("vendedor", "1234", Role.SELLER));
            storage.saveUsers(users);
        }
    }
}
EOF

# ========= Componentes (autocompletar genérico) =========
mkdir -p src/main/java/com/papeleria/pos/components

cat <<'EOF' > src/main/java/com/papeleria/pos/components/AutoCompleteCombo.java
package com.papeleria.pos.components;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Autocompletado simple para ComboBox editable (JavaFX 17). */
public class AutoCompleteCombo<T> {
    private final ComboBox<T> combo;
    private final ObservableList<T> source;
    private final Function<T,String> toText;

    public AutoCompleteCombo(ComboBox<T> combo, List<T> items, Function<T,String> toText) {
        this.combo = combo;
        this.source = FXCollections.observableArrayList(items);
        this.toText = toText;

        combo.setEditable(true);
        combo.setItems(FXCollections.observableArrayList(items));

        combo.getEditor().textProperty().addListener((obs,o,n) -> {
            String q = n == null ? "" : n.toLowerCase();
            List<T> filtered = source.stream()
                    .filter(it -> toText.apply(it).toLowerCase().contains(q))
                    .collect(Collectors.toList());
            combo.getItems().setAll(filtered);
            if (!combo.isShowing()) combo.show();
        });

        combo.setConverter(new StringConverter<T>() {
            @Override public String toString(T obj) { return obj == null ? "" : toText.apply(obj); }
            @Override public T fromString(String s) {
                return source.stream().filter(it -> toText.apply(it).equalsIgnoreCase(s)).findFirst().orElse(null);
            }
        });
    }
}
EOF

# ========= Vista: Usuarios y Roles =========
mkdir -p src/main/java/com/papeleria/pos/views

cat <<'EOF' > src/main/java/com/papeleria/pos/views/UsersView.java
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
EOF

# ========= Vista: Producción con autocompletar =========
cat <<'EOF' > src/main/java/com/papeleria/pos/views/ProductionView.java
package com.papeleria.pos.views;

import com.papeleria.pos.components.AutoCompleteCombo;
import com.papeleria.pos.components.AlertBanner;
import com.papeleria.pos.models.Product;
import com.papeleria.pos.services.EventBus;
import com.papeleria.pos.services.InventoryService;
import com.papeleria.pos.services.ProductionService;
import com.papeleria.pos.services.SessionService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.util.*;
import java.util.stream.Collectors;

public class ProductionView extends BorderPane {

    private final SessionService session;
    private final ProductionService production; // reservado si lo usas internamente
    private final InventoryService inventory;
    private final EventBus bus;

    // UI principal
    private final ComboBox<String> productoFinal = new ComboBox<>();
    private final Spinner<Integer> piezasPorLote = new Spinner<>(1, 100000, 10, 1);
    private final Spinner<Double> veces = new Spinner<>(0.1, 1000.0, 1.0, 0.5);
    private final Spinner<Double> ganancia = new Spinner<>(0.0, 3.0, 0.30, 0.05);
    private final Spinner<Double> extra = new Spinner<>(0.0, 100000.0, 0.0, 1.0);

    private final ComboBox<Product> material = new ComboBox<>();
    private final ComboBox<String> medida = new ComboBox<>(FXCollections.observableArrayList("Unidad base", "Paquete/Rollo"));
    private final Spinner<Double> cantidad = new Spinner<>(0.0, 100000.0, 1.0, 1.0);

    private final FlowPane chips = new FlowPane(8,8);
    private final ObservableList<Insumo> insumos = FXCollections.observableArrayList();

    // KPIs
    private final Label kCostoMat = new Label("$0.00");
    private final Label kCostoUnit = new Label("$0.00");
    private final Label kPrecioSug = new Label("$0.00");

    public ProductionView(SessionService session, ProductionService production, InventoryService inventory, EventBus bus){
        this.session = session; this.production = production; this.inventory = inventory; this.bus = bus;

        // ----- Lado izquierdo: chips + KPIs -----
        VBox left = new VBox(12);
        Label title = new Label("Producción / Armar productos"); title.getStyleClass().add("h1");
        Label sub = new Label("Define la receta (BOM), fabrica y actualiza inventario."); sub.getStyleClass().add("subtle");

        HBox kpis = new HBox(12, kpi("Costo materiales", kCostoMat),
                                  kpi("Costo unitario", kCostoUnit),
                                  kpi("Precio sugerido", kPrecioSug));
        VBox chipCard = new VBox(8, new Label("Insumos"), chips); chipCard.getStyleClass().add("card");
        chips.setPrefWrapLength(260);

        left.getChildren().addAll(title, sub, kpis, chipCard);
        left.setPadding(new Insets(12));

        // ----- Centro/Derecha: formulario -----
        GridPane form = new GridPane(); form.setHgap(10); form.setVgap(10); form.setPadding(new Insets(12));
        form.getStyleClass().add("card");

        // Producto final (ComboBox editable con autocompletar)
        productoFinal.setEditable(true);
        List<String> nombres = inventory.list().stream().map(Product::getNombre).sorted().collect(Collectors.toList());
        productoFinal.setItems(FXCollections.observableArrayList(nombres));
        new AutoCompleteCombo<>(cast(productoFinal), nombres, s -> s); // truco para reutilizar clase genérica

        // Materiales: ComboBox<Product> con autocompletar
        ObservableList<Product> productos = FXCollections.observableArrayList(inventory.list());
        material.setItems(productos);
        material.setConverter(new StringConverter<Product>() {
            @Override public String toString(Product p) { return p == null ? "" : p.getNombre() + "  (" + p.getSku() + ")"; }
            @Override public Product fromString(String s) { return productos.stream().filter(p -> (p.getNombre()+" ("+p.getSku()+")").equalsIgnoreCase(s)).findFirst().orElse(null); }
        });
        new AutoCompleteCombo<>(material, productos, p -> p.getNombre()+" "+p.getSku());
        medida.getSelectionModel().selectFirst();

        int r=0;
        form.addRow(r++, new Label("Producto final"), productoFinal);
        form.addRow(r++, new Label("Piezas por lote"), piezasPorLote);
        form.addRow(r++, new Label("Veces a fabricar"), veces);
        form.addRow(r++, new Label("Ganancia deseada"), ganancia);
        form.addRow(r++, new Label("Costo extra por lote"), extra);
        form.add(new Separator(), 0, r++, 2, 1);
        form.addRow(r++, new Label("Material"), material);
        form.addRow(r++, new Label("Medida"), medida);
        form.addRow(r++, new Label("Cantidad"), cantidad);

        Button addInsumo = new Button("Agregar insumo"); addInsumo.getStyleClass().add("primary");
        form.add(addInsumo, 1, r++);
        Button fabricar = new Button("Fabricar"); fabricar.getStyleClass().add("success");
        form.add(fabricar, 1, r++);

        setLeft(left); setCenter(form); BorderPane.setMargin(form, new Insets(0,0,0,12));

        // Eventos
        addInsumo.setOnAction(e -> agregarInsumo());
        fabricar.setOnAction(e -> fabricar());

        // Recalcular KPIs cada cambio
        piezasPorLote.valueProperty().addListener((o,old,v)->recalc());
        veces.valueProperty().addListener((o,old,v)->recalc());
        ganancia.valueProperty().addListener((o,old,v)->recalc());
        extra.valueProperty().addListener((o,old,v)->recalc());
        cantidad.valueProperty().addListener((o,old,v)->recalc());

        recalc();
    }

    // ===== util =====
    private <T> ComboBox<T> cast(ComboBox<?> cb){ @SuppressWarnings("unchecked") ComboBox<T> c = (ComboBox<T>) cb; return c; }

    private VBox kpi(String title, Label value){
        Label t = new Label(title); t.getStyleClass().add("subtle");
        value.setStyle("-fx-font-size: 22px; -fx-font-weight: 900;");
        VBox box = new VBox(4, t, value); VBox wrap = new VBox(box);
        wrap.getStyleClass().add("card"); wrap.setPadding(new Insets(12)); wrap.setPrefWidth(220);
        return wrap;
    }

    private void agregarInsumo(){
        Product p = material.getValue();
        if (p == null){ ((VBox)getLeft()).getChildren().add(0, AlertBanner.warn("Selecciona un material")); return; }
        double cant = cantidad.getValue();
        if (cant <= 0){ ((VBox)getLeft()).getChildren().add(0, AlertBanner.warn("Cantidad inválida")); return; }
        String med = medida.getValue();

        Insumo in = new Insumo(p, med, cant);
        insumos.add(in);
        renderChips();
        recalc();
        // limpiar edición rápida
        material.getSelectionModel().clearSelection(); material.getEditor().clear();
        cantidad.getValueFactory().setValue(1.0);
        medida.getSelectionModel().selectFirst();
    }

    private void renderChips(){
        chips.getChildren().clear();
        for (Insumo i : insumos){
            HBox chip = new HBox(8, new Label(i.texto()), new Button("❌"));
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("card");
            ((Button)chip.getChildren().get(1)).setOnAction(e -> { insumos.remove(i); renderChips(); recalc(); });
            chips.getChildren().add(chip);
        }
    }

    private void recalc(){
        double costoMat = insumos.stream().mapToDouble(Insumo::costo).sum();
        int piezas = Math.max(1, piezasPorLote.getValue());
        double costoUnit = (costoMat + extra.getValue()) / piezas;
        double precioSug = costoUnit * (1.0 + ganancia.getValue());
        kCostoMat.setText(String.format("$%.2f", costoMat));
        kCostoUnit.setText(String.format("$%.2f", costoUnit));
        kPrecioSug.setText(String.format("$%.2f", precioSug));
    }

    private void fabricar(){
        String nombreFinal = productoFinal.getEditor().getText().trim();
        if (nombreFinal.isEmpty()){ ((VBox)getLeft()).getChildren().add(0, AlertBanner.warn("Escribe el producto final")); return; }
        int piezas = Math.max(1, piezasPorLote.getValue());
        double vecesFab = Math.max(0.1, veces.getValue());

        // Validar stock de insumos
        for (Insumo i : insumos){
            double req = i.factor() * i.cantidad * vecesFab;
            Product inv = inventory.findBySku(i.prod.getSku());
            if (inv == null || inv.getStock() < req){
                ((VBox)getLeft()).getChildren().add(0, AlertBanner.danger("Stock insuficiente: " + i.prod.getNombre()));
                return;
            }
        }

        // Descontar insumos
        for (Insumo i : insumos){
            double req = i.factor() * i.cantidad * vecesFab;
            inventory.adjustStock(i.prod.getSku(), -req);
        }

        // Crear/actualizar producto final
        Product exist = inventory.search(nombreFinal).stream()
                .filter(p -> p.getNombre().equalsIgnoreCase(nombreFinal)).findFirst().orElse(null);

        if (exist == null){
            // sugerir creación
            Dialog<Product> d = new Dialog<>();
            d.setTitle("Crear producto final");
            d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8); grid.setPadding(new Insets(10));
            TextField sku = new TextField("PF-" + Math.abs(nombreFinal.hashCode() % 100000));
            TextField cat = new TextField("Producción");
            TextField uni = new TextField("pza");
            TextField cont = new TextField("0");
            TextField precio = new TextField(kPrecioSug.getText().replace("$",""));
            grid.addRow(0, new Label("SKU"), sku);
            grid.addRow(1, new Label("Categoría"), cat);
            grid.addRow(2, new Label("Unidad"), uni);
            grid.addRow(3, new Label("Contenido"), cont);
            grid.addRow(4, new Label("Precio base"), precio);
            d.getDialogPane().setContent(grid);
            d.setResultConverter(bt -> {
                if (bt != ButtonType.OK) return null;
                try {
                    return new Product(sku.getText().trim(), nombreFinal, cat.getText().trim(), uni.getText().trim(),
                            Double.parseDouble(cont.getText().trim()),
                            Double.parseDouble(precio.getText().trim()), 0.0);
                } catch(Exception ex){ return null; }
            });
            Optional<Product> res = d.showAndWait();
            if (res.isEmpty()){ ((VBox)getLeft()).getChildren().add(0, AlertBanner.warn("Cancelado")); return; }
            exist = res.get();
            inventory.upsert(exist);
        }

        // Sumar stock final
        double incremento = piezas * vecesFab;
        inventory.adjustStock(exist.getSku(), incremento);

        bus.publish(EventBus.Topic.INVENTORY_CHANGED, "PRODUCTION");
        ((VBox)getLeft()).getChildren().add(0, AlertBanner.success("Producción registrada"));
        insumos.clear(); renderChips(); recalc();
    }

    // ===== Insumo =====
    private static class Insumo {
        final Product prod; final String medida; final double cantidad;
        Insumo(Product prod, String medida, double cantidad){ this.prod = prod; this.medida = medida; this.cantidad = cantidad; }
        double factor(){ return ("Paquete/Rollo".equals(medida) && prod.getContenido() > 0) ? prod.getContenido() : 1.0; }
        double costo(){ return prod.getPrecio() * cantidad * factor(); }
        String texto(){ return prod.getNombre() + " x " + cantidad + " (" + medida + ")"; }
    }
}
EOF

# ========= MainView: agregar botón de Configuración (Usuarios/Roles) =========
cat <<'EOF' > src/main/java/com/papeleria/pos/views/MainView.java
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

    public MainView(SessionService session, InventoryService inventoryService, SalesService salesService,
                    ProductionService productionService, EventBus bus){
        this.session = session; this.inventoryService = inventoryService;
        this.salesService = salesService; this.productionService = productionService; this.bus = bus;
        this.storage = new StorageService(java.nio.file.Path.of("").toAbsolutePath());

        setPadding(new Insets(16));
        sidebar.getStyleClass().add("sidebar");

        Button bSales  = mk("🛒");
        Button bInv    = mk("📦");
        Button bProd   = mk("🛠️");
        Button bReport = mk("📊");
        Button bUsers  = mk("⚙️");
        Button bLogout = mk("⏻");
        bSales.getStyleClass().add("active");
        bLogout.setOnAction(e -> { session.logout(); System.exit(0); });

        sidebar.getChildren().addAll(bSales, bInv, bProd, bReport, bUsers, bLogout);
        setLeft(sidebar);

        inventoryView = new InventoryView(session, inventoryService, bus);
        salesView     = new SalesView(session, salesService, inventoryService, bus);
        productionView= new ProductionView(session, productionService, inventoryService, bus);
        reportsView   = new ReportsView(inventoryService, storage);
        usersView     = new UsersView(new UserService(storage));

        setContent(salesView); // inicia en POS

        bSales.setOnAction(e -> { activate(bSales, bInv, bProd, bReport, bUsers); setContent(salesView); });
        bInv.setOnAction(e -> { activate(bInv, bSales, bProd, bReport, bUsers); setContent(inventoryView); });
        bProd.setOnAction(e -> { activate(bProd, bSales, bInv, bReport, bUsers); setContent(productionView); });
        bReport.setOnAction(e -> { activate(bReport, bSales, bInv, bProd, bUsers); setContent(reportsView); });
        bUsers.setOnAction(e -> { activate(bUsers, bSales, bInv, bProd, bReport); setContent(usersView); });
    }

    private Button mk(String icon){
        Button b = new Button(icon);
        b.getStyleClass().add("nav-btn");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setPrefHeight(52);
        return b;
    }
    private void activate(Button on, Button... off){
        on.getStyleClass().add("active");
        for (Button x : off) x.getStyleClass().remove("active");
    }
    private void setContent(Node n){ center.getChildren().setAll(new VBox(){{
        getStyleClass().add("page"); getChildren().add(n);
    }}); setCenter(center); }
}
EOF

echo ">> Compilando..."
mvn -q clean package

echo
echo "=============================================="
echo "Usuarios/Roles y Producción con autocompletar listos."
echo "Ejecuta:"
echo "mvn -q javafx:run"
echo "=============================================="

