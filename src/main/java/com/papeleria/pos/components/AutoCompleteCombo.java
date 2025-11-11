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
 * Autocomplete no intrusivo:
 * - Filtra al escribir sin tocar el editor.
 * - Solo acepta con TAB/ENTER o al seleccionar en popup.
 * - Permite borrar normalmente; SPACE NO acepta.
 */
public class AutoCompleteCombo<T> {
    private final ComboBox<T> combo;
    private final Function<T, String> toText;
    private final ObservableList<T> master;
    private boolean updating = false;

    public AutoCompleteCombo(ComboBox<T> combo, List<T> items, Function<T, String> toText) {
        this.combo = combo;
        this.master = FXCollections.observableArrayList(items);
        this.toText = toText;
        init();
    }

    private void init() {
        combo.setEditable(true);
        combo.setItems(master);
        ensureEditorLTR();

        final TextField ed = combo.getEditor();

        ed.textProperty().addListener((o, a, now) -> {
            if (updating)
                return;
            filter(now);
        });

        ed.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB || e.getCode() == KeyCode.ENTER) {
                T first = firstStartsWith(ed.getText());
                if (first != null) {
                    accept(first);
                    e.consume();
                }
            } else {
                combo.getSelectionModel().clearSelection();
                if (e.getCode() == KeyCode.DOWN && !combo.isShowing())
                    combo.show();
            }
        });

        combo.getSelectionModel().selectedItemProperty().addListener((o, a, v) -> {
            if (updating || v == null)
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
        for (T t : master)
            if (norm(textOf(t)).startsWith(p))
                filtered.add(t);
        updating = true;
        combo.setItems(filtered);
        updating = false;
        if (!filtered.isEmpty()) {
            if (!combo.isShowing())
                combo.show();
        } else
            combo.hide();
    }

    private void accept(T value) {
        String s = textOf(value);
        updating = true;
        combo.getEditor().setText(s);
        combo.getEditor().positionCaret(s.length());
        combo.getEditor().deselect();
        combo.getSelectionModel().select(value);
        combo.setItems(master);
        updating = false;
        Platform.runLater(combo::hide);
    }

    private T firstStartsWith(String prefix) {
        String p = norm(prefix);
        if (p.isEmpty())
            return null;
        for (T t : master)
            if (norm(textOf(t)).startsWith(p))
                return t;
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
