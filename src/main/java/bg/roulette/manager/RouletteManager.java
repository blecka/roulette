package bg.roulette.manager;

import bg.roulette.Roulette;
import bg.roulette.config.ConfigManager;
import bg.roulette.model.Bet;
import bg.roulette.model.RouletteTable;
import bg.roulette.util.ColorUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

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
                    spawnWheelBlockDisplays(table, 0.0);
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
        Location centerBlockLoc = location.getBlock().getLocation();
        RouletteTable table = new RouletteTable(name, centerBlockLoc, size);
        tables.put(name.toLowerCase(), table);
        buildPlatform(table);
        spawnWheelBlockDisplays(table, 0.0);
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
        cleanupTableEntities(table);
        table.setSize(newSize);
        buildPlatform(table);
        spawnWheelBlockDisplays(table, 0.0);
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
            // Check placed central base block
            for (Location loc : table.getPlacedBlocks()) {
                if (loc.getBlockX() == block.getX() &&
                        loc.getBlockY() == block.getY() &&
                        loc.getBlockZ() == block.getZ() &&
                        loc.getWorld().equals(block.getWorld())) {
                    return table;
                }
            }

            // Also check central table location area
            Location center = table.getLocation();
            if (center.getWorld().equals(block.getWorld())) {
                if (Math.abs(block.getX() - center.getBlockX()) <= 1 &&
                        Math.abs(block.getZ() - center.getBlockZ()) <= 1 &&
                        Math.abs(block.getY() - center.getBlockY()) <= 1) {
                    return table;
                }
            }
        }
        return null;
    }

    public void buildPlatform(RouletteTable table) {
        ConfigManager config = plugin.getConfigManager();
        boolean spawnFence = config.getConfig().getBoolean("spawn-fence", true);
        if (!spawnFence) return;

        String matName = config.getConfig().getString("spawn-fence-material", "VERDANT_FROGLIGHT");
        Material fenceMat = Material.matchMaterial(matName);
        if (fenceMat == null) fenceMat = Material.VERDANT_FROGLIGHT;

        table.getPlacedBlocks().clear();
        Block baseBlock = table.getLocation().getBlock();
        baseBlock.setType(fenceMat);
        table.getPlacedBlocks().add(baseBlock.getLocation());
    }

    public void removePlatform(RouletteTable table) {
        for (Location loc : table.getPlacedBlocks()) {
            Block block = loc.getBlock();
            block.setType(Material.AIR);
        }
        table.getPlacedBlocks().clear();
    }

    public void spawnWheelBlockDisplays(RouletteTable table, double wheelRotationOffset) {
        removeWheelBlockDisplays(table);

        Location center = table.getLocation().getBlock().getLocation().add(0.5, 1.2, 0.5);
        World world = center.getWorld();
        if (world == null) return;

        double radius = Math.max(1.0, table.getSize() * 0.8);
        int totalSlots = WHEEL_NUMBERS.length;

        for (int i = 0; i < totalSlots; i++) {
            int number = WHEEL_NUMBERS[i];
            double angle = wheelRotationOffset + (2.0 * Math.PI * i / totalSlots);

            double x = center.getX() + radius * Math.cos(angle) - 0.175;
            double z = center.getZ() + radius * Math.sin(angle) - 0.175;
            double y = center.getY();

            Location blockLoc = new Location(world, x, y, z);

            Material woolMat;
            if (number == 0) {
                woolMat = Material.LIME_WOOL;
            } else if (isRed(number)) {
                woolMat = Material.RED_WOOL;
            } else {
                woolMat = Material.BLACK_WOOL;
            }

            BlockDisplay display = (BlockDisplay) world.spawnEntity(blockLoc, EntityType.BLOCK_DISPLAY);
            display.setBlock(woolMat.createBlockData());

            Transformation transform = display.getTransformation();
            transform.getScale().set(new Vector3f(0.35f, 0.35f, 0.35f));
            display.setTransformation(transform);

            table.getWheelDisplays().add(display);

            // Add number hologram label in clean white directly above wool block
            Location labelLoc = new Location(world, center.getX() + radius * Math.cos(angle), y + 0.45, center.getZ() + radius * Math.sin(angle));
            TextDisplay label = (TextDisplay) world.spawnEntity(labelLoc, EntityType.TEXT_DISPLAY);
            label.setBillboard(Display.Billboard.CENTER);
            label.setDefaultBackground(false);
            label.text(ColorUtil.component("&f" + number));

            table.getWheelLabels().add(label);
        }
    }

    public void updateWheelDisplayPositions(RouletteTable table, double wheelRotationOffset) {
        Location center = table.getLocation().getBlock().getLocation().add(0.5, 1.2, 0.5);
        World world = center.getWorld();
        if (world == null) return;

        double radius = Math.max(1.0, table.getSize() * 0.8);
        int totalSlots = WHEEL_NUMBERS.length;

        if (table.getWheelDisplays().size() != totalSlots || table.getWheelLabels().size() != totalSlots) {
            spawnWheelBlockDisplays(table, wheelRotationOffset);
            return;
        }

        for (int i = 0; i < totalSlots; i++) {
            double angle = wheelRotationOffset + (2.0 * Math.PI * i / totalSlots);

            double x = center.getX() + radius * Math.cos(angle) - 0.175;
            double z = center.getZ() + radius * Math.sin(angle) - 0.175;
            double y = center.getY();

            Location blockLoc = new Location(world, x, y, z);
            BlockDisplay display = table.getWheelDisplays().get(i);
            if (display != null && display.isValid()) {
                display.teleport(blockLoc);
            }

            Location labelLoc = new Location(world, center.getX() + radius * Math.cos(angle), y + 0.45, center.getZ() + radius * Math.sin(angle));
            TextDisplay label = table.getWheelLabels().get(i);
            if (label != null && label.isValid()) {
                label.teleport(labelLoc);
            }
        }
    }

    public void removeWheelBlockDisplays(RouletteTable table) {
        for (BlockDisplay display : table.getWheelDisplays()) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        table.getWheelDisplays().clear();

        for (TextDisplay label : table.getWheelLabels()) {
            if (label != null && label.isValid()) {
                label.remove();
            }
        }
        table.getWheelLabels().clear();

        if (table.getBallArrowEntity() != null && table.getBallArrowEntity().isValid()) {
            table.getBallArrowEntity().remove();
        }
        table.setBallArrowEntity(null);
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

        if (table.getState() == RouletteTable.TableState.WAITING && table.isTimerTaskRunning()) {
            return;
        }

        table.setState(RouletteTable.TableState.WAITING);

        BukkitTask timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                int rem = table.getRemainingSeconds() - 1;
                table.setRemainingSeconds(rem);
                plugin.getHologramManager().spawnOrUpdateHologram(table);

                if (rem <= 0) {
                    cancel();
                    table.setTimerTaskRunning(false);
                    startSpinningPhase(table);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        table.setActiveTask(timerTask);
        table.setTimerTaskRunning(true);
    }

    public void startSpinningPhase(RouletteTable table) {
        table.setState(RouletteTable.TableState.SPINNING);
        table.setTimerTaskRunning(false);

        ConfigManager config = plugin.getConfigManager();
        int spinSeconds = config.getConfig().getInt("times.spin-duration-seconds", 5);
        int totalTicks = spinSeconds * 20;

        int winningIndex = ThreadLocalRandom.current().nextInt(WHEEL_NUMBERS.length);
        int winningNumber = WHEEL_NUMBERS[winningIndex];

        table.setActiveTask(new BukkitRunnable() {
            int ticksElapsed = 0;
            int animIndex = 0;
            double wheelOffset = 0.0;

            @Override
            public void run() {
                ticksElapsed += 2;
                animIndex = (animIndex + 1) % WHEEL_NUMBERS.length;
                int currNumber = WHEEL_NUMBERS[animIndex];

                table.setCurrentSpinNumber(currNumber);
                plugin.getHologramManager().spawnOrUpdateHologram(table);

                // Rotate wheel blocks counter-clockwise
                wheelOffset -= 0.05;
                updateWheelDisplayPositions(table, wheelOffset);

                // Easing formula for arrow: slows down smoothly towards target winning wool block
                double progress = (double) ticksElapsed / totalTicks;
                double easedProgress = Math.sin((progress * Math.PI) / 2.0); // Smooth deceleration curve

                double totalRotations = Math.PI * 6.0; // 3 full counter-rotations
                double targetAngle = wheelOffset + (2.0 * Math.PI * winningIndex / WHEEL_NUMBERS.length);
                double arrowAngle = targetAngle + (1.0 - easedProgress) * totalRotations;

                // Update glowing arrow indicator
                updateArrowBallEntity(table, arrowAngle);

                // Play note block sound effect
                Location loc = table.getLocation();
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_SNARE, 1.0f, 1.2f);

                if (ticksElapsed >= totalTicks) {
                    cancel();
                    // Hold arrow steady above winning block for 1 second before revealing result
                    updateArrowBallEntity(table, targetAngle);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> startResultPhase(table, winningNumber), 20L);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L));
    }

    private void updateArrowBallEntity(RouletteTable table, double arrowAngle) {
        Location center = table.getLocation().getBlock().getLocation().add(0.5, 1.7, 0.5);
        World world = center.getWorld();
        if (world == null) return;

        double radius = Math.max(1.0, table.getSize() * 0.8);

        double ax = center.getX() + radius * Math.cos(arrowAngle);
        double az = center.getZ() + radius * Math.sin(arrowAngle);
        double ay = center.getY();

        Location targetLoc = new Location(world, ax, ay, az);

        // Point arrow downward directly at the wool block slot
        Vector direction = new Vector(0, -1, 0);
        targetLoc.setDirection(direction);

        Arrow arrow = table.getBallArrowEntity();
        if (arrow == null || !arrow.isValid()) {
            arrow = (Arrow) world.spawnEntity(targetLoc, EntityType.ARROW);
            arrow.setGravity(false);
            arrow.setPersistent(false);
            arrow.setGlowing(true);
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
            table.setBallArrowEntity(arrow);
        } else {
            arrow.teleport(targetLoc);
            arrow.setVelocity(new Vector(0, 0, 0));
            if (!arrow.isGlowing()) {
                arrow.setGlowing(true);
            }
        }
    }

    public void startResultPhase(RouletteTable table, int winningNumber) {
        table.setState(RouletteTable.TableState.RESULT);
        table.setWinningNumber(winningNumber);
        table.setTimerTaskRunning(false);

        plugin.getHologramManager().spawnOrUpdateHologram(table);

        Location loc = table.getLocation();
        loc.getWorld().playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        processPayouts(table, winningNumber);

        ConfigManager config = plugin.getConfigManager();
        int postDelay = config.getConfig().getInt("times.post-round-delay-seconds", 3);
        table.setActiveTask(new BukkitRunnable() {
            @Override
            public void run() {
                if (table.getBallArrowEntity() != null && table.getBallArrowEntity().isValid()) {
                    table.getBallArrowEntity().remove();
                    table.setBallArrowEntity(null);
                }
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
        removeWheelBlockDisplays(table);
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

    public String getNumberColorCode(int number) {
        if (number == 0) return "&a";
        if (isRed(number)) return "&c";
        return "&8";
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
