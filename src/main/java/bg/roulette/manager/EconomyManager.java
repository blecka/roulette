package bg.roulette.manager;

import bg.roulette.Roulette;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final Roulette plugin;
    private Economy economy;

    public EconomyManager(Roulette plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault plugin not found! Economy functionality disabled.");
            return;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
        }
    }

    public boolean isEconomyAvailable() {
        return economy != null;
    }

    public double getBalance(OfflinePlayer player) {
        if (!isEconomyAvailable()) return 0.0;
        return economy.getBalance(player);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!isEconomyAvailable()) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (!isEconomyAvailable()) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
