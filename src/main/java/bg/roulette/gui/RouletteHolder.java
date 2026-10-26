package bg.roulette.gui;

import bg.roulette.model.RouletteTable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class RouletteHolder implements InventoryHolder {

    private final RouletteTable table;

    public RouletteHolder(RouletteTable table) {
        this.table = table;
    }

    public RouletteTable getTable() {
        return table;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
