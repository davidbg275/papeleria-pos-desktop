package com.papeleria.pos.components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

public class AlertBanner extends HBox {
    private final Label emoji = new Label();
    private final Label text = new Label();

    public AlertBanner(String styleClass, String emojiText, String message){
        getStyleClass().addAll("alert", styleClass);
        emoji.getStyleClass().add("emoji");
        emoji.setText(emojiText);
        text.setText(message);
        setSpacing(6);
        setPadding(new Insets(8,10,8,10));
        getChildren().addAll(emoji, text);
    }

    public static AlertBanner info(String msg){ return new AlertBanner("alert-info","ℹ️", msg); }
    public static AlertBanner warn(String msg){ return new AlertBanner("alert-warn","⚠️", msg); }
    public static AlertBanner danger(String msg){ return new AlertBanner("alert-danger","⛔", msg); }
    public static AlertBanner success(String msg){ return new AlertBanner("alert-success","✅", msg); }
}
