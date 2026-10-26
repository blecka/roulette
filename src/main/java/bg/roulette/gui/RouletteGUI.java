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
    // Slot 4: Green 0
    // Slots 9-44: Numbers 1-36 in neat 9-column rows (Rows 1 to 4)
    // Slots 48, 49, 50: Action buttons for Red, Green, Black
    private static final int[] NUMBER_SLOTS = new int[37];

    static {
        NUMBER_SLOTS[0] = 4; // Green 0 on top row center
        for (int i = 1; i <= 36; i++) {
            NUMBER_SLOTS[i] = 8 + i; // 1 -> 9, 2 -> 10 ... 36 -> 44
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

        // Setup Numbers 0 - 36
        for (int i = 0; i <= 36; i++) {
            int slot = NUMBER_SLOTS[i];
            Material mat;
            String colorName;
            double multiplier;

            if (i == 0) {
                mat = Material.LIME_STAINED_GLASS_PANE;
                colorName = config.getRawMessageString("bet-target-green");
                multiplier = config.getConfig().getDouble("payouts.green", 32.0);
            } else if (plugin.getRouletteManager().isRed(i)) {
                mat = Material.RED_STAINED_GLASS_PANE;
                colorName = config.getRawMessageString("bet-target-red");
                multiplier = config.getConfig().getDouble("payouts.number", 24.0);
            } else {
                mat = Material.GRAY_STAINED_GLASS_PANE;
                colorName = config.getRawMessageString("bet-target-black");
                multiplier = config.getConfig().getDouble("payouts.number", 24.0);
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

        // Setup Special Buttons (Red, Green, Black)
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
        for (int i = 0; i < NUMBER_SLOTS.length; i++) {
            if (NUMBER_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }
}
