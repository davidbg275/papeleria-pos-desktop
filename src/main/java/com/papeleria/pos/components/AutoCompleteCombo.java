package com.papeleria.pos.components;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Autocomplete NO intrusivo para ComboBox editable.
 * - Filtra mientras escribes. No reescribe el texto ni mueve el caret.
 * - Acepta sugerencia solo con TAB/ENTER o seleccionando en el popup.
 * - Permite borrar normalmente (Backspace/Delete) sin re-autocompletar.
 * - Mantiene items completos (no bloquea edición).
 */
public class AutoCompleteCombo<T> {

    private final ComboBox<T> combo;
    private final Function<T, String> toText;
    private final ObservableList<T> master;
    private boolean updating = false; // evita recursión

    public AutoCompleteCombo(ComboBox<T> combo, List<T> items, Function<T, String> toText) {
        this.combo = combo;
        this.toText = toText;
        this.master = FXCollections.observableArrayList(items);
        init();
    }

    private void init() {
        combo.setEditable(true);
        combo.setItems(master);

        ensureEditorLTR();
        final TextField ed = combo.getEditor();

        // Filtrar mientras escribe (no tocar el texto del editor)
        ed.textProperty().addListener((obs, old, now) -> {
            if (updating)
                return;
            filter(now);
        });

        // Teclas: aceptar con TAB/ENTER; limpiar selección en cualquier edición
        ed.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB || e.getCode() == KeyCode.ENTER) {
                T first = firstStartsWith(ed.getText());
                if (first != null) {
                    accept(first);
                    e.consume();
                }
            } else {
                // Cualquier tecla de edición rompe la selección para que puedas borrar o seguir
                // escribiendo
                combo.getSelectionModel().clearSelection();
            }

            if (e.getCode() == KeyCode.DOWN) {
                if (!combo.isShowing())
                    combo.show();
            }
        });

        // Al seleccionar desde la lista, reflejar en el editor
        combo.getSelectionModel().selectedItemProperty().addListener((o, a, v) -> {
            if (updating)
                return;
            if (v == null)
                return;
            accept(v);
        });
    }

    private void ensureEditorLTR() {
        TextField ed = combo.getEditor();
        if (ed != null) {
            ed.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            return;
        }
        combo.skinProperty().addListener((o, a, s) -> {
            TextField e = combo.getEditor();
            if (e != null)
                e.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        });
    }

    private void filter(String text) {
        String p = norm(text);
        if (p.isEmpty()) {
            updating = true;
            combo.setItems(master);
            combo.getSelectionModel().clearSelection();
            updating = false;
            combo.hide();
            return;
        }

        ObservableList<T> filtered = FXCollections.observableArrayList();
        for (T t : master) {
            String s = norm(textOf(t));
            if (s.startsWith(p))
                filtered.add(t);
        }

        updating = true;
        combo.setItems(filtered.isEmpty() ? FXCollections.observableArrayList() : filtered);
        updating = false;

        if (!filtered.isEmpty()) {
            if (!combo.isShowing())
                combo.show();
        } else {
            combo.hide();
        }
    }

    private void accept(T value) {
        String s = textOf(value);
        updating = true;
        combo.getEditor().setText(s);
        combo.getEditor().positionCaret(s.length());
        combo.getEditor().deselect();
        combo.getSelectionModel().select(value);
        // Mantener catálogo completo para permitir seguir editando o borrar
        combo.setItems(master);
        updating = false;
        Platform.runLater(combo::hide);
    }

    private T firstStartsWith(String prefix) {
        String p = norm(prefix);
        if (p.isEmpty())
            return null;
        for (T t : master) {
            if (norm(textOf(t)).startsWith(p))
                return t;
        }
        return null;
    }

    private String textOf(T t) {
        if (t == null)
            return "";
        String s = toText == null ? Objects.toString(t, "") : toText.apply(t);
        return s == null ? "" : s;
    }

    private String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
    }
}
