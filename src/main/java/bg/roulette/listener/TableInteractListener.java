package bg.roulette.listener;

import bg.roulette.Roulette;
import bg.roulette.gui.RouletteGUI;
import bg.roulette.model.RouletteTable;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class TableInteractListener implements Listener {

    private final Roulette plugin;
    private final RouletteGUI rouletteGUI;

    public TableInteractListener(Roulette plugin) {
        this.plugin = plugin;
        this.rouletteGUI = new RouletteGUI(plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        RouletteTable table = plugin.getRouletteManager().getTableAtBlock(clickedBlock);
        if (table != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();

            if (!player.hasPermission("roulette.use")) {
                player.sendMessage(plugin.getConfigManager().getMessageString("no-permission"));
                return;
            }

            rouletteGUI.openGUI(player, table);
        }
    }
}
