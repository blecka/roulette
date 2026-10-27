package bg.roulette.command;

import bg.roulette.Roulette;
import bg.roulette.config.ConfigManager;
import bg.roulette.model.Bet;
import bg.roulette.model.RouletteTable;
import bg.roulette.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RouletteCommand implements CommandExecutor, TabCompleter {

    private final Roulette plugin;

    public RouletteCommand(Roulette plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager config = plugin.getConfigManager();

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(config.getMessageString("only-players"));
                    return true;
                }

                if (!player.hasPermission("roulette.admin")) {
                    player.sendMessage(config.getMessageString("no-permission"));
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ColorUtil.color("&cУпотреба: /roulette create <име> [размер]"));
                    return true;
                }

                String name = args[1];
                if (plugin.getRouletteManager().getTable(name) != null) {
                    player.sendMessage(config.getMessageString("table-already-exists").replace("{table}", name));
                    return true;
                }

                int size = 3;
                if (args.length >= 3) {
                    try {
                        size = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(config.getMessageString("table-size-invalid"));
                        return true;
                    }
                }

                if (size < 1 || size > 5) {
                    player.sendMessage(config.getMessageString("table-size-invalid"));
                    return true;
                }

                plugin.getRouletteManager().createTable(name, player.getLocation(), size);
                player.sendMessage(config.getMessageString("table-created")
                        .replace("{table}", name)
                        .replace("{size}", String.valueOf(size)));
            }

            case "delete", "remove" -> {
                if (!sender.hasPermission("roulette.admin")) {
                    sender.sendMessage(config.getMessageString("no-permission"));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(ColorUtil.color("&cУпотреба: /roulette delete <име>"));
                    return true;
                }

                String name = args[1];
                boolean deleted = plugin.getRouletteManager().deleteTable(name);
                if (deleted) {
                    sender.sendMessage(config.getMessageString("table-deleted").replace("{table}", name));
                } else {
                    sender.sendMessage(config.getMessageString("table-not-found").replace("{table}", name));
                }
            }

            case "size" -> {
                if (!sender.hasPermission("roulette.admin")) {
                    sender.sendMessage(config.getMessageString("no-permission"));
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(ColorUtil.color("&cУпотреба: /roulette size <име> <1-5>"));
                    return true;
                }

                String name = args[1];
                RouletteTable table = plugin.getRouletteManager().getTable(name);
                if (table == null) {
                    sender.sendMessage(config.getMessageString("table-not-found").replace("{table}", name));
                    return true;
                }

                int newSize;
                try {
                    newSize = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(config.getMessageString("table-size-invalid"));
                    return true;
                }

                if (newSize < 1 || newSize > 5) {
                    sender.sendMessage(config.getMessageString("table-size-invalid"));
                    return true;
                }

                plugin.getRouletteManager().resizeTable(table, newSize);
                sender.sendMessage(config.getMessageString("table-size-changed")
                        .replace("{table}", name)
                        .replace("{size}", String.valueOf(newSize)));
            }

            case "reload" -> {
                if (!sender.hasPermission("roulette.admin")) {
                    sender.sendMessage(config.getMessageString("no-permission"));
                    return true;
                }

                plugin.getConfigManager().reloadConfigurations();
                plugin.getRouletteManager().loadTables();
                sender.sendMessage(config.getMessageString("plugin-reloaded"));
            }

            case "bet" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(config.getMessageString("only-players"));
                    return true;
                }

                if (!player.hasPermission("roulette.use")) {
                    player.sendMessage(config.getMessageString("no-permission"));
                    return true;
                }

                if (args.length < 3) {
                    player.sendMessage(ColorUtil.color("&cУпотреба: /roulette bet <сума> <red|black|green|0-36>"));
                    return true;
                }

                RouletteTable table = plugin.getRouletteManager().getNearestTable(player.getLocation(), 10.0);
                if (table == null) {
                    player.sendMessage(config.getMessageString("not-near-table"));
                    return true;
                }

                double amount;
                try {
                    amount = Double.parseDouble(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(config.getMessageString("bet-invalid-amount"));
                    return true;
                }

                String targetStr = args[2].toLowerCase();
                Bet.BetTargetType targetType;
                int targetNumber = -1;

                if (targetStr.equals("red") || targetStr.equals("червено")) {
                    targetType = Bet.BetTargetType.RED;
                } else if (targetStr.equals("black") || targetStr.equals("черно")) {
                    targetType = Bet.BetTargetType.BLACK;
                } else if (targetStr.equals("green") || targetStr.equals("зелено")) {
                    targetType = Bet.BetTargetType.GREEN;
                } else {
                    try {
                        int num = Integer.parseInt(targetStr);
                        if (num < 0 || num > 36) {
                            player.sendMessage(config.getMessageString("bet-target-invalid"));
                            return true;
                        }
                        targetType = Bet.BetTargetType.NUMBER;
                        targetNumber = num;
                    } catch (NumberFormatException e) {
                        player.sendMessage(config.getMessageString("bet-target-invalid"));
                        return true;
                    }
                }

                plugin.getBetListener().processBet(player, table, amount, targetType, targetNumber);
            }

            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ColorUtil.color("&8&m------------------&r &c&lРУЛЕТКА &8&m------------------"));
        if (sender.hasPermission("roulette.use")) {
            sender.sendMessage(ColorUtil.color("&e/roulette bet <сума> <target> &7- Направете залог"));
        }
        if (sender.hasPermission("roulette.admin")) {
            sender.sendMessage(ColorUtil.color("&e/roulette create <име> [размер] &7- Създаване на маса"));
            sender.sendMessage(ColorUtil.color("&e/roulette delete <име> &7- Изтриване на маса"));
            sender.sendMessage(ColorUtil.color("&e/roulette size <име> <1-5> &7- Промяна на размер (радиус на анимацията)"));
            sender.sendMessage(ColorUtil.color("&e/roulette reload &7- Презареждане на конфигурацията"));
        }
        sender.sendMessage(ColorUtil.color("&8&m---------------------------------------------"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("roulette.use")) completions.add("bet");
            if (sender.hasPermission("roulette.admin")) {
                completions.add("create");
                completions.add("delete");
                completions.add("size");
                completions.add("reload");
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("delete") || sub.equals("remove") || sub.equals("size")) {
                for (RouletteTable table : plugin.getRouletteManager().getAllTables()) {
                    completions.add(table.getName());
                }
            } else if (sub.equals("bet")) {
                completions.add("100");
                completions.add("500");
                completions.add("1000");
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("create") || sub.equals("size")) {
                completions.add("1");
                completions.add("2");
                completions.add("3");
                completions.add("4");
                completions.add("5");
            } else if (sub.equals("bet")) {
                completions.add("red");
                completions.add("black");
                completions.add("green");
                for (int i = 0; i <= 36; i++) {
                    completions.add(String.valueOf(i));
                }
            }
        }

        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .toList();
    }
}
