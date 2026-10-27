package bg.roulette.listener;

import bg.roulette.Roulette;
import bg.roulette.config.ConfigManager;
import bg.roulette.gui.RouletteGUI;
import bg.roulette.gui.RouletteHolder;
import bg.roulette.model.Bet;
import bg.roulette.model.RouletteTable;
import bg.roulette.util.ColorUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BetListener implements Listener {

    public static class PendingBetPrompt {
        private final RouletteTable table;
        private final Bet.BetTargetType targetType;
        private final int targetNumber;
        private final BukkitTask timeoutTask;

        public PendingBetPrompt(RouletteTable table, Bet.BetTargetType targetType, int targetNumber, BukkitTask timeoutTask) {
            this.table = table;
            this.targetType = targetType;
            this.targetNumber = targetNumber;
            this.timeoutTask = timeoutTask;
        }

        public RouletteTable getTable() {
            return table;
        }

        public Bet.BetTargetType getTargetType() {
            return targetType;
        }

        public int getTargetNumber() {
            return targetNumber;
        }

        public BukkitTask getTimeoutTask() {
            return timeoutTask;
        }
    }

    private final Roulette plugin;
    private final Map<UUID, PendingBetPrompt> pendingBets = new ConcurrentHashMap<>();

    public BetListener(Roulette plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (event.getInventory().getHolder() instanceof RouletteHolder holder) {
            event.setCancelled(true);

            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
                return;
            }

            int slot = event.getSlot();
            RouletteTable table = holder.getTable();
            ConfigManager config = plugin.getConfigManager();
            FileConfiguration guiCfg = config.getGui();

            if (table.getState() == RouletteTable.TableState.SPINNING) {
                player.sendMessage(config.getMessageString("bet-round-spinning"));
                player.closeInventory();
                return;
            }

            if (table.hasPlayerBet(player.getUniqueId())) {
                player.sendMessage(config.getMessageString("bet-already-placed"));
                player.closeInventory();
                return;
            }

            Bet.BetTargetType targetType = null;
            int targetNumber = -1;

            int redSlot = guiCfg.getInt("items.red-button.slot", 48);
            int greenSlot = guiCfg.getInt("items.green-button.slot", 49);
            int blackSlot = guiCfg.getInt("items.black-button.slot", 50);

            if (slot == redSlot) {
                targetType = Bet.BetTargetType.RED;
            } else if (slot == greenSlot) {
                targetType = Bet.BetTargetType.GREEN;
            } else if (slot == blackSlot) {
                targetType = Bet.BetTargetType.BLACK;
            } else {
                int number = RouletteGUI.getNumberFromSlot(slot);
                if (number != -1) {
                    targetType = Bet.BetTargetType.NUMBER;
                    targetNumber = number;
                }
            }

            if (targetType != null) {
                player.closeInventory();
                initiateChatPrompt(player, table, targetType, targetNumber);
            }
        }
    }

    private void initiateChatPrompt(Player player, RouletteTable table, Bet.BetTargetType targetType, int targetNumber) {
        ConfigManager config = plugin.getConfigManager();
        cancelPendingPrompt(player.getUniqueId());

        String redText = config.getRawMessageString("bet-target-red");
        String blackText = config.getRawMessageString("bet-target-black");
        String greenText = config.getRawMessageString("bet-target-green");
        String numTemplate = config.getRawMessageString("bet-target-number");

        Bet dummyBet = new Bet(player.getUniqueId(), player.getName(), 0, targetType, targetNumber);
        String formattedTarget = dummyBet.getFormattedTarget(redText, blackText, greenText, numTemplate);

        String promptMsg = config.getMessageString("bet-prompt").replace("{target}", formattedTarget);
        player.sendMessage(promptMsg);

        BukkitTask timeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingBets.containsKey(player.getUniqueId())) {
                    pendingBets.remove(player.getUniqueId());
                    player.sendMessage(config.getMessageString("bet-timeout"));
                }
            }
        }.runTaskLater(plugin, 30 * 20L); // 30 seconds timeout

        pendingBets.put(player.getUniqueId(), new PendingBetPrompt(table, targetType, targetNumber, timeoutTask));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!pendingBets.containsKey(uuid)) return;

        PendingBetPrompt pending = pendingBets.remove(uuid);
        if (pending.getTimeoutTask() != null) {
            pending.getTimeoutTask().cancel();
        }

        event.setCancelled(true);

        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        ConfigManager config = plugin.getConfigManager();

        if (text.equalsIgnoreCase("cancel")) {
            player.sendMessage(config.getMessageString("bet-cancelled"));
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            player.sendMessage(config.getMessageString("bet-invalid-amount"));
            return;
        }

        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            player.sendMessage(config.getMessageString("bet-invalid-amount"));
            return;
        }

        // Run betting logic synchronously on main thread
        Bukkit.getScheduler().runTask(plugin, () -> processBet(player, pending.getTable(), amount, pending.getTargetType(), pending.getTargetNumber()));
    }

    public void processBet(Player player, RouletteTable table, double amount, Bet.BetTargetType targetType, int targetNumber) {
        ConfigManager config = plugin.getConfigManager();

        if (table.getState() == RouletteTable.TableState.SPINNING) {
            player.sendMessage(config.getMessageString("bet-round-spinning"));
            return;
        }

        if (table.hasPlayerBet(player.getUniqueId())) {
            player.sendMessage(config.getMessageString("bet-already-placed"));
            return;
        }

        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            player.sendMessage(config.getMessageString("bet-invalid-amount"));
            return;
        }

        double minBet = config.getConfig().getDouble("limits.min-bet", 100.0);
        double maxBet = config.getConfig().getDouble("limits.max-bet", 100000.0);

        if (amount < minBet) {
            player.sendMessage(config.getMessageString("bet-amount-min").replace("{min}", String.valueOf(minBet)));
            return;
        }

        if (amount > maxBet) {
            player.sendMessage(config.getMessageString("bet-amount-max").replace("{max}", String.valueOf(maxBet)));
            return;
        }

        double balance = plugin.getEconomyManager().getBalance(player);
        if (balance < amount) {
            player.sendMessage(config.getMessageString("bet-insufficient-funds").replace("{balance}", String.valueOf(balance)));
            return;
        }

        if (!plugin.getEconomyManager().withdraw(player, amount)) {
            player.sendMessage(config.getMessageString("bet-insufficient-funds").replace("{balance}", String.valueOf(balance)));
            return;
        }

        Bet bet = new Bet(player.getUniqueId(), player.getName(), amount, targetType, targetNumber);
        table.addBet(bet);

        String redText = config.getRawMessageString("bet-target-red");
        String blackText = config.getRawMessageString("bet-target-black");
        String greenText = config.getRawMessageString("bet-target-green");
        String numTemplate = config.getRawMessageString("bet-target-number");
        String targetFmt = bet.getFormattedTarget(redText, blackText, greenText, numTemplate);

        player.sendMessage(config.getMessageString("bet-success")
                .replace("{amount}", String.valueOf(amount))
                .replace("{target}", targetFmt));

        plugin.getRouletteManager().startOrResetTimer(table);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cancelPendingPrompt(event.getPlayer().getUniqueId());
    }

    private void cancelPendingPrompt(UUID uuid) {
        PendingBetPrompt pending = pendingBets.remove(uuid);
        if (pending != null && pending.getTimeoutTask() != null) {
            pending.getTimeoutTask().cancel();
        }
    }
}
