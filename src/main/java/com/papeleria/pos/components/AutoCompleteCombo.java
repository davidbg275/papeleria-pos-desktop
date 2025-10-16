package com.papeleria.pos.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Autocompletado seguro para ComboBox editable (evita reentradas/loops). */
public class AutoCompleteCombo<T> {
    private final ComboBox<T> combo;
    private final List<T> master;               // lista base inmutable
    private final Function<T,String> toText;
    private volatile boolean updating = false;  // guardia anti-reentrada

    public AutoCompleteCombo(ComboBox<T> combo, List<T> items, Function<T,String> toText) {
        this.combo = combo;
        this.master = new ArrayList<>(items);
        this.toText = toText;

        combo.setEditable(true);
        combo.setItems(FXCollections.observableArrayList(master));

        // Conversor consistente con el texto mostrado
        combo.setConverter(new StringConverter<T>() {
            @Override public String toString(T obj) { return obj == null ? "" : toText.apply(obj); }
            @Override public T fromString(String s) {
                if (s == null) return null;
                String needle = s.trim();
                return master.stream()
                        .filter(it -> toText.apply(it).equalsIgnoreCase(needle))
                        .findFirst()
                        .orElse(null);
            }
        });

        // Filtrado en tiempo real
        combo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            if (updating) return;
            updating = true;

            String q = newText == null ? "" : newText.trim().toLowerCase();
            List<T> filtered = q.isEmpty()
                    ? master
                    : master.stream()
                            .filter(it -> toText.apply(it).toLowerCase().contains(q))
                            .collect(Collectors.toList());

            // Preservar seleccionado si aún está
            T selected = combo.getSelectionModel().getSelectedItem();

            // Evitar trabajo si la lista ya coincide (opcional)
            ObservableList<T> current = combo.getItems();
            if (!(current.size() == filtered.size() && current.containsAll(filtered))) {
                combo.getItems().setAll(filtered);
            }

            if (selected != null && filtered.contains(selected)) {
                combo.getSelectionModel().select(selected);
            } else {
                combo.getSelectionModel().clearSelection();
            }

            // Mostrar popup si hay resultados y hay query
            if (!filtered.isEmpty() && !q.isEmpty() && !combo.isShowing()) {
                combo.show();
            }

            // Restaurar texto/caret (tras los cambios de items)
            int caret = combo.getEditor().getCaretPosition();
            Platform.runLater(() -> {
                combo.getEditor().setText(newText);
                combo.getEditor().positionCaret(Math.min(caret, combo.getEditor().getText().length()));
            });

            updating = false;
        });

        // Al perder foco: fijar value si el texto coincide exactamente con un item
        combo.getEditor().focusedProperty().addListener((o, was, now) -> {
            if (now) return;
            if (updating) return;
            updating = true;
            String txt = combo.getEditor().getText();
            if (txt != null) {
                T exact = master.stream()
                        .filter(it -> toText.apply(it).equalsIgnoreCase(txt.trim()))
                        .findFirst()
                        .orElse(null);
                if (exact != null) combo.setValue(exact);  // aquí sí es seguro
            }
            updating = false;
        });
    }
}
