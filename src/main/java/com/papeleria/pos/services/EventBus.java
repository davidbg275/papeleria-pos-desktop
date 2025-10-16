package com.papeleria.pos.services;

import java.util.*;
import java.util.function.Consumer;

public class EventBus {

    public enum Topic {
        INVENTORY_CHANGED,
        SALES_CHANGED,
        PRODUCTION_CHANGED
    }

    private final Map<Topic, List<Consumer<Object>>> subscribers = new EnumMap<>(Topic.class);

    public EventBus() {
        for (Topic t : Topic.values()) {
            subscribers.put(t, new ArrayList<>());
        }
    }

    public void subscribe(Topic topic, Consumer<Object> handler){
        subscribers.get(topic).add(handler);
    }

    public void publish(Topic topic, Object payload){
        for (Consumer<Object> h : subscribers.get(topic)){
            try { h.accept(payload); } catch (Exception ignored) {}
        }
    }
}
