package bg.roulette.manager;

import bg.roulette.Roulette;
import bg.roulette.config.ConfigManager;
import bg.roulette.model.Bet;
import bg.roulette.model.RouletteTable;
import bg.roulette.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

public class HologramManager {

    private final Roulette plugin;

    public HologramManager(Roulette plugin) {
        this.plugin = plugin;
    }

    /**
     * Spawns or updates a TextDisplay entity for the given table.
     */
    public TextDisplay spawnOrUpdateHologram(RouletteTable table) {
        Location hologramLocation = table.getLocation().clone().add(0.5, 2.2, 0.5);

        TextDisplay hologram = table.getHologramEntity();
        if (hologram == null || !hologram.isValid()) {
            // Remove any existing stale displays nearby if any
            clearStaleHolograms(hologramLocation);

            hologram = (TextDisplay) hologramLocation.getWorld().spawnEntity(hologramLocation, EntityType.TEXT_DISPLAY);
            hologram.setBillboard(Display.Billboard.CENTER);
            hologram.setDefaultBackground(false);
            hologram.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
            table.setHologramEntity(hologram);
        } else {
            hologram.teleport(hologramLocation);
        }

        Component content = buildHologramText(table);
        hologram.text(content);
        return hologram;
    }

    public void removeHologram(RouletteTable table) {
        TextDisplay hologram = table.getHologramEntity();
        if (hologram != null && hologram.isValid()) {
            hologram.remove();
        }
        table.setHologramEntity(null);
    }

    public void clearStaleHolograms(Location loc) {
        if (loc.getWorld() == null) return;
        loc.getWorld().getNearbyEntities(loc, 1.5, 1.5, 1.5, entity -> entity instanceof TextDisplay)
                .forEach(entity -> entity.remove());
    }

    public Component buildHologramText(RouletteTable table) {
        ConfigManager config = plugin.getConfigManager();
        boolean useIntCurrency = config.getConfig().getBoolean("use-integer-currency", true);
        List<String> lines = new ArrayList<>();

        // Title
        lines.add(config.getRawMessageString("hologram.title"));
        lines.add("");

        switch (table.getState()) {
            case WAITING -> {
                if (table.getActiveBets().isEmpty()) {
                    lines.add(config.getRawMessageString("hologram.waiting-for-bets"));
                } else {
                    String waitStatus = config.getRawMessageString("hologram.waiting-status")
                            .replace("{time}", String.valueOf(table.getRemainingSeconds()));
                    lines.add(waitStatus);
                }
                lines.add("");
                lines.add(config.getRawMessageString("hologram.bets-header"));

                if (table.getActiveBets().isEmpty()) {
                    lines.add(config.getRawMessageString("hologram.no-bets"));
                } else {
                    String redText = config.getRawMessageString("bet-target-red");
                    String blackText = config.getRawMessageString("bet-target-black");
                    String greenText = config.getRawMessageString("bet-target-green");
                    String numTemplate = config.getRawMessageString("bet-target-number");

                    for (Bet bet : table.getActiveBets().values()) {
                        String targetFormatted = bet.getFormattedTarget(redText, blackText, greenText, numTemplate);
                        String formattedAmount = ColorUtil.formatMoney(bet.getAmount(), useIntCurrency);
                        String entry = config.getRawMessageString("hologram.bet-entry")
                                .replace("{player}", bet.getPlayerName())
                                .replace("{amount}", formattedAmount)
                                .replace("{target}", targetFormatted);
                        lines.add(entry);
                    }
                }
            }
            case SPINNING -> {
                lines.add(config.getRawMessageString("hologram.spinning-status"));
                int num = table.getCurrentSpinNumber();
                String colorCode = plugin.getRouletteManager().getNumberColorCode(num);
                String colorName = plugin.getRouletteManager().getNumberColorString(num);

                String animLine = config.getRawMessageString("hologram.spinning-number")
                        .replace("{number}", String.valueOf(num))
                        .replace("{color}", colorCode)
                        .replace("{color_name}", colorName);
                lines.add(animLine);
            }
            case RESULT -> {
                int winNum = table.getWinningNumber();
                String colorCode = plugin.getRouletteManager().getNumberColorCode(winNum);
                String colorName = plugin.getRouletteManager().getNumberColorString(winNum);

                String resLine = config.getRawMessageString("hologram.result-status")
                        .replace("{number}", String.valueOf(winNum))
                        .replace("{color}", colorCode)
                        .replace("{color_name}", colorName);
                lines.add(resLine);
            }
        }

        StringBuilder fullText = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            fullText.append(lines.get(i));
            if (i < lines.size() - 1) {
                fullText.append("\n");
            }
        }

        return ColorUtil.component(fullText.toString());
    }
}
