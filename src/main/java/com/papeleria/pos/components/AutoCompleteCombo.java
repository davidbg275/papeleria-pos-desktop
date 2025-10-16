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
