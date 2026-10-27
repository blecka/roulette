package bg.roulette.gui;

import bg.roulette.Roulette;
import bg.roulette.config.ConfigManager;
import bg.roulette.model.RouletteTable;
import bg.roulette.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RouletteGUI {

    private final Roulette plugin;

    // 54 Slot Inventory Grid:
    // Slots 0 to 35: Numbers 1 through 36 placed in Row 0 through Row 3 (slots 0..35)
    // Row 4 (Slots 36..44): Filler glass panes separating numbers from action buttons
    // Row 5 (Slots 48, 49, 50): Red Concrete (48), Green 0 Lime Concrete (49), Black Concrete (50)
    private static final int[] NUMBER_SLOTS = new int[37];

    static {
        NUMBER_SLOTS[0] = 49; // Green 0 on bottom row center
        for (int i = 1; i <= 36; i++) {
            NUMBER_SLOTS[i] = i - 1; // Numbers 1..36 occupy slots 0..35 starting on top row
        }
    }

    public RouletteGUI(Roulette plugin) {
        this.plugin = plugin;
    }

    public void openGUI(Player player, RouletteTable table) {
        ConfigManager config = plugin.getConfigManager();
        FileConfiguration guiCfg = config.getGui();

        String title = ColorUtil.color(guiCfg.getString("title", "&8🎲 Рулетка - Направи Залог"));
        int size = guiCfg.getInt("size", 54);

        RouletteHolder holder = new RouletteHolder(table);
        Inventory inv = Bukkit.createInventory(holder, size, title);

        // Fill background with glass panes
        String fillerMatName = guiCfg.getString("items.filler.material", "BLACK_STAINED_GLASS_PANE");
        Material fillerMat = Material.matchMaterial(fillerMatName);
        if (fillerMat == null) fillerMat = Material.BLACK_STAINED_GLASS_PANE;

        ItemStack fillerItem = new ItemStack(fillerMat);
        ItemMeta fillerMeta = fillerItem.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(ColorUtil.component(" "));
            fillerItem.setItemMeta(fillerMeta);
        }

        for (int i = 0; i < size; i++) {
            inv.setItem(i, fillerItem);
        }

        // Setup Numbers 1 - 36 starting on Row 0 (slots 0..35)
        for (int i = 1; i <= 36; i++) {
            int slot = NUMBER_SLOTS[i];
            Material mat;
            String colorName;
            double multiplier = config.getConfig().getDouble("payouts.number", 24.0);

            if (plugin.getRouletteManager().isRed(i)) {
                mat = Material.RED_WOOL;
                colorName = config.getRawMessageString("bet-target-red");
            } else {
                mat = Material.BLACK_WOOL;
                colorName = config.getRawMessageString("bet-target-black");
            }

            ItemStack numItem = new ItemStack(mat);
            ItemMeta numMeta = numItem.getItemMeta();
            if (numMeta != null) {
                numMeta.displayName(ColorUtil.component("&eЧисло " + i + " (" + colorName + "&e)"));
                List<String> lore = new ArrayList<>();
                lore.add(ColorUtil.color("&7Изплащане: &a" + multiplier + "x"));
                lore.add(ColorUtil.color("&7Кликнете, за да заложите на число " + i));
                numMeta.setLore(lore);
                numItem.setItemMeta(numMeta);
            }
            inv.setItem(slot, numItem);
        }

        // Setup Bottom Row Action Buttons (Red 48, Green 0 49, Black 50)
        setupSpecialButton(inv, guiCfg, "red-button", 48, Material.RED_CONCRETE, "&c&lЧЕРВЕНО");
        setupSpecialButton(inv, guiCfg, "green-button", 49, Material.LIME_CONCRETE, "&a&lЗЕЛЕНО (0)");
        setupSpecialButton(inv, guiCfg, "black-button", 50, Material.BLACK_CONCRETE, "&8&lЧЕРНО");

        player.openInventory(inv);
    }

    private void setupSpecialButton(Inventory inv, FileConfiguration guiCfg, String key, int defaultSlot, Material defaultMat, String defaultName) {
        int slot = guiCfg.getInt("items." + key + ".slot", defaultSlot);
        String matName = guiCfg.getString("items." + key + ".material", defaultMat.name());
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = defaultMat;

        String name = guiCfg.getString("items." + key + ".name", defaultName);
        List<String> rawLore = guiCfg.getStringList("items." + key + ".lore");

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.component(name));
            List<String> lore = new ArrayList<>();
            for (String l : rawLore) {
                lore.add(ColorUtil.color(l));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    public static int getNumberFromSlot(int slot) {
        for (int i = 1; i <= 36; i++) {
            if (NUMBER_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
