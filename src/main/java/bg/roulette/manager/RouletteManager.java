package bg.roulette.manager;

import bg.roulette.Roulette;
import bg.roulette.config.ConfigManager;
import bg.roulette.model.Bet;
import bg.roulette.model.RouletteTable;
import bg.roulette.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RouletteManager {

    private final Roulette plugin;
    private final Map<String, RouletteTable> tables = new HashMap<>();

    // European Roulette Numbers in order on wheel
    private static final int[] WHEEL_NUMBERS = {
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    };

    public RouletteManager(Roulette plugin) {
        this.plugin = plugin;
    }

    public void loadTables() {
        for (RouletteTable table : tables.values()) {
            refundBets(table, "refund-message");
            cleanupTableEntities(table);
        }
        tables.clear();

        ConfigManager config = plugin.getConfigManager();
        ConfigurationSection sec = config.getTables().getConfigurationSection("tables");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            String worldName = sec.getString(key + ".world");
            double x = sec.getDouble(key + ".x");
            double y = sec.getDouble(key + ".y");
            double z = sec.getDouble(key + ".z");
            int size = sec.getInt(key + ".size", 3);

            if (worldName != null) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world, x, y, z);
                    RouletteTable table = new RouletteTable(key, loc, size);
                    tables.put(key.toLowerCase(), table);
                    buildPlatform(table);
                    plugin.getHologramManager().spawnOrUpdateHologram(table);
                }
            }
        }
    }

    public void saveTables() {
        ConfigManager config = plugin.getConfigManager();
        config.getTables().set("tables", null); // clear existing
        for (RouletteTable table : tables.values()) {
            String path = "tables." + table.getName();
            config.getTables().set(path + ".world", table.getLocation().getWorld().getName());
            config.getTables().set(path + ".x", table.getLocation().getX());
            config.getTables().set(path + ".y", table.getLocation().getY());
            config.getTables().set(path + ".z", table.getLocation().getZ());
            config.getTables().set(path + ".size", table.getSize());
        }
        config.saveTables();
    }

    public RouletteTable createTable(String name, Location location, int size) {
        RouletteTable table = new RouletteTable(name, location, size);
        tables.put(name.toLowerCase(), table);
        buildPlatform(table);
        plugin.getHologramManager().spawnOrUpdateHologram(table);
        saveTables();
        return table;
    }

    public boolean deleteTable(String name) {
        RouletteTable table = tables.remove(name.toLowerCase());
        if (table != null) {
            refundBets(table, "refund-message");
            removePlatform(table);
            cleanupTableEntities(table);
            saveTables();
            return true;
        }
        return false;
    }

    public void resizeTable(RouletteTable table, int newSize) {
        removePlatform(table);
        table.setSize(newSize);
        buildPlatform(table);
        plugin.getHologramManager().spawnOrUpdateHologram(table);
        saveTables();
    }

    public RouletteTable getTable(String name) {
        return tables.get(name.toLowerCase());
    }

    public Collection<RouletteTable> getAllTables() {
        return tables.values();
    }

    public RouletteTable getNearestTable(Location loc, double maxDistance) {
        RouletteTable nearest = null;
        double minDistanceSq = maxDistance * maxDistance;

        for (RouletteTable table : tables.values()) {
            if (table.getLocation().getWorld().equals(loc.getWorld())) {
                double distSq = table.getLocation().distanceSquared(loc);
                if (distSq <= minDistanceSq) {
                    minDistanceSq = distSq;
                    nearest = table;
                }
            }
        }
        return nearest;
    }

    public boolean isTableBlock(Block block) {
        return getTableAtBlock(block) != null;
    }

    public RouletteTable getTableAtBlock(Block block) {
        for (RouletteTable table : tables.values()) {
            // Check dynamically placed fence/platform blocks first
            for (Location loc : table.getPlacedBlocks()) {
                if (loc.getBlockX() == block.getX() &&
                        loc.getBlockY() == block.getY() &&
                        loc.getBlockZ() == block.getZ() &&
                        loc.getWorld().equals(block.getWorld())) {
                    return table;
                }
            }

            // Also check bounding box around center location based on table size
            Location center = table.getLocation();
            if (center.getWorld().equals(block.getWorld())) {
                int halfSize = Math.max(1, table.getSize() / 2);
                int dx = Math.abs(block.getX() - center.getBlockX());
                int dy = Math.abs(block.getY() - center.getBlockY());
                int dz = Math.abs(block.getZ() - center.getBlockZ());

                if (dx <= halfSize && dz <= halfSize && dy <= 1) {
                    return table;
                }
            }
        }
        return null;
    }

    public void buildPlatform(RouletteTable table) {
        ConfigManager config = plugin.getConfigManager();
        boolean spawnFence = config.getConfig().getBoolean("spawn-fence", false);
        if (!spawnFence) return;

        String matName = config.getConfig().getString("spawn-fence-material", "OAK_FENCE");
        Material fenceMat = Material.matchMaterial(matName);
        if (fenceMat == null) fenceMat = Material.OAK_FENCE;

        table.getPlacedBlocks().clear();
        Location center = table.getLocation().getBlock().getLocation();
        int halfSize = table.getSize() / 2;

        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                // Place fence along perimeter
                if (x == -halfSize || x == halfSize || z == -halfSize || z == halfSize) {
                    Block block = center.clone().add(x, 0, z).getBlock();
                    if (block.getType() == Material.AIR || block.getType().name().contains("FENCE")) {
                        block.setType(fenceMat);
                        table.getPlacedBlocks().add(block.getLocation());
                    }
                }
            }
        }
    }

    public void removePlatform(RouletteTable table) {
        for (Location loc : table.getPlacedBlocks()) {
            Block block = loc.getBlock();
            block.setType(Material.AIR);
        }
        table.getPlacedBlocks().clear();
    }

    public void startOrResetTimer(RouletteTable table) {
        ConfigManager config = plugin.getConfigManager();
        int cooldown = config.getConfig().getInt("times.cooldown-seconds", 15);
        int maxBets = config.getConfig().getInt("limits.max-bets-per-round", 10);

        table.setRemainingSeconds(cooldown);
        plugin.getHologramManager().spawnOrUpdateHologram(table);

        if (table.getActiveBets().size() >= maxBets) {
            startSpinningPhase(table);
            return;
        }

        if (table.getState() == RouletteTable.TableState.WAITING && table.getActiveTask() != null) {
            // Timer reset by new bet
            return;
        }

        table.setState(RouletteTable.TableState.WAITING);

        table.setActiveTask(new BukkitRunnable() {
            @Override
            public void run() {
                int rem = table.getRemainingSeconds() - 1;
                table.setRemainingSeconds(rem);
                plugin.getHologramManager().spawnOrUpdateHologram(table);

                if (rem <= 0) {
                    cancel();
                    startSpinningPhase(table);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L));
    }

    public void startSpinningPhase(RouletteTable table) {
        table.setState(RouletteTable.TableState.SPINNING);
        if (table.getActiveTask() != null) {
            table.getActiveTask().cancel();
        }

        ConfigManager config = plugin.getConfigManager();
        int spinSeconds = config.getConfig().getInt("times.spin-duration-seconds", 5);
        int totalTicks = spinSeconds * 20;

        int winningNumber = WHEEL_NUMBERS[ThreadLocalRandom.current().nextInt(WHEEL_NUMBERS.length)];

        table.setActiveTask(new BukkitRunnable() {
            int ticksElapsed = 0;
            int animIndex = 0;

            @Override
            public void run() {
                ticksElapsed += 2;
                animIndex = (animIndex + 1) % WHEEL_NUMBERS.length;
                int currNumber = WHEEL_NUMBERS[animIndex];

                table.setCurrentSpinNumber(currNumber);
                plugin.getHologramManager().spawnOrUpdateHologram(table);

                // Sound effect
                Location loc = table.getLocation();
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_SNARE, 1.0f, 1.2f);

                if (ticksElapsed >= totalTicks) {
                    cancel();
                    startResultPhase(table, winningNumber);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L));
    }

    public void startResultPhase(RouletteTable table, int winningNumber) {
        table.setState(RouletteTable.TableState.RESULT);
        table.setWinningNumber(winningNumber);

        plugin.getHologramManager().spawnOrUpdateHologram(table);

        Location loc = table.getLocation();
        loc.getWorld().playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        processPayouts(table, winningNumber);

        ConfigManager config = plugin.getConfigManager();
        int postDelay = config.getConfig().getInt("times.post-round-delay-seconds", 3);
        table.setActiveTask(new BukkitRunnable() {
            @Override
            public void run() {
                table.clearBets();
                table.setState(RouletteTable.TableState.WAITING);
                table.setRemainingSeconds(config.getConfig().getInt("times.cooldown-seconds", 15));
                plugin.getHologramManager().spawnOrUpdateHologram(table);
            }
        }.runTaskLater(plugin, postDelay * 20L));
    }

    private void processPayouts(RouletteTable table, int winningNumber) {
        ConfigManager config = plugin.getConfigManager();
        double broadcastMin = config.getConfig().getDouble("limits.broadcast-win-amount", 5000.0);

        double numMultiplier = config.getConfig().getDouble("payouts.number", 24.0);
        double greenMultiplier = config.getConfig().getDouble("payouts.green", 32.0);
        double redMultiplier = config.getConfig().getDouble("payouts.red", 2.0);
        double blackMultiplier = config.getConfig().getDouble("payouts.black", 2.0);

        boolean isWinRed = isRed(winningNumber);
        boolean isWinBlack = isBlack(winningNumber);
        boolean isWinGreen = (winningNumber == 0);

        for (Bet bet : table.getActiveBets().values()) {
            boolean won = false;
            double multiplier = 0.0;

            switch (bet.getTargetType()) {
                case NUMBER -> {
                    if (bet.getTargetNumber() == winningNumber) {
                        won = true;
                        multiplier = numMultiplier;
                    }
                }
                case RED -> {
                    if (isWinRed) {
                        won = true;
                        multiplier = redMultiplier;
                    }
                }
                case BLACK -> {
                    if (isWinBlack) {
                        won = true;
                        multiplier = blackMultiplier;
                    }
                }
                case GREEN -> {
                    if (isWinGreen) {
                        won = true;
                        multiplier = greenMultiplier;
                    }
                }
            }

            Player player = Bukkit.getPlayer(bet.getPlayerUuid());
            if (won) {
                double payout = bet.getAmount() * multiplier;
                plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(bet.getPlayerUuid()), payout);

                if (player != null && player.isOnline()) {
                    String msg = config.getMessageString("win-message").replace("{amount}", String.valueOf(payout));
                    player.sendMessage(msg);
                }

                if (payout >= broadcastMin) {
                    String redText = config.getRawMessageString("bet-target-red");
                    String blackText = config.getRawMessageString("bet-target-black");
                    String greenText = config.getRawMessageString("bet-target-green");
                    String numTemplate = config.getRawMessageString("bet-target-number");
                    String targetFmt = bet.getFormattedTarget(redText, blackText, greenText, numTemplate);

                    String broadcastMsg = config.getMessageString("broadcast-big-win")
                            .replace("{player}", bet.getPlayerName())
                            .replace("{amount}", String.valueOf(payout))
                            .replace("{target}", targetFmt);
                    Bukkit.broadcast(ColorUtil.component(broadcastMsg));
                }
            } else {
                if (player != null && player.isOnline()) {
                    player.sendMessage(config.getMessageString("lose-message"));
                }
            }
        }
    }

    public void refundBets(RouletteTable table, String messageKey) {
        ConfigManager config = plugin.getConfigManager();
        for (Bet bet : table.getActiveBets().values()) {
            plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(bet.getPlayerUuid()), bet.getAmount());
            Player player = Bukkit.getPlayer(bet.getPlayerUuid());
            if (player != null && player.isOnline()) {
                String msg = config.getMessageString(messageKey).replace("{amount}", String.valueOf(bet.getAmount()));
                player.sendMessage(msg);
            }
        }
        table.clearBets();
    }

    public void cleanupTableEntities(RouletteTable table) {
        if (table.getActiveTask() != null) {
            table.getActiveTask().cancel();
        }
        plugin.getHologramManager().removeHologram(table);
    }

    public void shutdown() {
        for (RouletteTable table : tables.values()) {
            refundBets(table, "refund-message");
            cleanupTableEntities(table);
        }
    }

    public boolean isRed(int number) {
        if (number <= 0 || number > 36) return false;
        return switch (number) {
            case 1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36 -> true;
            default -> false;
        };
    }

    public boolean isBlack(int number) {
        if (number <= 0 || number > 36) return false;
        return !isRed(number);
    }

    public String getNumberColorString(int number) {
        if (number == 0) return configColor("bet-target-green", "&aЗелено");
        if (isRed(number)) return configColor("bet-target-red", "&cЧервено");
        return configColor("bet-target-black", "&8Черно");
    }

    private String configColor(String key, String def) {
        return plugin.getConfigManager().getRawMessageString(key);
    }
}
