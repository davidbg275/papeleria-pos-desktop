package com.papeleria.pos.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Autocompletado para ComboBox editable con refreshData(...).
 * - ENTER confirma, ESPACIO no confirma.
 * - Borrar limpia selección.
 */
public class AutoCompleteCombo<T> {

    private ComboBox<T> combo; // no final
    private ObservableList<T> master; // no final
    private final Function<T, String> toText;
    private boolean programmaticChange = false;

    public AutoCompleteCombo(ComboBox<T> combo, ObservableList<T> items, Function<T, String> toText) {
        this.combo = combo;
        this.master = items == null ? FXCollections.observableArrayList()
                : FXCollections.observableArrayList(items);
        this.toText = toText;

        combo.setItems(FXCollections.observableArrayList(master));
        combo.setEditable(true);
        combo.setVisibleRowCount(10);

        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(T object) {
                return object == null ? "" : AutoCompleteCombo.this.toText.apply(object);
            }

            @Override
            public T fromString(String string) {
                return combo.getValue();
            }
        });

        Callback<ListView<T>, ListCell<T>> cellFactory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : AutoCompleteCombo.this.toText.apply(item));
            }
        };
        combo.setCellFactory(cellFactory);
        combo.setButtonCell(cellFactory.call(null));

        // Filtro mientras escribe
        combo.getEditor().textProperty().addListener((obs, old, val) -> {
            if (programmaticChange)
                return;
            String q = (val == null ? "" : val).trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty()) {
                combo.getItems().setAll(master);
                combo.getSelectionModel().clearSelection();
                combo.hide();
                return;
            }
            var filtered = master.stream()
                    .filter(it -> safeText(it).contains(q))
                    .collect(Collectors.toList());
            combo.getItems().setAll(filtered);
            combo.show();
        });

        // Teclado
        combo.getEditor().setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (!combo.getItems().isEmpty()) {
                    T exact = combo.getItems().stream()
                            .filter(it -> Objects.equals(toText.apply(it), combo.getEditor().getText()))
                            .findFirst().orElse(null);
                    if (exact == null)
                        exact = combo.getItems().get(0);
                    final T pick = exact;
                    programmaticChange = true;
                    combo.setValue(pick);
                    combo.getEditor().setText(toText.apply(pick));
                    combo.getEditor().positionCaret(combo.getEditor().getText().length());
                    programmaticChange = false;
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                combo.hide();
            }
        });

        // Click confirma
        combo.setOnAction(ev -> {
            if (programmaticChange)
                return;
            T v = combo.getSelectionModel().getSelectedItem();
            if (v != null) {
                programmaticChange = true;
                combo.getEditor().setText(toText.apply(v));
                combo.getEditor().positionCaret(combo.getEditor().getText().length());
                programmaticChange = false;
            }
        });

        // Sincroniza value -> editor
        combo.valueProperty().addListener((o, a, v) -> {
            programmaticChange = true;
            combo.getEditor().setText(v == null ? "" : toText.apply(v));
            combo.getEditor().positionCaret(combo.getEditor().getText().length());
            programmaticChange = false;
        });

        // Inicial
        Platform.runLater(() -> {
            combo.getEditor().setText("");
            combo.getSelectionModel().clearSelection();
        });
    }

    /** Actualiza el catálogo sin recrear el componente ni duplicar listeners. */
    public void refreshData(ObservableList<T> newData) {
        if (newData == null)
            newData = FXCollections.observableArrayList();
        this.master = FXCollections.observableArrayList(newData);

        String currentText = combo.getEditor().getText();
        T selected = combo.getValue();

        combo.getItems().setAll(this.master);

        if (selected != null) {
            for (T it : this.master) {
                if (Objects.equals(toText.apply(it), toText.apply(selected))) {
                    combo.setValue(it);
                    break;
                }
            }
        }

        if (currentText != null && !currentText.isBlank()) {
            int pos = combo.getEditor().getCaretPosition();
            programmaticChange = true;
            combo.getEditor().setText(currentText);
            combo.getEditor().positionCaret(Math.min(pos, currentText.length()));
            programmaticChange = false;
            // re-ejecuta filtro
            String q = currentText.trim().toLowerCase(Locale.ROOT);
            var filtered = this.master.stream()
                    .filter(it -> safeText(it).contains(q))
                    .collect(Collectors.toList());
            combo.getItems().setAll(filtered);
            if (!filtered.isEmpty())
                combo.show();
        } else {
            combo.hide();
        }
    }

    public void refreshData(List<T> newData) {
        refreshData(newData == null ? FXCollections.observableArrayList()
                : FXCollections.observableArrayList(newData));
    }

    private String safeText(T it) {
        if (it == null)
            return "";
        String s = toText == null ? String.valueOf(it) : toText.apply(it);
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
