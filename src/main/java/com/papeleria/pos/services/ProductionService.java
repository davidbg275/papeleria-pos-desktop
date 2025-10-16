package com.papeleria.pos.services;

public class ProductionService {
    private final InventoryService inventory;
    private final EventBus bus;

    public ProductionService(InventoryService inventory, EventBus bus) {
        this.inventory = inventory;
        this.bus = bus;
    }

    public InventoryService getInventory() { return inventory; }
    public EventBus getBus() { return bus; }
}
