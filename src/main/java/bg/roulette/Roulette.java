package bg.roulette;

import bg.roulette.command.RouletteCommand;
import bg.roulette.config.ConfigManager;
import bg.roulette.listener.BetListener;
import bg.roulette.listener.TableInteractListener;
import bg.roulette.manager.EconomyManager;
import bg.roulette.manager.HologramManager;
import bg.roulette.manager.RouletteManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Roulette extends JavaPlugin {

    private ConfigManager configManager;
    private EconomyManager economyManager;
    private HologramManager hologramManager;
    private RouletteManager rouletteManager;
    private BetListener betListener;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.loadConfigurations();

        this.economyManager = new EconomyManager(this);
        this.hologramManager = new HologramManager(this);
        this.rouletteManager = new RouletteManager(this);

        this.rouletteManager.loadTables();

        // Listeners
        this.betListener = new BetListener(this);
        getServer().getPluginManager().registerEvents(this.betListener, this);
        getServer().getPluginManager().registerEvents(new TableInteractListener(this), this);

        // Commands
        RouletteCommand cmd = new RouletteCommand(this);
        if (getCommand("roulette") != null) {
            getCommand("roulette").setExecutor(cmd);
            getCommand("roulette").setTabCompleter(cmd);
        }

        getLogger().info("Roulette plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (this.rouletteManager != null) {
            this.rouletteManager.shutdown();
        }
        getLogger().info("Roulette plugin disabled!");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public RouletteManager getRouletteManager() {
        return rouletteManager;
    }

    public BetListener getBetListener() {
        return betListener;
    }
}
