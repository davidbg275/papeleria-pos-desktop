package com.papeleria.pos.components;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Autocompletado simple para ComboBox editable.
 * Reglas:
 * - NO confirma con ESPACIO.
 * - Sólo confirma con ENTER o click del mouse.
 * - BACKSPACE/DELETE funcionan normal y no fuerzan selección.
 * - Si se borra todo, se limpia la selección.
 */
public class AutoCompleteCombo<T> {

    private final ComboBox<T> combo;
    private final ObservableList<T> master;
    private final Function<T, String> toText;
    private boolean programmaticChange = false;

    public AutoCompleteCombo(ComboBox<T> combo, ObservableList<T> items, Function<T, String> toText) {
        this.combo = combo;
        this.master = FXCollections.observableArrayList(items);
        this.toText = toText;

        combo.setItems(FXCollections.observableArrayList(master));
        combo.setEditable(true);
        combo.setVisibleRowCount(10);

        // Converter estable
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

        // Celdas con texto seguro
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
        ChangeListener<String> textListener = (obs, old, val) -> {
            if (programmaticChange)
                return;
            String q = (val == null ? "" : val).trim().toLowerCase();
            if (q.isEmpty()) {
                combo.getItems().setAll(master);
                combo.getSelectionModel().clearSelection();
                return;
            }
            var filtered = master.stream()
                    .filter(it -> AutoCompleteCombo.this.toText.apply(it).toLowerCase().contains(q))
                    .collect(Collectors.toList());
            combo.getItems().setAll(filtered);
            combo.show();
        };
        combo.getEditor().textProperty().addListener(textListener);

        // Teclado: ENTER confirma, ESPACIO no confirma
        combo.getEditor().setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (!combo.getItems().isEmpty()) {
                    // si hay coincidencia exacta usa esa, si no la primera visible
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
            } else if (e.getCode() == KeyCode.SPACE) {
                // Sólo inserta espacio en el editor, no confirma nada.
                // No se consume el evento.
            } else if (e.getCode() == KeyCode.ESCAPE) {
                combo.hide();
            }
        });

        // Click confirma
        combo.setOnMouseClicked(ev -> combo.show());
        combo.setOnAction(ev -> {
            if (programmaticChange)
                return;
            // acción normal cuando el usuario hace click
            T v = combo.getSelectionModel().getSelectedItem();
            if (v != null) {
                programmaticChange = true;
                combo.getEditor().setText(toText.apply(v));
                combo.getEditor().positionCaret(combo.getEditor().getText().length());
                programmaticChange = false;
            }
        });

        // Sincronizar al cambiar value externamente
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
}
